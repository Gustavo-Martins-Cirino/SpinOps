package com.gustavocirino.myday_productivity.repository;

import com.gustavocirino.myday_productivity.model.Task;

import com.gustavocirino.myday_productivity.model.enums.TaskStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByUserId(Long userId);

    List<Task> findByUserIsNull();

    List<Task> findByStatusAndStartTimeBetween(TaskStatus status, LocalDateTime start, LocalDateTime end);

    /**
     * Conta tarefas por status em um intervalo de tempo (startTime between start
     * and end)
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.status = :status AND t.startTime BETWEEN :start AND :end")
    long countByStatusAndStartTimeBetween(@Param("status") TaskStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Conta tarefas criadas em um intervalo de tempo
     */
    @Query("SELECT COUNT(t) FROM Task t WHERE t.createdAt BETWEEN :start AND :end")
    long countByCreatedAtBetween(@Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Busca todas as tarefas com startTime em um intervalo
     */
    List<Task> findAllByStartTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Helper: conta tarefas SCHEDULED em um período
     */
    default long countByStatusScheduledAndStartTimeBetween(LocalDateTime start, LocalDateTime end) {
        return countByStatusAndStartTimeBetween(TaskStatus.SCHEDULED, start, end);
    }

    /**
     * Helper: conta tarefas DONE em um período
     */
    default long countByStatusDoneAndStartTimeBetween(LocalDateTime start, LocalDateTime end) {
        return countByStatusAndStartTimeBetween(TaskStatus.DONE, start, end);
    }

    /**
     * Verifica se já existe ao menos uma tarefa com determinado status em um
     * intervalo de início (startTime BETWEEN start AND end).
     *
     * Útil para checagens rápidas de conflito em uma janela (ex: start até
     * start + 1 hora).
     */
    boolean existsByStatusAndStartTimeBetween(TaskStatus status, LocalDateTime start, LocalDateTime end);

}