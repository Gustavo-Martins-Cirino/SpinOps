package com.gustavocirino.myday_productivity.dto;

public record WebPushSubscriptionDTO(
        String endpoint,
        WebPushKeysDTO keys) {
}
