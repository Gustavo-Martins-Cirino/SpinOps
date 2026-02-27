package com.gustavocirino.myday_productivity.dto;

import java.time.LocalDateTime;

/**
 * DTO para criar uma nova tarefa (POST /api/tasks)
 * Agora inclui horários opcionais para criar tarefas já agendadas
 */
public record TaskCreateDTO(
        String title,
        String description,
        String priority, // "HIGH", "MEDIUM", "LOW"
        LocalDateTime startTime, // Opcional: se fornecido, tarefa é criada como SCHEDULED
        LocalDateTime endTime, // Opcional: se fornecido, tarefa é criada como SCHEDULED
        String color, // Opcional: cor HEX da tarefa
        String equipmentName, // Opcional: nome do equipamento
        String equipmentType, // Opcional: tipo do equipamento
        String sensorReadings // Opcional: leituras dos sensores
) {
}
