package com.gustavocirino.myday_productivity.dto;

public record UserProfileDTO(
        Long id,
        String name,
        String email,
        String phone,
        String photoBase64) {
}
