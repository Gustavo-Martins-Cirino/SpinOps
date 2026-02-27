package com.gustavocirino.myday_productivity.service;

import com.gustavocirino.myday_productivity.dto.*;
import com.gustavocirino.myday_productivity.model.Task;
import com.gustavocirino.myday_productivity.model.User;
import com.gustavocirino.myday_productivity.model.enums.TaskPriority;
import com.gustavocirino.myday_productivity.model.enums.TaskStatus;
import com.gustavocirino.myday_productivity.repository.TaskRepository;
import com.gustavocirino.myday_productivity.repository.UserRepository;
import com.gustavocirino.myday_productivity.dto.DashboardSummaryDTO;
import com.gustavocirino.myday_productivity.service.ai.PredictiveMaintenanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;

/**
 * Service Layer - Cérebro da Aplicação
 * ÚNICA camada que pode manipular Entidades (Task)
 * Responsável por: Validações, Regras de Negócio, Transições de Estado
 */
@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final PredictiveMaintenanceService predictiveMaintenanceService;
    private static final String DEFAULT_COLOR = "#34a853";

    public TaskService(TaskRepository taskRepository, UserRepository userRepository, PredictiveMaintenanceService predictiveMaintenanceService) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.predictiveMaintenanceService = predictiveMaintenanceService;
    }

    // ==================== CRUD OPERATIONS ====================

    /**
     * GET ALL - Busca todas as tarefas de um usuário e converte para DTO
     */
    public List<TaskResponseDTO> getAllTasks(User owner) {
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("Usuário obrigatório para listar tarefas.");
        }
        List<Task> tasks = taskRepository.findByUserId(owner.getId());

        // Fallback temporário: se for o primeiro usuário cadastrado, também enxerga
        // tarefas legadas sem owner (user_id NULL), para não "sumirem" após o
        // isolamento por usuário.
        boolean isFirstUser = userRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                .stream()
                .findFirst()
                .map(first -> first.getId().equals(owner.getId()))
                .orElse(false);

        if (isFirstUser) {
            List<Task> orphans = taskRepository.findByUserIsNull();
            if (!orphans.isEmpty()) {
                List<Task> merged = new ArrayList<>(tasks);
                merged.addAll(orphans);
                tasks = merged;
            }
        }

        return tasks.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * GET BY ID - Busca uma tarefa específica
     */
    public TaskResponseDTO getTaskById(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task não encontrada com ID: " + id));
        return toDTO(task);
    }

    /**
     * POST - Cria nova tarefa
     * Se horários forem fornecidos, cria como SCHEDULED (já no calendário)
     * Se não, cria como PENDING (backlog)
     */
    @Transactional
    public TaskResponseDTO createTask(TaskCreateDTO dto, User owner) {
        // Validação básica
        if (dto.title() == null || dto.title().isBlank()) {
            throw new IllegalArgumentException("Título não pode ser vazio");
        }

        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("Usuário obrigatório para criar tarefa.");
        }

        Task task = new Task();
        task.setTitle(dto.title());
        task.setDescription(dto.description());
        task.setPriority(parsePriority(dto.priority())); // Converte String -> Enum com fallback seguro
        task.setColor(resolveColor(dto.color()));
        
        // Novos campos SPIN OPS
        task.setEquipmentName(dto.equipmentName());
        task.setEquipmentType(dto.equipmentType());
        task.setSensorReadings(dto.sensorReadings());

        // SpinOps AI: Analyze Maintenance Order (Predictive)
        if (task.getEquipmentName() != null && !task.getEquipmentName().isBlank()) {
             predictiveMaintenanceService.analyzeMaintenanceOrder(task);
        }

        task.setUser(owner);

        // Se horários foram fornecidos, criar como SCHEDULED e validar conflitos
        if (dto.startTime() != null && dto.endTime() != null) {
            // Validação temporal
            if (dto.endTime().isBefore(dto.startTime()) || dto.endTime().isEqual(dto.startTime())) {
                throw new IllegalArgumentException("Horário final deve ser posterior ao inicial");
            }

            // Validar conflitos de horário
            validateTimeSlotConflict(null, dto.startTime(), dto.endTime());

            task.setStartTime(dto.startTime());
            task.setEndTime(dto.endTime());
            task.setStatus(TaskStatus.SCHEDULED); // Tarefa já agendada
        } else {
            // Sem horários = tarefa no backlog
            task.setStatus(TaskStatus.PENDING);
        }

        Task saved = taskRepository.save(task);
        return toDTO(saved);
    }

    /**
     * PATCH - Move tarefa para o calendário (TIME-BLOCKING)
     * Regra Crítica: Ao mover, muda status de PENDING -> SCHEDULED
     */
    @Transactional
    public TaskResponseDTO moveTask(Long id, TaskMoveDTO dto) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task não encontrada"));

        // Validação temporal
        if (dto.newStartTime() == null || dto.newEndTime() == null) {
            throw new IllegalArgumentException("Datas de início e fim são obrigatórias");
        }

        if (dto.newEndTime().isBefore(dto.newStartTime())) {
            throw new IllegalArgumentException("Data de fim não pode ser antes do início");
        }

        // ✅ VALIDAÇÃO DE CONFLITOS: Impede sobreposição de horários
        validateTimeSlotConflict(id, dto.newStartTime(), dto.newEndTime());

        // Atualiza dados temporais
        task.setStartTime(dto.newStartTime());
        task.setEndTime(dto.newEndTime());

        // TRANSIÇÃO DE ESTADO: PENDING -> SCHEDULED
        task.setStatus(TaskStatus.SCHEDULED);

        Task updated = taskRepository.save(task);
        return toDTO(updated);
    }

    /**
     * Valida se o horário proposto conflita com tarefas já agendadas.
     * Lança exceção se detectar sobreposição.
     */
    private void validateTimeSlotConflict(Long taskId, java.time.LocalDateTime newStart,
            java.time.LocalDateTime newEnd) {
        validateTimeSlotConflictExcluding(taskId == null ? List.of() : List.of(taskId), newStart, newEnd);

    }

    private void validateTimeSlotConflictExcluding(List<Long> excludedTaskIds, LocalDateTime newStart,
            LocalDateTime newEnd) {
        List<Long> excluded = excludedTaskIds == null ? List.of() : excludedTaskIds;

        // Busca todas as tarefas SCHEDULED (exceto as tarefas excluídas)
        List<Task> scheduledTasks = taskRepository.findByStatus(TaskStatus.SCHEDULED)
                .stream()
                .filter(t -> t.getStartTime() != null && t.getEndTime() != null)
                .filter(t -> excluded.stream().filter(Objects::nonNull).noneMatch(id -> id.equals(t.getId())))
                .toList();

        // Verifica se há sobreposição de horários
        for (Task existing : scheduledTasks) {
            boolean hasOverlap = !(newEnd.isBefore(existing.getStartTime()) ||
                    newStart.isAfter(existing.getEndTime()) ||
                    newEnd.isEqual(existing.getStartTime()) ||
                    newStart.isEqual(existing.getEndTime()));

            if (hasOverlap) {
                throw new IllegalArgumentException(
                        String.format("⚠️ Conflito de horário! Já existe uma tarefa agendada entre %s e %s: '%s'",
                                existing.getStartTime(), existing.getEndTime(), existing.getTitle()));
            }
        }
    }

    /**
     * Troca horários entre duas tarefas agendadas.
     */
    @Transactional
    public List<TaskResponseDTO> swapTasks(Long taskId, TaskSwapDTO dto) {
        if (dto == null || dto.otherTaskId() == null) {
            throw new IllegalArgumentException("ID da outra tarefa é obrigatório");
        }
        if (taskId == null) {
            throw new IllegalArgumentException("ID da tarefa é obrigatório");
        }
        if (taskId.equals(dto.otherTaskId())) {
            throw new IllegalArgumentException("Não é possível trocar com a mesma tarefa");
        }

        Task a = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task não encontrada"));
        Task b = taskRepository.findById(dto.otherTaskId())
                .orElseThrow(() -> new RuntimeException("Task não encontrada"));

        if (a.getStartTime() == null || a.getEndTime() == null) {
            throw new IllegalArgumentException("A tarefa a ser movida não está agendada");
        }
        if (b.getStartTime() == null || b.getEndTime() == null) {
            throw new IllegalArgumentException("A tarefa de destino não está agendada");
        }

        LocalDateTime aStart = a.getStartTime();
        LocalDateTime aEnd = a.getEndTime();
        LocalDateTime bStart = b.getStartTime();
        LocalDateTime bEnd = b.getEndTime();

        // Valida que o swap não criará conflitos com outras tarefas
        List<Long> excluded = new ArrayList<>();
        excluded.add(a.getId());
        excluded.add(b.getId());
        validateTimeSlotConflictExcluding(excluded, bStart, bEnd);
        validateTimeSlotConflictExcluding(excluded, aStart, aEnd);

        a.setStartTime(bStart);
        a.setEndTime(bEnd);
        a.setStatus(TaskStatus.SCHEDULED);

        b.setStartTime(aStart);
        b.setEndTime(aEnd);
        b.setStatus(TaskStatus.SCHEDULED);

        taskRepository.save(a);
        taskRepository.save(b);

        return List.of(toDTO(a), toDTO(b));
    }

    /**
     * DELETE - Remove tarefa
     */
    @Transactional
    public void deleteTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task não encontrada"));

        // Limpar relacionamentos antes de deletar
        task.getTags().clear();
        taskRepository.save(task);

        // Agora deletar a task
        taskRepository.deleteById(id);
    }

    /**
     * PATCH - Marca tarefa como DONE
     */
    @Transactional
    public TaskResponseDTO markAsDone(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task não encontrada"));

        task.setStatus(TaskStatus.DONE);
        Task updated = taskRepository.save(task);
        return toDTO(updated);
    }

    /**
     * PATCH - Remove conclusão (DONE -> PENDING ou SCHEDULED)
     */
    @Transactional
    public TaskResponseDTO markAsUndone(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task não encontrada"));

        // Se a tarefa possui startTime, consideramos que ela pertence ao calendário.
        // (Alguns registros antigos podem ter endTime nulo; mesmo assim precisamos
        // restaurar o status SCHEDULED.)
        boolean hasSchedule = task.getStartTime() != null;
        task.setStatus(hasSchedule ? TaskStatus.SCHEDULED : TaskStatus.PENDING);

        Task updated = taskRepository.save(task);
        return toDTO(updated);
    }

    // ==================== CONVERSORES (Entity <-> DTO) ====================

    /**
     * Converte Entity -> DTO (esconde estrutura interna do banco)
     */
    private TaskResponseDTO toDTO(Task task) {
        return new TaskResponseDTO(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                task.getStatus(),
                task.getStartTime(),
                task.getEndTime(),
                task.getCreatedAt(),
                resolveColor(task.getColor()),
                task.getEquipmentName(),
                task.getEquipmentType(),
                task.getSensorReadings(),
                task.getRiskLevel(),
                task.getPredictedFailureDate(),
                task.getFailureProbability(),
                task.getAiAnalysisSummary());
    }

    /**
     * PUT/PATCH - Atualiza dados de uma tarefa (título, descrição, prioridade)
     */
    @Transactional
    public TaskResponseDTO updateTask(Long id, TaskUpdateDTO dto) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task não encontrada"));

        // Atualiza apenas os campos fornecidos (null-safe)
        if (dto.title() != null && !dto.title().isBlank()) {
            task.setTitle(dto.title());
        }
        if (dto.description() != null) {
            task.setDescription(dto.description());
        }
        if (dto.priority() != null) {
            task.setPriority(dto.priority());
        }
        if (dto.color() != null && !dto.color().isBlank()) {
            task.setColor(resolveColor(dto.color()));
        }

        // Atualizar horários se fornecidos
        if (dto.startTime() != null && dto.endTime() != null) {
            task.setStartTime(dto.startTime());
            task.setEndTime(dto.endTime());

            // Se tinha horário e ainda tem, mantém SCHEDULED
            // Se não tinha horário e agora tem, muda para SCHEDULED
            if (task.getStatus() == TaskStatus.PENDING) {
                task.setStatus(TaskStatus.SCHEDULED);
            }
        } else if (dto.startTime() == null && dto.endTime() == null && task.getStartTime() != null) {
            // Se remover os horários, volta para PENDING
            task.setStartTime(null);
            task.setEndTime(null);
            task.setStatus(TaskStatus.PENDING);
        }

        Task updated = taskRepository.save(task);
        return toDTO(updated);
    }

    /**
     * PATCH - Move tarefa de volta para o backlog
     * Transição: SCHEDULED -> PENDING (remove do calendário)
     */
    @Transactional
    public TaskResponseDTO moveBackToBacklog(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task não encontrada"));

        // Remove datas e volta para PENDING
        task.setStartTime(null);
        task.setEndTime(null);
        task.setStatus(TaskStatus.PENDING);

        Task updated = taskRepository.save(task);
        return toDTO(updated);
    }

    // ==================== MÉTODOS DE ANALYTICS ====================

    /**
     * Busca tarefas por status (usado para estatísticas)
     */
    public List<TaskResponseDTO> getTasksByStatus(TaskStatus status) {
        return taskRepository.findByStatus(status)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Gera estatísticas agregadas para o dashboard
     * Calcula: total, pending, scheduled, done, late e taxa de conclusão
     */
    public TaskStatsDTO getStatistics() {
        List<Task> allTasks = taskRepository.findAll();

        long total = allTasks.size();
        long pending = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();
        long scheduled = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.SCHEDULED).count();
        long done = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        long late = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.LATE).count();

        double completionRate = total > 0 ? (done * 100.0) / total : 0.0;

        return new TaskStatsDTO(total, pending, scheduled, done, late, completionRate);
    }

    /**
     * Resumo compacto para o dashboard principal.
     *
     * Regra de carga cognitiva:
     * - cada tarefa PENDING soma +10 pontos
     * - valor máximo limitado em 100
     */
    public DashboardSummaryDTO getDashboardSummary(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId não pode ser nulo para o resumo do dashboard");
        }

        List<Task> allTasks = taskRepository.findByUserId(userId);

        long total = allTasks.size();
        long completed = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        long pending = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();

        int cognitiveLoad = (int) Math.min(100, pending * 10L);

        return new DashboardSummaryDTO(total, completed, pending, cognitiveLoad);
    }

    private String resolveColor(String color) {
        if (color == null || color.isBlank()) {
            return DEFAULT_COLOR;
        }
        return color.trim().toLowerCase();
    }

    private TaskPriority parsePriority(String priorityRaw) {
        if (priorityRaw == null || priorityRaw.isBlank()) {
            return TaskPriority.MEDIUM;
        }

        try {
            return TaskPriority.valueOf(priorityRaw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return TaskPriority.MEDIUM;
        }
    }
}
