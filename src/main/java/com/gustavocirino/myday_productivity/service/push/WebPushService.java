package com.gustavocirino.myday_productivity.service.push;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.jose4j.lang.JoseException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

@Service
public class WebPushService {

    private final VapidKeyService vapidKeyService;

    public WebPushService(VapidKeyService vapidKeyService) {
        this.vapidKeyService = vapidKeyService;
    }

    public void send(String endpoint, String p256dh, String auth, String payloadJson) {
        try {
            PushService pushService = new PushService();
            pushService.setPublicKey(vapidKeyService.getPublicKey());
            pushService.setPrivateKey(vapidKeyService.getPrivateKey());
            pushService.setSubject("mailto:admin@localhost");

            Notification notification = new Notification(
                    endpoint,
                    p256dh,
                    auth,
                    payloadJson.getBytes(StandardCharsets.UTF_8));

            pushService.send(notification);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao enviar Web Push (interrompido)", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Falha ao enviar Web Push", cause);
        } catch (JoseException | IOException | java.security.GeneralSecurityException e) {
            throw new RuntimeException("Falha ao enviar Web Push", e);
        }
    }
}
