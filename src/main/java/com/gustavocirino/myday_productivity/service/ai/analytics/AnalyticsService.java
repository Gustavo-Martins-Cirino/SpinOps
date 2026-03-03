package com.gustavocirino.myday_productivity.service.ai.analytics;

import com.gustavocirino.myday_productivity.dto.AnalyticsSummaryDTO;
import com.gustavocirino.myday_productivity.dto.AnomalyDTO;
import com.gustavocirino.myday_productivity.dto.ClassificationDTO;
import com.gustavocirino.myday_productivity.dto.ForecastDTO;
import com.gustavocirino.myday_productivity.dto.RecommendationDTO;
import com.gustavocirino.myday_productivity.dto.TaskStatsDTO;
import com.gustavocirino.myday_productivity.model.Task;
import com.gustavocirino.myday_productivity.model.enums.TaskPriority;
import com.gustavocirino.myday_productivity.model.enums.TaskStatus;
import com.gustavocirino.myday_productivity.repository.TaskRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
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
    private final ChatLanguageModel chatModel;

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
        distribution.put("CRITICAL", allTasks.stream().filter(t -> t.getPriority() == TaskPriority.CRITICAL).count());
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
     * Distribuição de OS por hora do dia (0–23).
     * Usa o horário de criação (createdAt) da tarefa.
     */
    public Map<Integer, Long> getHourlyDistribution() {
        log.info("📊 Calculando distribuição por hora do dia");
        List<Task> allTasks = taskRepository.findAll();
        Map<Integer, Long> distribution = new java.util.LinkedHashMap<>();
        for (int h = 0; h < 24; h++) distribution.put(h, 0L);
        allTasks.stream()
                .filter(t -> t.getCreatedAt() != null)
                .forEach(t -> distribution.merge(t.getCreatedAt().getHour(), 1L, Long::sum));
        return distribution;
    }

    /**
     * Distribuição de OS por tag/categoria.
     * Usa o campo {@code category} da tarefa como rótulo.
     */
    public Map<String, Long> getTagDistribution() {
        log.info("📊 Calculando distribuição por tag/categoria");
        List<Task> allTasks = taskRepository.findAll();
        Map<String, Long> distribution = new java.util.LinkedHashMap<>();
        allTasks.forEach(t -> {
            if (t.getTags() != null && !t.getTags().isEmpty()) {
                t.getTags().forEach(tag -> {
                    String label = (tag.getName() != null && !tag.getName().isBlank())
                            ? tag.getName().trim() : "Sem tag";
                    distribution.merge(label, 1L, Long::sum);
                });
            } else {
                distribution.merge("Sem tag", 1L, Long::sum);
            }
        });
        return distribution;
    }

    /**
     * Série temporal diária dos últimos {@code days} dias.
     * Cada ponto retorna: date (yyyy-MM-dd), created (OS criadas), done (OS concluídas).
     */
    public List<Map<String, Object>> getDailyTrend(int days) {
        log.info("📊 Calculando tendência diária dos últimos {} dias", days);
        List<Task> allTasks = taskRepository.findAll();
        List<Map<String, Object>> series = new java.util.ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime dayStart = LocalDate.now().minusDays(i).atStartOfDay();
            LocalDateTime dayEnd   = dayStart.plusDays(1);

            long created = allTasks.stream()
                    .filter(t -> t.getCreatedAt() != null
                            && !t.getCreatedAt().isBefore(dayStart)
                            && t.getCreatedAt().isBefore(dayEnd))
                    .count();

            // completedAt tem prioridade; fallback para createdAt para OS antigas sem o campo
            long done = allTasks.stream()
                    .filter(t -> {
                        if (t.getStatus() != TaskStatus.DONE) return false;
                        LocalDateTime dt = t.getCompletedAt() != null ? t.getCompletedAt() : t.getCreatedAt();
                        return dt != null && !dt.isBefore(dayStart) && dt.isBefore(dayEnd);
                    })
                    .count();

            Map<String, Object> point = new java.util.LinkedHashMap<>();
            point.put("date",    dayStart.toLocalDate().toString());
            point.put("created", created);
            point.put("done",    done);
            series.add(point);
        }
        return series;
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

    /**
     * Previsão de produtividade (forecasting).
     *
     * Coleta o histórico de OS concluídas/criadas por dia dos últimos {@code historicDays} dias,
     * aplica regressão linear simples sobre as conclusões e projeta os próximos {@code horizon} dias.
     *
     * @param historicDays Número de dias históricos a considerar (padrão: 14)
     * @param horizon      Dias à frente a projetar (padrão: 7)
     */
    public ForecastDTO getForecast(int historicDays, int horizon) {
        log.info("📈 Calculando forecast: {} dias históricos → {} dias à frente", historicDays, horizon);

        List<Task> allTasks = taskRepository.findAll();

        // === SÉRIE HISTÓRICA ===
        List<ForecastDTO.DayPoint> historical = new java.util.ArrayList<>();
        long[] doneSeries = new long[historicDays];

        for (int i = historicDays - 1; i >= 0; i--) {
            LocalDateTime dayStart = LocalDate.now().minusDays(i).atStartOfDay();
            LocalDateTime dayEnd   = dayStart.plusDays(1);

            // completedAt tem prioridade; fallback para createdAt para OS antigas sem o campo
            long done = allTasks.stream()
                    .filter(t -> {
                        if (t.getStatus() != TaskStatus.DONE) return false;
                        LocalDateTime dt = t.getCompletedAt() != null ? t.getCompletedAt() : t.getCreatedAt();
                        return dt != null && !dt.isBefore(dayStart) && dt.isBefore(dayEnd);
                    })
                    .count();

            long created = allTasks.stream()
                    .filter(t -> t.getCreatedAt() != null
                            && !t.getCreatedAt().isBefore(dayStart)
                            && t.getCreatedAt().isBefore(dayEnd))
                    .count();

            String date = dayStart.toLocalDate().toString();
            historical.add(new ForecastDTO.DayPoint(date, done, created, false));
            doneSeries[historicDays - 1 - i] = done;
        }

        // === REGRESSÃO LINEAR SIMPLES (mínimos quadrados) sobre doneSeries ===
        int n = doneSeries.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX  += i;
            sumY  += doneSeries[i];
            sumXY += i * doneSeries[i];
            sumX2 += (double) i * i;
        }
        double denom = n * sumX2 - sumX * sumX;
        double slope     = denom != 0 ? (n * sumXY - sumX * sumY) / denom : 0;
        double intercept = (sumY - slope * sumX) / n;

        double avgDailyCompletion = sumY / Math.max(1, n);

        String trend;
        if      (slope >  0.05) trend = "up";
        else if (slope < -0.05) trend = "down";
        else                    trend = "stable";

        // === SÉRIE DE FORECAST ===
        List<ForecastDTO.DayPoint> forecast = new java.util.ArrayList<>();
        for (int i = 1; i <= horizon; i++) {
            LocalDate futureDate     = LocalDate.now().plusDays(i);
            double projected         = intercept + slope * (n + i - 1);
            long projectedClamped    = Math.max(0, Math.round(projected));

            forecast.add(new ForecastDTO.DayPoint(futureDate.toString(), projectedClamped, 0L, true));
        }

        // === MÉTRICAS DE QUALIDADE DO MODELO ===
        double ssRes = 0, ssTot = 0, maeAcc = 0;
        for (int i = 0; i < n; i++) {
            double predicted = intercept + slope * i;
            double actual    = doneSeries[i];
            ssRes   += Math.pow(actual - predicted, 2);
            ssTot   += Math.pow(actual - avgDailyCompletion, 2);
            maeAcc  += Math.abs(actual - predicted);
        }
        double r2     = ssTot > 0 ? Math.max(0.0, 1.0 - ssRes / ssTot) : 0.0;
        double mae    = maeAcc / Math.max(1, n);
        double stdDev = Math.sqrt(ssRes / Math.max(1, n));

        log.info("📈 Forecast concluído: slope={}, trend={}, média={}, R²={}, MAE={}",
                String.format("%.3f", slope), trend,
                String.format("%.1f", avgDailyCompletion),
                String.format("%.2f", r2),
                String.format("%.2f", mae));
        return new ForecastDTO(historical, forecast, avgDailyCompletion, trend, horizon, r2, mae, stdDev);
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

    // ==================== DETECÇÃO DE ANOMALIAS ====================

    /**
     * Detecta anomalias via LLM (Gemini) com fallback estatístico z-score.
     * A IA analisa a série histórica de produtividade e identifica padrões anômalos
     * usando raciocínio de modelo ML sobre tendências e desvios.
     *
     * @param days Janela histórica (padrão: 30)
     */
    @Transactional(readOnly = true)
    public java.util.List<AnomalyDTO> detectAnomalies(int days) {
        log.info("🤖 Detectando anomalias (AI+ML) nos últimos {} dias", days);
        java.util.List<Task> allTasks = taskRepository.findAll();
        LocalDate today = LocalDate.now();

        long[]   doneSeries    = new long[days];
        long[]   createdSeries = new long[days];
        String[] dates         = new String[days];
        StringBuilder seriesJson = new StringBuilder("[");

        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime dayStart = today.minusDays(i).atStartOfDay();
            LocalDateTime dayEnd   = dayStart.plusDays(1);
            int idx = days - 1 - i;
            dates[idx] = today.minusDays(i).toString();

            // completedAt tem prioridade; fallback para createdAt para OS antigas sem o campo
            doneSeries[idx] = allTasks.stream()
                    .filter(t -> {
                        if (t.getStatus() != TaskStatus.DONE) return false;
                        LocalDateTime dt = t.getCompletedAt() != null ? t.getCompletedAt() : t.getCreatedAt();
                        return dt != null && !dt.isBefore(dayStart) && dt.isBefore(dayEnd);
                    })
                    .count();
            createdSeries[idx] = allTasks.stream()
                    .filter(t -> t.getCreatedAt() != null
                            && !t.getCreatedAt().isBefore(dayStart)
                            && t.getCreatedAt().isBefore(dayEnd))
                    .count();
            if (idx > 0) seriesJson.append(",");
            seriesJson.append(String.format("{\"date\":\"%s\",\"done\":%d,\"created\":%d}",
                    dates[idx], doneSeries[idx], createdSeries[idx]));
        }
        seriesJson.append("]");

        long lateTasks  = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.LATE).count();
        long totalTasks = allTasks.size();
        double lateRate = totalTasks > 0 ? lateTasks * 100.0 / totalTasks : 0;

        // === ANÁLISE VIA LLM (Gemini) ===
        String prompt = String.format(
            "Você é um analista de operações de manutenção industrial especializado em detecção de anomalias por ML.%n" +
            "Analise esta série temporal de produtividade dos últimos %d dias:%n" +
            "{\"period\":\"últimos %d dias\",\"dailySeries\":%s," +
            "\"summary\":{\"totalLate\":%d,\"totalTasks\":%d,\"lateRatePercent\":%.1f}}%n" +
            "Identifique anomalias: quedas de produtividade (z-score), picos de criação, acúmulo de atrasos e períodos de inatividade.%n" +
            "Responda APENAS com array JSON válido sem markdown:%n" +
            "[{\"type\":\"PRODUCTIVITY_DROP|TASK_ACCUMULATION|LATE_SPIKE|IDLE_PERIOD\"," +
            "\"severity\":\"LOW|MEDIUM|HIGH|CRITICAL\",\"date\":\"YYYY-MM-DD\"," +
            "\"message\":\"Explicação em português\",\"metadata\":{}}]%n" +
            "Se não houver anomalias retorne exatamente: []",
            days, days, seriesJson.toString(), lateTasks, totalTasks, lateRate);

        try {
            String aiResponse = chatModel.generate(prompt);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\[.*?\\]", java.util.regex.Pattern.DOTALL)
                    .matcher(aiResponse);
            if (m.find()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.List<java.util.Map<String, Object>> items = mapper.readValue(
                        m.group(),
                        new com.fasterxml.jackson.core.type.TypeReference<
                            java.util.List<java.util.Map<String, Object>>>() {});
                java.util.List<AnomalyDTO> result = new java.util.ArrayList<>();
                for (var item : items) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> meta = item.containsKey("metadata")
                            ? (java.util.Map<String, Object>) item.get("metadata")
                            : java.util.Map.of();
                    result.add(new AnomalyDTO(
                            String.valueOf(item.getOrDefault("type", "UNKNOWN")),
                            String.valueOf(item.getOrDefault("severity", "MEDIUM")),
                            String.valueOf(item.getOrDefault("date", today.toString())),
                            String.valueOf(item.getOrDefault("message", "")),
                            meta));
                }
                java.util.Map<String, Integer> ord = java.util.Map.of("CRITICAL",0,"HIGH",1,"MEDIUM",2,"LOW",3);
                result.sort(java.util.Comparator.comparingInt(a -> ord.getOrDefault(a.severity(), 4)));
                log.info("🤖 IA detectou {} anomalia(s)", result.size());
                return result;
            }
        } catch (Exception e) {
            log.warn("⚠️ Detecção por IA falhou, usando fallback estatístico: {}", e.getMessage());
        }
        return detectAnomaliesStatistical(days, doneSeries, createdSeries, dates, allTasks, today);
    }

    /** Fallback estatístico z-score quando a IA não está disponível. */
    private java.util.List<AnomalyDTO> detectAnomaliesStatistical(
            int days, long[] doneSeries, long[] createdSeries, String[] dates,
            java.util.List<Task> allTasks, LocalDate today) {

        double sumD = 0; for (long v : doneSeries) sumD += v;
        double mD = sumD / Math.max(1, days);
        double vD = 0; for (long v : doneSeries) vD += Math.pow(v - mD, 2);
        double sD = Math.sqrt(vD / Math.max(1, days));

        double sumC = 0; for (long v : createdSeries) sumC += v;
        double mC = sumC / Math.max(1, days);
        double vC = 0; for (long v : createdSeries) vC += Math.pow(v - mC, 2);
        double sC = Math.sqrt(vC / Math.max(1, days));

        java.util.List<AnomalyDTO> anomalies = new java.util.ArrayList<>();
        if (mD > 0.5 && sD > 0) {
            int start = Math.max(0, days - 15);
            for (int i = start; i < days - 1; i++) {
                if (doneSeries[i] < mD - 1.5 * sD && doneSeries[i] < mD * 0.5)
                    anomalies.add(new AnomalyDTO("PRODUCTIVITY_DROP", doneSeries[i] == 0 ? "HIGH" : "MEDIUM",
                            dates[i], String.format("Queda em %s: %d OS (média %.1f/dia)", dates[i], doneSeries[i], mD),
                            java.util.Map.of("done", doneSeries[i], "mean", Math.round(mD * 10.0) / 10.0)));
            }
        }
        if (mC > 0 && sC > 0) {
            int start = Math.max(0, days - 14);
            for (int i = start; i < days; i++)
                if (createdSeries[i] > mC + 2.0 * sC)
                    anomalies.add(new AnomalyDTO("TASK_ACCUMULATION", "MEDIUM", dates[i],
                            String.format("Pico: %d OS criadas em %s (média %.1f/dia)", createdSeries[i], dates[i], mC),
                            java.util.Map.of("created", createdSeries[i])));
        }
        long total = allTasks.size();
        long late  = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.LATE).count();
        if (total > 0 && late > 0) {
            double r = late * 100.0 / total;
            if (r > 15.0 || late >= 5)
                anomalies.add(new AnomalyDTO("LATE_SPIKE",
                        r > 30 ? "CRITICAL" : r > 15 ? "HIGH" : "MEDIUM", today.toString(),
                        String.format("%d OS atrasadas (%.0f%% do total)", late, r),
                        java.util.Map.of("lateCount", late, "lateRate", Math.round(r * 10.0) / 10.0)));
        }
        int streak = 0, maxIdle = 0, endIdx = -1;
        for (int i = 0; i < days; i++) {
            if (doneSeries[i] == 0 && createdSeries[i] == 0) { if (++streak > maxIdle) { maxIdle = streak; endIdx = i; } }
            else streak = 0;
        }
        if (maxIdle >= 3 && endIdx >= 0)
            anomalies.add(new AnomalyDTO("IDLE_PERIOD", maxIdle >= 5 ? "HIGH" : "MEDIUM", dates[endIdx],
                    String.format("%d dias consecutivos sem atividade", maxIdle),
                    java.util.Map.of("idleDays", maxIdle)));

        java.util.Map<String, Integer> ord = java.util.Map.of("CRITICAL",0,"HIGH",1,"MEDIUM",2,"LOW",3);
        anomalies.sort(java.util.Comparator.comparingInt(a -> ord.getOrDefault(a.severity(), 4)));
        log.info("🔍 (fallback) {} anomalia(s)", anomalies.size());
        return anomalies;
    }

    // ==================== CORRELAÇÃO ====================


    /**
     * Retorna correlações operacionais: conclusões/atrasos por dia da semana e por prioridade.
     */
    public java.util.Map<String, Object> getCorrelation() {
        log.info("📈 Calculando correlações operacionais");
        java.util.List<Task> allTasks = taskRepository.findAll();
        String[] dayNames = {"Dom", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb"};
        long[] completionByDOW = new long[7];
        long[] lateByDOW       = new long[7];
        allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE && t.getCompletedAt() != null)
                .forEach(t -> completionByDOW[t.getCompletedAt().getDayOfWeek().getValue() % 7]++);
        allTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.LATE && t.getStartTime() != null)
                .forEach(t -> lateByDOW[t.getStartTime().getDayOfWeek().getValue() % 7]++);

        java.util.Map<String, Long> completionByPriority = new java.util.LinkedHashMap<>();
        java.util.Map<String, Long> lateByPriority       = new java.util.LinkedHashMap<>();
        for (TaskPriority prio : new TaskPriority[]{TaskPriority.CRITICAL, TaskPriority.HIGH, TaskPriority.MEDIUM, TaskPriority.LOW}) {
            completionByPriority.put(prio.name(),
                allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE && t.getPriority() == prio).count());
            lateByPriority.put(prio.name(),
                allTasks.stream().filter(t -> t.getStatus() == TaskStatus.LATE && t.getPriority() == prio).count());
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("dayLabels",            dayNames);
        result.put("completionByDayOfWeek", completionByDOW);
        result.put("lateByDayOfWeek",       lateByDOW);
        result.put("completionByPriority",  completionByPriority);
        result.put("lateByPriority",        lateByPriority);
        return result;
    }

    // ==================== INSIGHTS NARRATIVOS ====================

    /**
     * Gera um resumo executivo em linguagem natural via Gemini, analisando
     * os dados operacionais e retornando insights acionáveis.
     * Fallback local quando a IA não está disponível.
     */
    public String getInsights() {
        log.info("🧠 Gerando insights narrativos via IA");
        java.util.List<Task> allTasks = taskRepository.findAll();

        long total    = allTasks.size();
        long done     = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        long late     = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.LATE).count();
        long pending  = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();
        double compRate = total > 0 ? (done * 100.0 / total) : 0.0;
        double lateRate = total > 0 ? (late * 100.0 / total) : 0.0;

        ForecastDTO fc = getForecast(14, 7);
        java.util.Map<String, Long> prio = getPriorityDistribution();

        String ctx = String.format(
            "{\"totalOS\":%d,\"concluidas\":%d,\"atrasadas\":%d,\"pendentes\":%d," +
            "\"taxaConclusaoPct\":%.1f,\"taxaAtrasoPct\":%.1f," +
            "\"tendencia7dias\":\"%s\",\"mediaConclusioesDia\":%.1f," +
            "\"r2Modelo\":%.2f,\"maeModelo\":%.2f," +
            "\"distribuicaoPrioridade\":{\"alta\":%d,\"media\":%d,\"baixa\":%d}}",
            total, done, late, pending, compRate, lateRate,
            fc.trend(), fc.avgDailyCompletion(), fc.r2(), fc.mae(),
            prio.getOrDefault("HIGH", 0L),
            prio.getOrDefault("MEDIUM", 0L),
            prio.getOrDefault("LOW", 0L));

        String prompt =
            "Você é um analista sênior de operações de manutenção industrial. " +
            "Com base nestes dados reais da equipe, escreva um resumo executivo de 3 a 4 frases " +
            "destacando: padrões identificados, tendências críticas e UMA recomendação acionável clara. " +
            "Seja direto e objetivo. Não use markdown, listas, bullets nem títulos. " +
            "Responda apenas com o texto narrativo em português brasileiro.\n\nDados:\n" + ctx;

        try {
            String narrative = chatModel.generate(prompt);
            log.info("🧠 Insights gerados com sucesso ({} chars)", narrative.length());
            return narrative;
        } catch (Exception e) {
            log.warn("⚠️ Insights IA falhou, usando fallback: {}", e.getMessage());
            String trendPt = "up".equals(fc.trend()) ? "crescente" :
                             "down".equals(fc.trend()) ? "declinante" : "estável";
            String qualidade = fc.r2() >= 0.7 ? "alta" : fc.r2() >= 0.4 ? "média" : "baixa";
            String recom = late > 0
                ? String.format("Recomendação: priorize a resolução das %d OS atrasadas para reduzir exposição ao risco operacional.", late)
                : "Continue mantendo o bom ritmo de conclusão de ordens de serviço.";
            return String.format(
                "A equipe registra %.0f%% de taxa de conclusão com %d OS totais, %d atrasadas e %d pendentes. " +
                "A tendência de produtividade é %s (média de %.1f OS concluídas/dia nos últimos 14 dias). " +
                "O modelo preditivo apresenta confiabilidade %s (R²=%.2f, MAE=%.1f OS/dia). %s",
                compRate, total, late, pending, trendPt,
                fc.avgDailyCompletion(), qualidade, fc.r2(), fc.mae(), recom);
        }
    }

    // ==================== CLASSIFICAÇÃO DE OS ====================

    /**
     * Classifica as OS abertas por categoria técnica via GROQ.
     * Categorias: ELETRICA | MECANICA | HIDRAULICA | INSTRUMENTACAO | CIVIL | OUTRO
     * Fallback: classificação por palavras-chave quando a IA não está disponível.
     */
    @Transactional(readOnly = true)
    public java.util.List<ClassificationDTO> classifyTasks() {
        log.info("🏷️ Classificando OS por categoria técnica via IA");
        java.util.List<Task> tasks = taskRepository.findAll().stream()
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .limit(50)
                .toList();

        if (tasks.isEmpty()) return java.util.List.of();

        StringBuilder taskJson = new StringBuilder("[");
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            if (i > 0) taskJson.append(",");
            String title = (t.getTitle() != null ? t.getTitle() : "").replace("\"", "'");
            String desc  = (t.getDescription() != null ? t.getDescription() : "").replace("\"", "'");
            taskJson.append(String.format("{\"id\":%d,\"title\":\"%s\",\"desc\":\"%s\"}",
                    t.getId(), title, desc.length() > 100 ? desc.substring(0, 100) : desc));
        }
        taskJson.append("]");

        String prompt = String.format(
            "Você é especialista em manutenção industrial. Classifique cada OS na lista abaixo em UMA das categorias:%n" +
            "ELETRICA, MECANICA, HIDRAULICA, INSTRUMENTACAO, CIVIL, OUTRO%n" +
            "Lista de OS:%n%s%n" +
            "Responda APENAS com JSON válido sem markdown:%n" +
            "[{\"taskId\":1,\"category\":\"ELETRICA\",\"confidence\":0.9}, ...]",
            taskJson.toString());

        try {
            String aiResp = chatModel.generate(prompt);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\[.*?\\]", java.util.regex.Pattern.DOTALL)
                    .matcher(aiResp);
            if (m.find()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.List<java.util.Map<String, Object>> items = mapper.readValue(
                        m.group(),
                        new com.fasterxml.jackson.core.type.TypeReference<
                            java.util.List<java.util.Map<String, Object>>>() {});

                java.util.Map<Long, String> titleMap = new java.util.HashMap<>();
                tasks.forEach(t -> titleMap.put(t.getId(), t.getTitle() != null ? t.getTitle() : ""));

                java.util.List<ClassificationDTO> result = new java.util.ArrayList<>();
                for (var item : items) {
                    Long tid = item.containsKey("taskId")
                            ? ((Number) item.get("taskId")).longValue() : -1L;
                    String cat = String.valueOf(item.getOrDefault("category", "OUTRO")).toUpperCase();
                    double conf = item.containsKey("confidence")
                            ? ((Number) item.get("confidence")).doubleValue() : 0.75;
                    result.add(new ClassificationDTO(tid, titleMap.getOrDefault(tid, ""), cat, conf));
                }
                log.info("🏷️ IA classificou {} OS", result.size());
                return result;
            }
        } catch (Exception e) {
            log.warn("⚠️ Classificação por IA falhou, usando fallback por palavras-chave: {}", e.getMessage());
        }
        return classifyByKeywords(tasks);
    }

    /** Fallback por palavras-chave quando a IA não está disponível. */
    private java.util.List<ClassificationDTO> classifyByKeywords(java.util.List<Task> tasks) {
        java.util.List<ClassificationDTO> result = new java.util.ArrayList<>();
        for (Task t : tasks) {
            String text = ((t.getTitle() != null ? t.getTitle() : "") + " " +
                          (t.getDescription() != null ? t.getDescription() : "")).toLowerCase();
            String cat;
            double conf;
            if (text.matches(".*\\b(eletri[ck]|tensao|corrente|transformador|disjuntor|rele|fusivel|motor el|curto|sobrecarga)\\b.*")) {
                cat = "ELETRICA"; conf = 0.80;
            } else if (text.matches(".*\\b(mecan|rolamento|engrenagem|vibra|desgaste|lubrif|correia|redut|acoplam)\\b.*")) {
                cat = "MECANICA"; conf = 0.80;
            } else if (text.matches(".*\\b(hidraul|bomba|fluido|pressao|vazam|tubulaç|valvula hidro|oleo)\\b.*")) {
                cat = "HIDRAULICA"; conf = 0.80;
            } else if (text.matches(".*\\b(sensor|transmissor|calibr|instrumento|medidor|alarme|cLP|plc|scada|iHM)\\b.*")) {
                cat = "INSTRUMENTACAO"; conf = 0.80;
            } else if (text.matches(".*\\b(estrutur|civil|alvenaria|pintura|fissura|telhado|parede|piso)\\b.*")) {
                cat = "CIVIL"; conf = 0.80;
            } else {
                cat = "OUTRO"; conf = 0.55;
            }
            result.add(new ClassificationDTO(t.getId(), t.getTitle() != null ? t.getTitle() : "", cat, conf));
        }
        return result;
    }

    // ==================== RECOMENDAÇÕES DE OTIMIZAÇÃO ====================

    /**
     * Gera recomendações estruturadas de otimização operacional via GROQ.
     * Fallback: recomendações baseadas em regras quando a IA não está disponível.
     */
    @Transactional(readOnly = true)
    public java.util.List<RecommendationDTO> getRecommendations() {
        log.info("💡 Gerando recomendações de otimização via IA");
        java.util.List<Task> allTasks = taskRepository.findAll();

        long total   = allTasks.size();
        long done    = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        long late    = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.LATE).count();
        long pending = allTasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING).count();
        double lateRate = total > 0 ? late * 100.0 / total : 0;
        double compRate = total > 0 ? done * 100.0 / total : 0;

        ForecastDTO fc = getForecast(14, 7);

        long highPrio = allTasks.stream()
                .filter(t -> t.getPriority() == TaskPriority.HIGH || t.getPriority() == TaskPriority.CRITICAL)
                .filter(t -> t.getStatus() != TaskStatus.DONE)
                .count();

        String ctx = String.format(
            "{\"total\":%d,\"concluidas\":%d,\"atrasadas\":%d,\"pendentes\":%d," +
            "\"altaPrioridadeAbertas\":%d,\"taxaConclusao\":%.1f,\"taxaAtraso\":%.1f," +
            "\"tendencia\":\"%s\",\"mediaConclusioesDia\":%.1f}",
            total, done, late, pending, highPrio, compRate, lateRate,
            fc.trend(), fc.avgDailyCompletion());

        String prompt =
            "Você é um consultor sênior de manutenção industrial. Com base nestes dados operacionais, " +
            "gere exatamente 4 recomendações de otimização claras e acionáveis.\n" +
            "Dados: " + ctx + "\n" +
            "Responda APENAS com JSON válido sem markdown:\n" +
            "[{\"action\":\"texto da ação (≤80 chars)\",\"impact\":\"ALTA|MEDIA|BAIXA\"," +
            "\"priority\":\"URGENTE|IMPORTANTE|ROTINA\",\"category\":\"Área afetada (≤30 chars)\"}, ...]";

        try {
            String aiResp = chatModel.generate(prompt);
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\\[.*?\\]", java.util.regex.Pattern.DOTALL)
                    .matcher(aiResp);
            if (m.find()) {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.List<java.util.Map<String, Object>> items = mapper.readValue(
                        m.group(),
                        new com.fasterxml.jackson.core.type.TypeReference<
                            java.util.List<java.util.Map<String, Object>>>() {});
                java.util.List<RecommendationDTO> result = new java.util.ArrayList<>();
                for (var item : items) {
                    result.add(new RecommendationDTO(
                            String.valueOf(item.getOrDefault("action",  "Revisar processos operacionais")),
                            String.valueOf(item.getOrDefault("impact",  "MEDIA")),
                            String.valueOf(item.getOrDefault("priority","IMPORTANTE")),
                            String.valueOf(item.getOrDefault("category","Operações"))));
                }
                log.info("💡 IA gerou {} recomendação(ões)", result.size());
                return result;
            }
        } catch (Exception e) {
            log.warn("⚠️ Recomendações por IA falhou, usando fallback por regras: {}", e.getMessage());
        }
        return buildFallbackRecommendations(late, pending, highPrio, lateRate, compRate, fc.trend());
    }

    /** Fallback baseado em regras quando a IA não está disponível. */
    private java.util.List<RecommendationDTO> buildFallbackRecommendations(
            long late, long pending, long highPrio,
            double lateRate, double compRate, String trend) {
        java.util.List<RecommendationDTO> recs = new java.util.ArrayList<>();
        if (late > 0) {
            String imp = lateRate > 20 ? "ALTA" : "MEDIA";
            String pri = lateRate > 20 ? "URGENTE" : "IMPORTANTE";
            recs.add(new RecommendationDTO(
                    String.format("Resolver as %d OS atrasadas priorizando alta prioridade", late),
                    imp, pri, "Gestão de Prazos"));
        }
        if (highPrio > 0) {
            recs.add(new RecommendationDTO(
                    String.format("Escalonar %d OS de alta prioridade ainda em aberto", highPrio),
                    "ALTA", "URGENTE", "Priorização"));
        }
        if ("down".equals(trend)) {
            recs.add(new RecommendationDTO(
                    "Tendência de queda detectada — revisar capacidade da equipe",
                    "ALTA", "IMPORTANTE", "Capacidade Operacional"));
        }
        if (pending > 5) {
            recs.add(new RecommendationDTO(
                    String.format("Distribuir %d OS pendentes entre os técnicos disponíveis", pending),
                    "MEDIA", "ROTINA", "Distribuição de Carga"));
        }
        if (compRate > 80) {
            recs.add(new RecommendationDTO(
                    "Manter ritmo atual e documentar boas práticas da equipe",
                    "BAIXA", "ROTINA", "Conhecimento"));
        }
        if (recs.size() < 2) {
            recs.add(new RecommendationDTO(
                    "Registrar lições aprendidas das OS concluídas este mês",
                    "BAIXA", "ROTINA", "Gestão do Conhecimento"));
            recs.add(new RecommendationDTO(
                    "Revisar periodicidade de manutenção preventiva dos equipamentos críticos",
                    "MEDIA", "IMPORTANTE", "Manutenção Preventiva"));
        }
        return recs.subList(0, Math.min(recs.size(), 4));
    }
}
