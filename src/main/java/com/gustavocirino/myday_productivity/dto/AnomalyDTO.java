package com.gustavocirino.myday_productivity.dto;

import java.util.Map;

/**
 * Representa uma anomalia detectada na análise de produtividade.
 *
 * @param type     Tipo da anomalia: PRODUCTIVITY_DROP | TASK_ACCUMULATION | LATE_SPIKE | IDLE_PERIOD
 * @param severity Gravidade: LOW | MEDIUM | HIGH | CRITICAL
 * @param date     Data ISO (yyyy-MM-dd) de ocorrência da anomalia
 * @param message  Descrição legível para o usuário
 * @param metadata Dados extras para exibição (ex: done, mean, lateRate, etc.)
 */
public record AnomalyDTO(
        String type,
        String severity,
        String date,
        String message,
        Map<String, Object> metadata
) {}
