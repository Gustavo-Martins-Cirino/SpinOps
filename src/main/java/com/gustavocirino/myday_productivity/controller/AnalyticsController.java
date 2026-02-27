package com.gustavocirino.myday_productivity.controller;

import com.gustavocirino.myday_productivity.dto.AnalyticsSummaryDTO;
import com.gustavocirino.myday_productivity.dto.TaskStatsDTO;
import com.gustavocirino.myday_productivity.model.Task;
import com.gustavocirino.myday_productivity.service.ai.analytics.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Controller para endpoints de analytics e métricas.
 * 
 * Endpoints:
 * - GET /api/analytics/stats - Estatísticas gerais do sistema
 * - GET /api/analytics/today - Estatísticas do dia atual
 * - GET /api/analytics/priority - Distribuição por prioridade
 * - GET /api/analytics/status - Distribuição por status
 * - GET /api/analytics/productivity - Taxa de produtividade
 * - GET /api/analytics/overdue - Tarefas atrasadas
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Analytics", description = "Métricas e estatísticas de produtividade")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * GET /api/analytics/stats
     * Retorna estatísticas gerais: total, pending, scheduled, done, late,
     * completionRate
     */
    @Operation(summary = "Estatísticas gerais", description = "Retorna métricas agregadas de todas as tarefas: total, pendentes, agendadas, concluídas, atrasadas e taxa de conclusão")
    @ApiResponse(responseCode = "200", description = "Estatísticas calculadas com sucesso")
    @GetMapping("/stats")
    public ResponseEntity<TaskStatsDTO> getGeneralStatistics() {
        log.info("📊 GET /api/analytics/stats - Estatísticas gerais");
        TaskStatsDTO stats = analyticsService.getGeneralStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/analytics/today
     * Retorna estatísticas filtradas para o dia atual
     */
    @GetMapping("/today")
    public ResponseEntity<TaskStatsDTO> getTodayStatistics() {
        log.info("📊 GET /api/analytics/today - Estatísticas de hoje");
        TaskStatsDTO stats = analyticsService.getTodayStatistics();
        return ResponseEntity.ok(stats);
    }

    /**
     * GET /api/analytics/priority
     * Retorna distribuição de tarefas por prioridade
     * Exemplo: { "HIGH": 5, "MEDIUM": 10, "LOW": 3 }
     */
    @GetMapping("/priority")
    public ResponseEntity<Map<String, Long>> getPriorityDistribution() {
        log.info("📊 GET /api/analytics/priority - Distribuição por prioridade");
        Map<String, Long> distribution = analyticsService.getPriorityDistribution();
        return ResponseEntity.ok(distribution);
    }

    /**
     * GET /api/analytics/status
     * Retorna distribuição de tarefas por status
     * Exemplo: { "PENDING": 8, "SCHEDULED": 5, "DONE": 12, "LATE": 2 }
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Long>> getStatusDistribution() {
        log.info("📊 GET /api/analytics/status - Distribuição por status");
        Map<String, Long> distribution = analyticsService.getStatusDistribution();
        return ResponseEntity.ok(distribution);
    }

    /**
     * GET /api/analytics/productivity
     * Retorna taxa de produtividade (0-100)
     * Cálculo: (tarefas concluídas / total de tarefas) * 100
     */
    @GetMapping("/productivity")
    public ResponseEntity<Double> getProductivityRate() {
        log.info("📊 GET /api/analytics/productivity - Taxa de produtividade");
        double rate = analyticsService.getProductivityRate();
        return ResponseEntity.ok(rate);
    }

    /**
     * GET /api/analytics/overdue
     * Retorna lista de tarefas atrasadas
     * (SCHEDULED mas com endTime já passou)
     */
    @GetMapping("/overdue")
    public ResponseEntity<List<Task>> getOverdueTasks() {
        log.info("📊 GET /api/analytics/overdue - Tarefas atrasadas");
        List<Task> overdue = analyticsService.getOverdueTasks();
        return ResponseEntity.ok(overdue);
    }

    /**
     * GET /api/analytics/avg-time
     * Retorna tempo médio de conclusão de tarefas (em horas)
     */
    @GetMapping("/avg-time")
    public ResponseEntity<Double> getAverageCompletionTime() {
        log.info("📊 GET /api/analytics/avg-time - Tempo médio de conclusão");
        double avgTime = analyticsService.getAverageCompletionTime();
        return ResponseEntity.ok(avgTime);
    }

    /**
     * GET /api/analytics/summary
     * Retorna resumo de produtividade para um período específico
     * 
     * @param start Data/hora de início do período (ISO 8601)
     * @param end Data/hora de fim do período (ISO 8601)
     * @return Resumo com métricas agregadas do período
     */
    @Operation(
        summary = "Resumo de produtividade por período",
        description = "Retorna métricas agregadas para análise temporal: tarefas planejadas, concluídas, criadas, taxa de conclusão e média por dia"
    )
    @ApiResponse(responseCode = "200", description = "Resumo calculado com sucesso")
    @GetMapping("/summary")
    public ResponseEntity<AnalyticsSummaryDTO> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        log.info("📊 GET /api/analytics/summary - Resumo de {} a {}", start, end);
        AnalyticsSummaryDTO summary = analyticsService.getSummary(start, end);
        return ResponseEntity.ok(summary);
    }
}
