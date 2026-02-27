package com.gustavocirino.myday_productivity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO para chat interativo com a IA.
 * 
 * @param message Mensagem do usuário
 */
public record AIChatRequestDTO(
        @NotBlank(message = "Mensagem é obrigatória") String message) {
}
