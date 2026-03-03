package com.gustavocirino.myday_productivity.dto;

/**
 * Resultado de classificação automática de uma OS por IA.
 *
 * @param taskId     ID da tarefa classificada
 * @param title      Título da OS
 * @param category   Categoria técnica: ELETRICA | MECANICA | HIDRAULICA | INSTRUMENTACAO | CIVIL | OUTRO
 * @param confidence Confiança do modelo (0.0 – 1.0)
 */
public record ClassificationDTO(
        Long taskId,
        String title,
        String category,
        double confidence
) {}
