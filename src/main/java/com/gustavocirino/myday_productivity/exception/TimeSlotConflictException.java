package com.gustavocirino.myday_productivity.exception;

import java.time.LocalDateTime;

/**
 * Lançada quando o horário solicitado conflita com uma tarefa já agendada.
 * Carrega um horário alternativo calculado automaticamente pelo TaskService.
 */
public class TimeSlotConflictException extends RuntimeException {

    private final String conflictingTaskTitle;
    private final LocalDateTime suggestedStart;
    private final LocalDateTime suggestedEnd;

    public TimeSlotConflictException(String conflictingTaskTitle,
                                     LocalDateTime suggestedStart,
                                     LocalDateTime suggestedEnd) {
        super("Conflito de horário com: " + conflictingTaskTitle);
        this.conflictingTaskTitle = conflictingTaskTitle;
        this.suggestedStart = suggestedStart;
        this.suggestedEnd = suggestedEnd;
    }

    public String getConflictingTaskTitle() { return conflictingTaskTitle; }
    public LocalDateTime getSuggestedStart()  { return suggestedStart; }
    public LocalDateTime getSuggestedEnd()    { return suggestedEnd; }
}
