package com.gustavocirino.myday_productivity.service.push;

import com.gustavocirino.myday_productivity.dto.WebPushSubscribeRequestDTO;
import com.gustavocirino.myday_productivity.model.PushSubscription;
import com.gustavocirino.myday_productivity.repository.PushSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;

    public PushSubscriptionService(PushSubscriptionRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void upsert(WebPushSubscribeRequestDTO dto) {
        if (dto == null || dto.subscription() == null) {
            throw new IllegalArgumentException("Subscription inválida");
        }
        if (dto.subscription().endpoint() == null || dto.subscription().endpoint().isBlank()) {
            throw new IllegalArgumentException("Endpoint é obrigatório");
        }
        if (dto.subscription().keys() == null ||
                dto.subscription().keys().p256dh() == null || dto.subscription().keys().p256dh().isBlank() ||
                dto.subscription().keys().auth() == null || dto.subscription().keys().auth().isBlank()) {
            throw new IllegalArgumentException("Keys (p256dh/auth) são obrigatórias");
        }

        int leadMinutes = dto.leadMinutes() == null ? 10 : dto.leadMinutes();
        leadMinutes = Math.max(0, Math.min(120, leadMinutes));

        PushSubscription sub = repository.findByEndpoint(dto.subscription().endpoint())
                .orElseGet(PushSubscription::new);

        sub.setEndpoint(dto.subscription().endpoint());
        sub.setP256dh(dto.subscription().keys().p256dh());
        sub.setAuth(dto.subscription().keys().auth());
        sub.setLeadMinutes(leadMinutes);
        sub.setUserAgent(dto.userAgent());

        repository.save(sub);
    }

    @Transactional
    public void unsubscribe(String endpoint) {
        if (endpoint == null || endpoint.isBlank())
            return;
        repository.deleteByEndpoint(endpoint);
    }

    public List<PushSubscription> findAll() {
        return repository.findAll();
    }
}
