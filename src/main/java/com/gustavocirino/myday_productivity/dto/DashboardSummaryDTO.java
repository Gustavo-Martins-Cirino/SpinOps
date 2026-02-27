package com.gustavocirino.myday_productivity.dto;

/**
 * DTO principal para o Dashboard de Alta Performance.
 * Resume o estado geral das tarefas e a carga cognitiva atual.
 */
public record DashboardSummaryDTO(
        long totalTasks,
        long completedTasks,
        long pendingTasks,
        int cognitiveLoad // 0-100 (% de carga cognitiva)
) {
}
