package com.gustavocirino.myday_productivity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO para requisições de análise de produtividade via IA.
 * 
 * @param analysisType Tipo de análise: "productivity", "patterns",
 *                     "recommendations"
 * @param timeRange    Período: "today", "week", "month"
 */
public record AIAnalysisRequestDTO(
        @NotBlank(message = "Tipo de análise é obrigatório") String analysisType,

        String timeRange) {
}
