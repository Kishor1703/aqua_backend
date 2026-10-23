package com.aquaalert.service;

import com.aquaalert.entity.*;
import com.aquaalert.notification.MessageTemplateService;
import com.aquaalert.notification.EmailNotificationProviderClient;
import com.aquaalert.notification.NotificationProviderClient;
import com.aquaalert.notification.SendResult;
import com.aquaalert.repository.NotificationRepository;
import com.aquaalert.repository.ResidentRepository;
import com.aquaalert.repository.WaterScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private final ResidentRepository residentRepository;
    private final NotificationRepository notificationRepository;
    private final WaterScheduleRepository scheduleRepository;
    private final NotificationProviderClient providerClient;
    private final MessageTemplateService templates;
    private final UserAccessService userAccessService;
    private final EmailNotificationProviderClient emailProvider;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @PreAuthorize("hasRole('ADMIN')")
    public SendResult sendTestEmail() {
        return emailProvider.sendTestEmail();
    }

    private static final int MAX_RETRY_ATTEMPTS = 3;

    // ---------- Password reset (from the Authentication module) ----------

    public void sendPasswordResetLink(User user, String rawToken) {
        String resetLink = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/reset-password")
                .queryParam("token", rawToken)
                .build().encode().toUriString();
        SendResult result = emailProvider.sendPasswordResetLink(user.getEmail(), resetLink);
        if (!result.success()) {
            log.warn("Password reset email could not be sent (reason: {})", result.errorMessage());
        }
    }

    // ---------- Schedule-triggered dispatch (the automatic notification flow) ----------

    /**
     * Fans a due water schedule out to every active resident in its area,
     * one EMAIL notification row per resident. Runs off the scheduler's
     * calling thread via @Async so a slow provider call never blocks the
     * polling job from checking the next schedule.
     *
     * Takes an id rather than the WaterSchedule entity itself: the caller
     * (NotificationSchedulerJob) loads the schedule in its own
     * transaction, which closes before this @Async method's thread picks
     * the call up — passing the detached entity across that boundary and
     * then touching a lazy association (schedule.getArea()) would throw
     * LazyInitializationException. Reloading by id here opens a fresh
     * persistence context instead.
     */
    @Async
    @Transactional
    public void dispatchForSchedule(Long scheduleId) {
        WaterSchedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            log.warn("Schedule {} no longer exists — skipping notification dispatch", scheduleId);
            return;
        }

        List<Resident> residents = residentRepository.findAllByAreaIdAndStatus(
                schedule.getArea().getId(), ResidentStatus.ACTIVE);

        log.info("Dispatching notifications for schedule {} ({}) to {} residents",
                schedule.getId(), schedule.getArea().getName(), residents.size());

        for (Resident resident : residents) {
            Notification notification = Notification.builder()
                    .schedule(schedule)
                    .resident(resident)
                    .channel(NotificationChannel.EMAIL)
                    .status(NotificationStatus.PENDING)
                    .attemptCount(0)
                    .build();
            notification = notificationRepository.save(notification);
            attemptSend(notification);
        }
    }

    // ---------- Sending + retry ----------

    @Transactional
    public void attemptSend(Notification notification) {
        WaterSchedule schedule = notification.getSchedule();
        Resident resident = notification.getResident();

        SendResult result = switch (notification.getChannel()) {
            case EMAIL -> emailProvider.sendScheduleAlert(resident.getEmail(), templates.emailText(schedule));
            case SMS -> providerClient.sendSms(resident, templates.smsText(schedule));
            case VOICE -> providerClient.sendVoiceCall(resident, templates.voiceScript(schedule));
            case PUSH -> providerClient.sendPushNotification(resident, templates.pushTitle(), templates.pushBody(schedule));
            case WHATSAPP -> providerClient.sendWhatsApp(resident, templates.whatsAppText(schedule));
        };

        notification.setAttemptCount(notification.getAttemptCount() + 1);

        if (result.success()) {
            notification.setStatus(NotificationStatus.SENT);
            notification.setProviderMessageId(result.providerMessageId());
            notification.setSentAt(Instant.now());
            notification.setErrorMessage(null);
        } else {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(result.errorMessage());
        }

        notificationRepository.save(notification);
    }

    @Transactional
    public Notification retry(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new com.aquaalert.exception.ResourceNotFoundException(
                        "Notification not found: " + notificationId));
        userAccessService.requireWardAccess(notification.getSchedule().getArea().getWard());

        if (notification.getStatus() != NotificationStatus.FAILED) {
            throw new com.aquaalert.exception.InvalidStateTransitionException(
                    "Only FAILED notifications can be retried (current status: " + notification.getStatus() + ")");
        }
        if (notification.getAttemptCount() >= MAX_RETRY_ATTEMPTS) {
            throw new com.aquaalert.exception.InvalidStateTransitionException(
                    "Notification has already reached the maximum of %d attempts".formatted(MAX_RETRY_ATTEMPTS));
        }

        notification.setStatus(NotificationStatus.RETRYING);
        notificationRepository.save(notification);
        attemptSend(notification);
        return notification;
    }

    /**
     * Bulk retry, used by both the manual "Retry all failed" action and
     * the background retry job. Skips notifications that have already
     * exhausted their attempt budget rather than failing the whole batch.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryAllEligibleFailed() {
        String ward = userAccessService.isAdmin(userAccessService.currentUser()) ? null : userAccessService.currentWard();
        List<Notification> failed = notificationRepository.findAllByStatus(NotificationStatus.FAILED).stream()
                .filter(n -> ward == null || n.getSchedule().getArea().getWard().equalsIgnoreCase(ward))
                .toList();
        int retried = 0;
        for (Notification n : failed) {
            if (n.getAttemptCount() < MAX_RETRY_ATTEMPTS) {
                n.setStatus(NotificationStatus.RETRYING);
                notificationRepository.save(n);
                attemptSend(n);
                retried++;
            }
        }
        log.info("Background retry pass: {} of {} failed notifications retried", retried, failed.size());
    }

}
