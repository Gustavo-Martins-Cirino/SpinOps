package com.gustavocirino.myday_productivity.dto;

import java.time.LocalDateTime;

/**
 * DTO para respostas do chat com IA.
 * 
 * @param response  Resposta da IA
 * @param timestamp Momento da resposta
 */
public record AIChatResponseDTO(
        String response,
        LocalDateTime timestamp) {
}
