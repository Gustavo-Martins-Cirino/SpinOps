package com.gustavocirino.myday_productivity.service.ai.analytics;

import com.gustavocirino.myday_productivity.dto.AnalyticsSummaryDTO;
import com.gustavocirino.myday_productivity.dto.TaskStatsDTO;
import com.gustavocirino.myday_productivity.model.Task;
import com.gustavocirino.myday_productivity.model.enums.TaskPriority;
import com.gustavocirino.myday_productivity.model.enums.TaskStatus;
import com.gustavocirino.myday_productivity.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serviço de analytics e métricas de produtividade.
 * 
 * Responsável por calcular estatísticas agregadas sobre as tarefas,
 * identificar padrões de produtividade e fornecer dados para dashboards.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final TaskRepository taskRepository;

    /**
     * Retorna estatísticas gerais do sistema.
     * Delega para TaskService.getStatistics() mantendo compatibilidade.
     */
    public TaskStatsDTO getGeneralStatistics() {
        log.info("📊 Calculando estatísticas gerais");
        List<Task> allTasks = taskRepository.findAll();
        return calculateStats(allTasks);
    }

    /**
     * Retorna estatísticas filtradas por data (hoje, esta semana, etc).
     */
    public TaskStatsDTO getStatisticsByDateRange(LocalDateTime start, LocalDateTime end) {
        log.info("📊 Calculando estatísticas entre {} e {}", start, end);

        List<Task> tasksInRange = taskRepository.findAll().stream()
                .filter(t -> isInDateRange(t, start, end))
                .toList();

        return calculateStats(tasksInRange);
    }

    /**
     * Retorna estatísticas do dia atual.
     */
    public TaskStatsDTO getTodayStatistics() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);
        return getStatisticsByDateRange(startOfDay, endOfDay);
    }

    /**
     * Retorna distribuição de tarefas por prioridade.
     */
    public Map<String, Long> getPriorityDistribution() {
        log.info("📊 Calculando distribuição por prioridade");
        List<Task> allTasks = taskRepository.findAll();

        Map<String, Long> distribution = new HashMap<>();
        distribution.put("HIGH", allTasks.stream().filter(t -> t.getPriority() == TaskPriority.HIGH).count());
        distribution.put("MEDIUM", allTasks.stream().filter(t -> t.getPriority() == TaskPriority.MEDIUM).count());
        distribution.put("LOW", allTasks.stream().filter(t -> t.getPriority() == TaskPriority.LOW).count());

        return distribution;
    }

    /**
     * Retorna distribuição de tarefas por status.
     */
    public Map<String, Long> getStatusDistribution() {
        log.info("📊 Calculando distribuição por status");
        List<Task> allTasks = taskRepository.findAll();

        Map<String, Long> distribution = new HashMap<>();
        distribution.put("PENDING", allTasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count());
        distribution.put("SCHEDULED", allTasks.stream().filter(t -> t.getStatus() == TaskStatus.SCHEDULED).count());
        distribution.put("DONE", allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count());
        distribution.put("LATE", allTasks.stream().filter(t -> t.getStatus() == TaskStatus.LATE).count());

        return distribution;
    }

    /**
     * Calcula taxa de produtividade (tarefas concluídas vs total).
     */
    public double getProductivityRate() {
        List<Task> allTasks = taskRepository.findAll();
        if (allTasks.isEmpty())
            return 0.0;

        long done = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        return (done * 100.0) / allTasks.size();
    }

    /**
     * Identifica tarefas atrasadas (SCHEDULED mas passou do horário de término).
     */
    public List<Task> getOverdueTasks() {
        LocalDateTime now = LocalDateTime.now();
        return taskRepository.findAll().stream()
                .filter(t -> t.getStatus() == TaskStatus.SCHEDULED)
                .filter(t -> t.getEndTime() != null && t.getEndTime().isBefore(now))
                .toList();
    }

    /**
     * Calcula tempo médio de conclusão de tarefas (em horas).
     */
    public double getAverageCompletionTime() {
        List<Task> doneTasks = taskRepository.findByStatus(TaskStatus.DONE);

        if (doneTasks.isEmpty())
            return 0.0;

        double totalHours = doneTasks.stream()
                .filter(t -> t.getStartTime() != null && t.getEndTime() != null)
                .mapToDouble(t -> {
                    long minutes = java.time.Duration.between(t.getStartTime(), t.getEndTime()).toMinutes();
                    return minutes / 60.0;
                })
                .sum();

        return totalHours / doneTasks.size();
    }

    /**
     * Retorna resumo de produtividade para um período específico.
     * Calcula métricas agregadas: tarefas planejadas, concluídas, criadas,
     * taxa de conclusão e média de tarefas planejadas por dia.
     * 
     * Nota: completionRate pode exceder 100% se houver mais tarefas concluídas
     * do que planejadas (ex: tarefas movidas diretamente para DONE sem passar por SCHEDULED).
     */
    @Transactional(readOnly = true)
    public AnalyticsSummaryDTO getSummary(LocalDateTime start, LocalDateTime end) {
        log.info("📊 Calculando resumo de produtividade entre {} e {}", start, end);

        // Conta tarefas SCHEDULED (planejadas) no período
        long totalPlanned = taskRepository.countByStatusScheduledAndStartTimeBetween(start, end);

        // Conta tarefas DONE (concluídas) no período
        long totalCompleted = taskRepository.countByStatusDoneAndStartTimeBetween(start, end);

        // Conta tarefas criadas no período
        long totalCreated = taskRepository.countByCreatedAtBetween(start, end);

        // Calcula taxa de conclusão
        double completionRate = totalPlanned == 0 ? 0.0 : (100.0 * totalCompleted) / totalPlanned;

        // Calcula dias no período usando date-based calculation (mínimo 1)
        long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()) + 1);

        // Calcula média de tarefas planejadas por dia
        double avgPlannedPerDay = (double) totalPlanned / days;

        return new AnalyticsSummaryDTO(
                start,
                end,
                totalPlanned,
                totalCompleted,
                completionRate,
                totalCreated,
                avgPlannedPerDay
        );
    }

    // ==================== MÉTODOS AUXILIARES ====================

    private TaskStatsDTO calculateStats(List<Task> tasks) {
        long total = tasks.size();
        long pending = tasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();
        long scheduled = tasks.stream().filter(t -> t.getStatus() == TaskStatus.SCHEDULED).count();
        long done = tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        long late = tasks.stream().filter(t -> t.getStatus() == TaskStatus.LATE).count();

        double completionRate = total > 0 ? (done * 100.0) / total : 0.0;

        return new TaskStatsDTO(total, pending, scheduled, done, late, completionRate);
    }

    private boolean isInDateRange(Task task, LocalDateTime start, LocalDateTime end) {
        if (task.getStartTime() == null)
            return false;
        return !task.getStartTime().isBefore(start) && !task.getStartTime().isAfter(end);
    }
}
