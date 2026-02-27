package com.gustavocirino.myday_productivity.service.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gustavocirino.myday_productivity.model.PushSubscription;
import com.gustavocirino.myday_productivity.model.Task;
import com.gustavocirino.myday_productivity.model.enums.TaskStatus;
import com.gustavocirino.myday_productivity.repository.TaskRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PushReminderScheduler {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final PushSubscriptionService subscriptionService;
    private final TaskRepository taskRepository;
    private final WebPushService webPushService;
    private final ObjectMapper objectMapper;

    public PushReminderScheduler(PushSubscriptionService subscriptionService,
            TaskRepository taskRepository,
            WebPushService webPushService,
            ObjectMapper objectMapper) {
        this.subscriptionService = subscriptionService;
        this.taskRepository = taskRepository;
        this.webPushService = webPushService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 30_000)
    public void tick() {
        List<PushSubscription> subs = subscriptionService.findAll();
        if (subs.isEmpty())
            return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minusSeconds(45);
        LocalDateTime windowEnd = now.plusSeconds(45);

        for (PushSubscription sub : subs) {
            try {
                sendStartReminders(sub, windowStart, windowEnd);
                sendLeadReminders(sub, now, windowStart, windowEnd);
            } catch (Exception e) {
                // não derrubar o scheduler por uma subscription inválida
            }
        }
    }

    private void sendStartReminders(PushSubscription sub, LocalDateTime windowStart, LocalDateTime windowEnd) {
        List<Task> due = taskRepository.findByStatusAndStartTimeBetween(TaskStatus.SCHEDULED, windowStart, windowEnd);
        for (Task task : due) {
            if (task.getStartTime() == null)
                continue;
            String hhmm = task.getStartTime().format(HHMM);
            send(sub,
                    "⏰ Agora",
                    task.getTitle() + " (" + hhmm + ")");
        }
    }

    private void sendLeadReminders(PushSubscription sub, LocalDateTime now, LocalDateTime windowStart,
            LocalDateTime windowEnd) {
        int lead = sub.getLeadMinutes() == null ? 0 : Math.max(0, Math.min(120, sub.getLeadMinutes()));
        if (lead <= 0)
            return;

        LocalDateTime shiftedStart = windowStart.plusMinutes(lead);
        LocalDateTime shiftedEnd = windowEnd.plusMinutes(lead);

        List<Task> due = taskRepository.findByStatusAndStartTimeBetween(TaskStatus.SCHEDULED, shiftedStart, shiftedEnd);
        for (Task task : due) {
            if (task.getStartTime() == null)
                continue;
            // Garante que é realmente "lead" (start - lead) ~ now
            LocalDateTime leadAt = task.getStartTime().minusMinutes(lead);
            if (leadAt.isBefore(windowStart) || leadAt.isAfter(windowEnd))
                continue;

            String hhmm = task.getStartTime().format(HHMM);
            send(sub,
                    "⏰ Em " + lead + " min",
                    task.getTitle() + " (às " + hhmm + ")");
        }
    }

    private void send(PushSubscription sub, String title, String body) {
        if (sub.getEndpoint() == null || sub.getP256dh() == null || sub.getAuth() == null)
            return;

        try {
            String payload = objectMapper.writeValueAsString(new PushPayload(title, body, "/"));
            webPushService.send(sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
        } catch (Exception e) {
            // se falhar (subscription expirada), apenas ignora
        }
    }

    private record PushPayload(String title, String body, String url) {
    }
}
