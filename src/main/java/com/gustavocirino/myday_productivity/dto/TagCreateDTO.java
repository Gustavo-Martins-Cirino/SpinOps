package com.gustavocirino.myday_productivity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para criação de tags.
 * 
 * @param name  Nome da tag (único)
 * @param color Cor hexadecimal (#RRGGBB)
 */
public record TagCreateDTO(
        @NotBlank(message = "Nome da tag é obrigatório") @Size(max = 50, message = "Nome deve ter no máximo 50 caracteres") String name,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato #RRGGBB") String color) {
}
