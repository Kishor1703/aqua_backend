package com.aquaalert.notification;

import com.aquaalert.entity.Resident;

/**
 * Abstraction over the four outbound channels. Swap the bean
 * (LoggingNotificationProviderClient) for a real implementation that
 * calls Twilio/MSG91/Exotel/FCM without touching any calling code —
 * NotificationDispatchService only depends on this interface.
 */
public interface NotificationProviderClient {

    SendResult sendSms(Resident resident, String message);

    SendResult sendVoiceCall(Resident resident, String script);

    SendResult sendPushNotification(Resident resident, String title, String body);

    SendResult sendWhatsApp(Resident resident, String message);
}
