package com.gustavocirino.myday_productivity.dto;

import com.gustavocirino.myday_productivity.model.enums.TaskPriority;
import com.gustavocirino.myday_productivity.model.enums.TaskStatus;
import java.time.LocalDateTime;
import com.gustavocirino.myday_productivity.model.enums.RiskLevel;

public record TaskResponseDTO(
        Long id,
        String title,
        String description,
        TaskPriority priority,
        TaskStatus status,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createdAt,
        String color,
        String equipmentName,
        String equipmentType,
        String sensorReadings,
        RiskLevel riskLevel,
        LocalDateTime predictedFailureDate,
        Double failureProbability,
        String aiAnalysisSummary) {
    public TaskResponseDTO(Long id, String title, String description, TaskPriority priority, TaskStatus status, LocalDateTime startTime, LocalDateTime endTime, LocalDateTime createdAt, String color) {
        this(id, title, description, priority, status, startTime, endTime, createdAt, color, null, null, null, RiskLevel.SAFE, null, 0.0, null);
    }
}
