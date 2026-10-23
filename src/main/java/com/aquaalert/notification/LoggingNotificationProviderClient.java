package com.aquaalert.notification;

import com.aquaalert.entity.Resident;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Default provider implementation: logs the outbound message instead of
 * calling a real carrier/API and returns a simulated success. This keeps
 * the whole notification pipeline (scheduler → dispatch → status tracking
 * → retry) runnable and testable without live credentials or outbound
 * network access to third-party domains.
 *
 * To go live, implement {@link NotificationProviderClient} against the
 * real SDKs and register it in place of this bean (e.g. behind a
 * `@Profile("prod")` / `@ConditionalOnProperty` switch driven by
 * app.notifications.*.provider in application.yml):
 *
 *   - SMS:      MSG91 or Twilio Programmable Messaging REST API
 *   - Voice:    Twilio Voice (TwiML) or Exotel Voice API
 *   - Push:     Firebase Cloud Messaging (FCM) Admin SDK — uses
 *               resident.getFcmToken()
 *   - WhatsApp: WhatsApp Business Cloud API (Meta) or Twilio WhatsApp
 *
 * Each real implementation should map provider-specific error codes to
 * a short, human-readable `errorMessage` so the operator's failed-
 * notification list stays useful without needing to check provider logs.
 */
@Service
@Slf4j
public class LoggingNotificationProviderClient implements NotificationProviderClient {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public SendResult sendSms(Resident resident, String message) {
        if (resident.getPhoneNumber() == null || resident.getPhoneNumber().isBlank()) {
            return SendResult.failure("Resident has no phone number on file");
        }
        log.info("Simulated SMS notification accepted");
        return simulatedSuccess("sms");
    }

    @Override
    public SendResult sendVoiceCall(Resident resident, String script) {
        if (resident.getPhoneNumber() == null || resident.getPhoneNumber().isBlank()) {
            return SendResult.failure("Resident has no phone number on file");
        }
        log.info("Simulated voice notification accepted");
        return simulatedSuccess("voice");
    }

    @Override
    public SendResult sendPushNotification(Resident resident, String title, String body) {
        if (resident.getFcmToken() == null || resident.getFcmToken().isBlank()) {
            return SendResult.failure("Resident has no registered device (no FCM token)");
        }
        log.info("Simulated push notification accepted");
        return simulatedSuccess("push");
    }

    @Override
    public SendResult sendWhatsApp(Resident resident, String message) {
        if (resident.getPhoneNumber() == null || resident.getPhoneNumber().isBlank()) {
            return SendResult.failure("Resident has no phone number on file");
        }
        log.info("Simulated WhatsApp notification accepted");
        return simulatedSuccess("whatsapp");
    }

    private SendResult simulatedSuccess(String channelPrefix) {
        String fakeId = channelPrefix + "_" + Long.toHexString(RANDOM.nextLong());
        return SendResult.ok(fakeId);
    }
}
