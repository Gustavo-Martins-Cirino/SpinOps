package com.gustavocirino.myday_productivity.dto;

/**
 * DTO para estatísticas do dashboard
 * Retorna métricas agregadas para o painel lateral
 */
public record TaskStatsDTO(
        long totalTasks,
        long pendingTasks,
        long scheduledTasks,
        long doneTasks,
        long lateTasks,
        double completionRate // % de tarefas concluídas
) {
}
