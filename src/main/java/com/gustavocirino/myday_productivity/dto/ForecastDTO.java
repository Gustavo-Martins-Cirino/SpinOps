package com.gustavocirino.myday_productivity.dto;

import java.util.List;

/**
 * DTO de resposta para previsão de produtividade (forecasting).
 *
 * @param historical        Série histórica: dias passados com OS concluídas/dia
 * @param forecast          Série projetada: próximos dias com valores estimados
 * @param avgDailyCompletion Média diária de OS concluídas no período histórico
 * @param trend             Tendência calculada: "up", "stable" ou "down"
 * @param horizon           Número de dias à frente projetados
 */
public record ForecastDTO(
        List<DayPoint> historical,
        List<DayPoint> forecast,
        double avgDailyCompletion,
        String trend,
        int horizon,
        /** R² — coeficiente de determinação do modelo (0=ruim, 1=perfeito). */
        double r2,
        /** MAE — erro médio absoluto da regressão (OS/dia). */
        double mae,
        /** Desvio padrão dos resíduos — usado para banda de confiança ±σ na projeção. */
        double stdDev
) {
    /**
     * Ponto de dado diário.
     *
     * @param date         Data no formato ISO (yyyy-MM-dd)
     * @param done         OS concluídas neste dia
     * @param created      OS criadas neste dia (apenas histórico)
     * @param isProjection true se este ponto é projetado (forecast)
     */
    public record DayPoint(String date, long done, long created, boolean isProjection) {}
}
