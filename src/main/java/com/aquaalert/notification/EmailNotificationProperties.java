package com.aquaalert.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.notifications.email")
@Getter
@Setter
public class EmailNotificationProperties {
    private boolean enabled;
    private String testRecipient = "";
    private String from = "";
    private String senderName = "AquaAlert";
}
