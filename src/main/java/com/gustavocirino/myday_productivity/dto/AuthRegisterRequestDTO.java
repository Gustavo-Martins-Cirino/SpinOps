package com.gustavocirino.myday_productivity.dto;

public record AuthRegisterRequestDTO(
        String name,
        String email,
        String password,
        String phone) {
}
