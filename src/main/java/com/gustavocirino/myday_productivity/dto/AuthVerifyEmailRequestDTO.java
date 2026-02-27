package com.gustavocirino.myday_productivity.dto;

public record AuthVerifyEmailRequestDTO(
        String email,
        String code) {
}
