package com.gustavocirino.myday_productivity.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO para respostas de análise de IA.
 * 
 * @param summary         Resumo executivo da análise
 * @param insights        Lista de insights identificados
 * @param recommendations Recomendações acionáveis
 * @param score           Score de produtividade (0-100)
 * @param timestamp       Momento da análise
 */
public record AIAnalysisResponseDTO(
        String summary,
        List<String> insights,
        List<String> recommendations,
        Integer score,
        LocalDateTime timestamp) {
}
