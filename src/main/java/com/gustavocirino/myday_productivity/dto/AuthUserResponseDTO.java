package com.gustavocirino.myday_productivity.dto;

public record AuthUserResponseDTO(
        Long id,
        String name,
        String email,
        boolean verified,
        String token) {
}
