package com.gustavocirino.myday_productivity.controller;

import com.gustavocirino.myday_productivity.dto.*;
import com.gustavocirino.myday_productivity.model.enums.TaskStatus;
import com.gustavocirino.myday_productivity.service.TaskService;
import com.gustavocirino.myday_productivity.dto.DashboardSummaryDTO;
import com.gustavocirino.myday_productivity.model.User;
import com.gustavocirino.myday_productivity.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller - Camada de Entrada da API
 * Stateless: Apenas transforma HTTP -> Java -> HTTP
 * Não contém lógica de negócio (delega tudo ao Service)
 */
@RestController
@RequestMapping("/api/tasks") // Base URL: http://localhost:8080/api/tasks
@CrossOrigin(origins = "*") // Permite acesso do frontend (CORS liberado)
@Tag(name = "Tasks", description = "API de gerenciamento de tarefas com time-blocking")
public class TaskController {

    private final TaskService taskService;
    private final UserRepository userRepository;

    // Constructor Injection (boa prática Spring)
    public TaskController(TaskService taskService, UserRepository userRepository) {
        this.taskService = taskService;
        this.userRepository = userRepository;
    }

    // ==================== ENDPOINTS ====================

    /**
     * GET /api/tasks
     * Retorna todas as tarefas (backlog + calendário)
     * Frontend chama isso ao carregar a página
     */
    @Operation(summary = "Listar todas as tarefas", description = "Retorna lista completa de tarefas incluindo backlog (PENDING) e calendário (SCHEDULED/DONE)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    })
    @GetMapping
    public ResponseEntity<List<TaskResponseDTO>> getAllTasks(
            @RequestHeader(name = "X-Auth-Token", required = true) String authToken) {

        if (authToken == null || authToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByAuthToken(authToken)
                .orElseThrow(() -> new RuntimeException("Token de autenticação inválido"));

        List<TaskResponseDTO> tasks = taskService.getAllTasks(user);
        return ResponseEntity.ok(tasks); // 200 OK
    }

    /**
     * GET /api/tasks/{id}
     * Busca uma tarefa específica por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponseDTO> getTaskById(@PathVariable Long id) {
        TaskResponseDTO task = taskService.getTaskById(id);
        return ResponseEntity.ok(task);
    }

    /**
     * POST /api/tasks
     * Cria nova tarefa no BACKLOG
     * Recebe: { "title": "...", "description": "...", "priority": "HIGH" }
     * Retorna: TaskResponseDTO com ID gerado pelo banco
     */
    @Operation(summary = "Criar nova tarefa", description = "Cria tarefa no backlog com status PENDING. Requer título e prioridade.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tarefa criada com sucesso")
    })
    @PostMapping
    public ResponseEntity<TaskResponseDTO> createTask(
            @RequestBody TaskCreateDTO dto,
            @RequestHeader(name = "X-Auth-Token", required = true) String authToken) {

        if (authToken == null || authToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByAuthToken(authToken)
                .orElseThrow(() -> new RuntimeException("Token de autenticação inválido"));

        TaskResponseDTO created = taskService.createTask(dto, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * PATCH /api/tasks/{id}/move
     * Move tarefa para o calendário (TIME-BLOCKING via Drag & Drop)
     */
    @PatchMapping("/{id}/move")
    public ResponseEntity<TaskResponseDTO> moveTask(
            @PathVariable Long id,
            @RequestBody TaskMoveDTO dto) {

        TaskResponseDTO updated = taskService.moveTask(id, dto);
        return ResponseEntity.ok(updated); // 200 OK
    }

    /**
     * PATCH /api/tasks/{id}/swap
     * Troca horários entre duas tarefas agendadas.
     * Usado pelo frontend ao confirmar "swap" em um horário ocupado.
     */
    @PatchMapping("/{id}/swap")
    public ResponseEntity<List<TaskResponseDTO>> swapTasks(
            @PathVariable Long id,
            @RequestBody TaskSwapDTO dto) {

        List<TaskResponseDTO> updated = taskService.swapTasks(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * PATCH /api/tasks/{id}/complete
     * Marca tarefa como concluída (DONE)
     * Frontend pode chamar isso ao clicar num checkbox
     */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<TaskResponseDTO> completeTask(@PathVariable Long id) {
        TaskResponseDTO completed = taskService.markAsDone(id);
        return ResponseEntity.ok(completed);
    }

    /**
     * PATCH /api/tasks/{id}/uncomplete
     * Remove conclusão (DONE -> PENDING ou SCHEDULED)
     */
    @PatchMapping("/{id}/uncomplete")
    public ResponseEntity<TaskResponseDTO> uncompleteTask(@PathVariable Long id) {
        TaskResponseDTO updated = taskService.markAsUndone(id);
        return ResponseEntity.ok(updated);
    }

    /**
     * PUT /api/tasks/{id}
     * Atualiza dados de uma tarefa (título, descrição, prioridade)
     * Permite editar tarefa sem mudar seu status ou datas
     */
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponseDTO> updateTask(
            @PathVariable Long id,
            @RequestBody TaskUpdateDTO dto) {

        TaskResponseDTO updated = taskService.updateTask(id, dto);
        return ResponseEntity.ok(updated);
    }

    /**
     * PATCH /api/tasks/{id}/backlog
     * Remove tarefa do calendário e volta para o backlog
     * Transição: SCHEDULED -> PENDING
     */
    @PatchMapping("/{id}/backlog")
    public ResponseEntity<TaskResponseDTO> moveToBacklog(@PathVariable Long id) {
        TaskResponseDTO task = taskService.moveBackToBacklog(id);
        return ResponseEntity.ok(task);
    }

    /**
     * GET /api/tasks/status/{status}
     * Filtra tarefas por status (PENDING, SCHEDULED, DONE, LATE)
     * Útil para analytics e filtros no frontend
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<TaskResponseDTO>> getTasksByStatus(@PathVariable TaskStatus status) {
        List<TaskResponseDTO> tasks = taskService.getTasksByStatus(status);
        return ResponseEntity.ok(tasks);
    }

    /**
     * GET /api/tasks/stats
     * Retorna estatísticas agregadas (total, pending, done, completion rate)
     * Alimenta o painel de analytics do frontend
     */
    @GetMapping("/stats")
    public ResponseEntity<TaskStatsDTO> getStatistics() {
        TaskStatsDTO stats = taskService.getStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/tasks/dashboard
     * Resumo de alta performance filtrado pelo usuário logado.
     *
     * O frontend deve enviar o token de autenticação no header "X-Auth-Token".
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardSummaryDTO> getDashboardSummary(
            @RequestHeader(name = "X-Auth-Token", required = true) String authToken) {

        if (authToken == null || authToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userRepository.findByAuthToken(authToken)
                .orElseThrow(() -> new RuntimeException("Token de autenticação inválido"));

        DashboardSummaryDTO summary = taskService.getDashboardSummary(user.getId());
        return ResponseEntity.ok(summary);
    }

    /**
     * DELETE /api/tasks/{id}
     * Remove tarefa permanentemente
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}