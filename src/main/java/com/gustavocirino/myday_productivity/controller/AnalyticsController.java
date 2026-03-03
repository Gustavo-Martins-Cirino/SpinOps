package com.gustavocirino.myday_productivity.controller;

import com.gustavocirino.myday_productivity.dto.AnalyticsSummaryDTO;
import com.gustavocirino.myday_productivity.dto.AnomalyDTO;
import com.gustavocirino.myday_productivity.dto.ClassificationDTO;
import com.gustavocirino.myday_productivity.dto.ForecastDTO;
import com.gustavocirino.myday_productivity.dto.RecommendationDTO;
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

    /**
     * GET /api/analytics/hourly
     * Distribuição de OS por hora do dia (0–23)
     */
    @Operation(summary = "Distribuição por hora do dia", description = "Contagem de OSs agrupadas por hora de criação")
    @ApiResponse(responseCode = "200", description = "Distribuição calculada com sucesso")
    @GetMapping("/hourly")
    public ResponseEntity<Map<Integer, Long>> getHourlyDistribution() {
        log.info("📊 GET /api/analytics/hourly - Distribuição por hora");
        Map<Integer, Long> distribution = analyticsService.getHourlyDistribution();
        return ResponseEntity.ok(distribution);
    }

    /**
     * GET /api/analytics/tags
     * Distribuição de OS por tag/categoria
     */
    @Operation(summary = "Distribuição por tag/categoria", description = "Contagem de OSs por tag")
    @ApiResponse(responseCode = "200", description = "Distribuição calculada com sucesso")
    @GetMapping("/tags")
    public ResponseEntity<Map<String, Long>> getTagDistribution() {
        log.info("📊 GET /api/analytics/tags - Distribuição por tag");
        Map<String, Long> distribution = analyticsService.getTagDistribution();
        return ResponseEntity.ok(distribution);
    }

    /**
     * GET /api/analytics/trend?days=N
     * Série temporal diária dos últimos N dias: { date, created, done }
     */
    @Operation(summary = "Tendência diária (série temporal)", description = "Array de pontos diários com OS criadas e concluídas por dia")
    @ApiResponse(responseCode = "200", description = "Tendência calculada com sucesso")
    @GetMapping("/trend")
    public ResponseEntity<List<Map<String, Object>>> getDailyTrend(
            @RequestParam(defaultValue = "14") int days) {
        log.info("📊 GET /api/analytics/trend - Últimos {} dias", days);
        List<Map<String, Object>> trend = analyticsService.getDailyTrend(days);
        return ResponseEntity.ok(trend);
    }

    /**
     * GET /api/analytics/forecast?historicDays=14&horizon=7
     * Previsão de produtividade: série histórica + projeção via regressão linear.
     * Retorna tendência (up/stable/down), média diária e pontos do forecast.
     */
    @Operation(summary = "Previsão de produtividade (forecasting)", description = "Regressão linear sobre OS concluídas/dia. Retorna série histórica e projeção futura com tendência.")
    @ApiResponse(responseCode = "200", description = "Previsão calculada com sucesso")
    @GetMapping("/forecast")
    public ResponseEntity<ForecastDTO> getForecast(
            @RequestParam(defaultValue = "14") int historicDays,
            @RequestParam(defaultValue = "7")  int horizon) {
        log.info("📈 GET /api/analytics/forecast - histórico={} dias, horizonte={} dias", historicDays, horizon);
        ForecastDTO forecast = analyticsService.getForecast(historicDays, horizon);
        return ResponseEntity.ok(forecast);
    }

    /**
     * GET /api/analytics/anomalies?days=30
     * Detecta anomalias estatísticas de produtividade:
     * PRODUCTIVITY_DROP | TASK_ACCUMULATION | LATE_SPIKE | IDLE_PERIOD
     */
    @Operation(
        summary = "Detecção de anomalias de produtividade",
        description = "Analisa série temporal dos últimos N dias e sinaliza: quedas de produtividade, picos de abertura de OS, acúmulo de atrasos e períodos inativos."
    )
    @ApiResponse(responseCode = "200", description = "Lista de anomalias detectadas (pode ser vazia)")
    @GetMapping("/anomalies")
    public ResponseEntity<java.util.List<AnomalyDTO>> getAnomalies(
            @RequestParam(defaultValue = "30") int days) {
        log.info("🔍 GET /api/analytics/anomalies - janela={} dias", days);
        java.util.List<AnomalyDTO> anomalies = analyticsService.detectAnomalies(days);
        return ResponseEntity.ok(anomalies);
    }

    /**
     * GET /api/analytics/correlation
     * Retorna correlações operacionais: conclusões/atrasos por dia da semana e por prioridade.
     */
    @GetMapping("/correlation")
    public ResponseEntity<java.util.Map<String, Object>> getCorrelation() {
        log.info("📈 GET /api/analytics/correlation");
        return ResponseEntity.ok(analyticsService.getCorrelation());
    }

    /**
     * GET /api/analytics/insights
     * Gera resumo executivo em linguagem natural via GROQ analisando todos os dados operacionais.
     * Retorna: { "narrative": "texto gerado pela IA" }
     */
    @Operation(
        summary = "Insights narrativos via IA",
        description = "GROQ analisa todos os dados operacionais e retorna um resumo executivo em linguagem natural com insights e recomendações acionáveis."
    )
    @ApiResponse(responseCode = "200", description = "Narrative gerada com sucesso")
    @GetMapping("/insights")
    public ResponseEntity<java.util.Map<String, String>> getInsights() {
        log.info("🧠 GET /api/analytics/insights");
        String narrative = analyticsService.getInsights();
        return ResponseEntity.ok(java.util.Map.of("narrative", narrative));
    }

    /**
     * GET /api/analytics/classify
     * Classifica as OS abertas por categoria técnica usando IA (GROQ) com fallback por palavras-chave.
     * Categorias: ELETRICA | MECANICA | HIDRAULICA | INSTRUMENTACAO | CIVIL | OUTRO
     */
    @Operation(
        summary = "Classificação automática de OS por categoria técnica",
        description = "Usa IA para classificar cada OS aberta em categoria técnica (Elétrica, Mecânica, etc.) com nível de confiança."
    )
    @ApiResponse(responseCode = "200", description = "Lista de classificações calculada com sucesso")
    @GetMapping("/classify")
    public ResponseEntity<List<ClassificationDTO>> classifyTasks() {
        log.info("🏷️ GET /api/analytics/classify");
        List<ClassificationDTO> result = analyticsService.classifyTasks();
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/analytics/recommendations
     * Gera recomendações estruturadas de otimização operacional via GROQ.
     * Retorna: [{ action, impact, priority, category }]
     */
    @Operation(
        summary = "Recomendações de otimização operacional via IA",
        description = "GROQ analisa dados operacionais e retorna 4 recomendações estruturadas com impacto, urgência e área afetada."
    )
    @ApiResponse(responseCode = "200", description = "Recomendações geradas com sucesso")
    @GetMapping("/recommendations")
    public ResponseEntity<List<RecommendationDTO>> getRecommendations() {
        log.info("💡 GET /api/analytics/recommendations");
        List<RecommendationDTO> result = analyticsService.getRecommendations();
        return ResponseEntity.ok(result);
    }
}
