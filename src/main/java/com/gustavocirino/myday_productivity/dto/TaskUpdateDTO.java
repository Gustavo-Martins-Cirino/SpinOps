package com.gustavocirino.myday_productivity.dto;

import com.gustavocirino.myday_productivity.model.enums.TaskPriority;

import java.time.LocalDateTime;

/**
 * DTO para atualizar parcialmente uma tarefa
 * Permite editar título, descrição, prioridade e horários
 */
public record TaskUpdateDTO(
        String title,
        String description,
        TaskPriority priority,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String color) {
}
