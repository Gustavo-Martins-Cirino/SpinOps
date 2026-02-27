package com.gustavocirino.myday_productivity.dto;

public record WebPushSubscribeRequestDTO(
        WebPushSubscriptionDTO subscription,
        Integer leadMinutes,
        String userAgent) {
}
