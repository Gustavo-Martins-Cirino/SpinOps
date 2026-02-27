package com.gustavocirino.myday_productivity.dto;

import java.time.LocalDateTime;

/**
 * DTO para resumo de produtividade em um período
 * Retorna métricas agregadas para análise temporal
 */
public record AnalyticsSummaryDTO(
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        long totalPlanned,
        long totalCompleted,
        double completionRate,
        long totalCreated,
        double avgPlannedPerDay
) {
}
