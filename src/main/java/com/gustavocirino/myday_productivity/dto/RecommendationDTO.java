package com.gustavocirino.myday_productivity.dto;

/**
 * Recomendação de otimização operacional gerada por IA.
 *
 * @param action   Ação recomendada (texto objetivo, ≤ 80 chars)
 * @param impact   Impacto esperado: ALTA | MEDIA | BAIXA
 * @param priority Urgência: URGENTE | IMPORTANTE | ROTINA
 * @param category Área afetada: ex. "Gestão de Prazos", "Distribuição de Carga", "Priorização"
 */
public record RecommendationDTO(
        String action,
        String impact,
        String priority,
        String category
) {}
