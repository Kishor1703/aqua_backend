package com.aquaalert.notification;

/** SMTP delivery for alerts, password resets and administrator tests. */
public interface EmailNotificationProviderClient {
    SendResult sendTestEmail();

    SendResult sendScheduleAlert(String recipient, String text);

    SendResult sendPasswordResetLink(String recipient, String resetLink);
}
