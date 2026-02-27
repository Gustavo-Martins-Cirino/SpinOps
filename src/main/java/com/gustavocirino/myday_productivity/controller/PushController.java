package com.gustavocirino.myday_productivity.controller;

import com.gustavocirino.myday_productivity.dto.VapidPublicKeyDTO;
import com.gustavocirino.myday_productivity.dto.WebPushSubscribeRequestDTO;
import com.gustavocirino.myday_productivity.dto.WebPushUnsubscribeRequestDTO;
import com.gustavocirino.myday_productivity.service.push.PushSubscriptionService;
import com.gustavocirino.myday_productivity.service.push.VapidKeyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/push")
@CrossOrigin(origins = "*")
@Tag(name = "Push", description = "API de Web Push (PWA) para notificações com aba fechada")
public class PushController {

    private final VapidKeyService vapidKeyService;
    private final PushSubscriptionService subscriptionService;

    public PushController(VapidKeyService vapidKeyService, PushSubscriptionService subscriptionService) {
        this.vapidKeyService = vapidKeyService;
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/vapid-public-key")
    public ResponseEntity<VapidPublicKeyDTO> getVapidPublicKey() {
        return ResponseEntity.ok(new VapidPublicKeyDTO(vapidKeyService.getPublicKeyBase64Url()));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<Void> subscribe(@RequestBody WebPushSubscribeRequestDTO dto) {
        subscriptionService.upsert(dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Void> unsubscribe(@RequestBody WebPushUnsubscribeRequestDTO dto) {
        subscriptionService.unsubscribe(dto == null ? null : dto.endpoint());
        return ResponseEntity.ok().build();
    }
}
