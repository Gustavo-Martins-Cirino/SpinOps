package com.gustavocirino.myday_productivity.dto;

import java.time.LocalDateTime;

/**
 * DTO para mover tarefa no calendário (PATCH /api/tasks/{id}/move)
 * Recebe as novas datas de início e fim quando o usuário arrasta no grid
 */
public record TaskMoveDTO(
        LocalDateTime newStartTime,
        LocalDateTime newEndTime) {
}
