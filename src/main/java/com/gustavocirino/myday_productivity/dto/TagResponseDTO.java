package com.gustavocirino.myday_productivity.dto;

/**
 * DTO para resposta de tags.
 * 
 * @param id        ID da tag
 * @param name      Nome da tag
 * @param color     Cor hexadecimal
 * @param taskCount Quantidade de tarefas com essa tag
 */
public record TagResponseDTO(
        Long id,
        String name,
        String color,
        Integer taskCount) {
}
