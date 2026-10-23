package com.aquaalert.notification;

import jakarta.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class GmailNotificationProviderClient implements EmailNotificationProviderClient {
    private final JavaMailSender sender;
    private final MailProperties smtp;
    private final EmailNotificationProperties config;
    private final Clock clock;
    private Instant nextAttempt = Instant.MIN;

    @Autowired
    public GmailNotificationProviderClient(JavaMailSender sender, MailProperties smtp,
                                           EmailNotificationProperties config) {
        this(sender, smtp, config, Clock.systemUTC());
    }

    GmailNotificationProviderClient(JavaMailSender sender, MailProperties smtp,
                                    EmailNotificationProperties config, Clock clock) {
        this.sender = sender;
        this.smtp = smtp;
        this.config = config;
        this.clock = clock;
    }

    // Serialize attempts and throttle even failed sends: an SMTP timeout may occur
    // after acceptance. Never automatically retry an ambiguous delivery.
    @Override
    public synchronized SendResult sendTestEmail() {
        if (!config.isEnabled()) {
            return SendResult.failure("Test email is disabled. Set AQUAALERT_MAIL_ENABLED=true.");
        }
        if (blank(smtp.getHost()) || blank(smtp.getUsername()) || blank(smtp.getPassword())) {
            return SendResult.failure("SMTP configuration is missing. Set AQUAALERT_MAIL_USERNAME and AQUAALERT_MAIL_PASSWORD.");
        }
        String from = configuredSender();
        if (!singleAddress(from) || !singleAddress(config.getTestRecipient())) {
            return SendResult.failure("Configure a valid sender and exactly one AQUAALERT_TEST_EMAIL address.");
        }
        if (clock.instant().isBefore(nextAttempt)) {
            return SendResult.failure("Please wait 60 seconds between test email attempts.");
        }
        try {
            nextAttempt = clock.instant().plusSeconds(60);
            sender.send(message(config.getTestRecipient(), "AquaAlert test email",
                    "This is a development test email from AquaAlert.\n"
                            + "Your SMTP configuration is working. This is not a scheduled water-release alert."));
            // SMTP acceptance is not proof of inbox delivery and provides no receipt ID.
            return SendResult.ok(null);
        } catch (MailAuthenticationException ex) {
            return SendResult.failure("SMTP authentication failed. Check the configured Gmail account and app password.");
        } catch (MailException ex) {
            return SendResult.failure("SMTP send could not be confirmed. Check your inbox and SMTP settings before retrying.");
        } catch (java.io.UnsupportedEncodingException ex) {
            return SendResult.failure("Email sender configuration is invalid.");
        }
    }

    @Override
    public SendResult sendPasswordResetLink(String recipient, String resetLink) {
        if (!config.isEnabled()) {
            return SendResult.failure("Email delivery is not configured.");
        }
        if (!smtpConfigured() || !singleAddress(configuredSender()) || !singleAddress(recipient)
                || blank(resetLink) || resetLink.contains("\r") || resetLink.contains("\n")) {
            return SendResult.failure("Email delivery is not configured with a valid address.");
        }
        try {
            sender.send(message(recipient, "Reset your AquaAlert password",
                    "We received a request to reset your AquaAlert password.\n\n"
                            + "Use this link within 30 minutes to choose a new password:\n"
                            + resetLink + "\n\n"
                            + "If you did not request this, you can ignore this email."));
            return SendResult.ok(null);
        } catch (MailAuthenticationException ex) {
            return SendResult.failure("SMTP authentication failed.");
        } catch (MailException ex) {
            return SendResult.failure("SMTP could not confirm delivery.");
        } catch (java.io.UnsupportedEncodingException ex) {
            return SendResult.failure("Email sender configuration is invalid.");
        }
    }

    @Override
    public SendResult sendScheduleAlert(String recipient, String text) {
        if (!config.isEnabled()) {
            return SendResult.failure("Email is disabled. Set AQUAALERT_MAIL_ENABLED=true.");
        }
        if (!smtpConfigured() || !singleAddress(configuredSender())) {
            return SendResult.failure("SMTP configuration is missing or the sender address is invalid.");
        }
        if (!singleAddress(recipient)) {
            return SendResult.failure("Resident email address is missing or invalid. Update the resident before retrying.");
        }
        try {
            sender.send(message(recipient, "AquaAlert water supply alert", text));
            return SendResult.ok(null);
        } catch (MailAuthenticationException ex) {
            return SendResult.failure("SMTP authentication failed. Use the Gmail account and its app password without spaces.");
        } catch (MailException ex) {
            return SendResult.failure("SMTP delivery could not be confirmed. Check the inbox before retrying.");
        } catch (java.io.UnsupportedEncodingException ex) {
            return SendResult.failure("Email sender configuration is invalid.");
        }
    }

    private boolean smtpConfigured() {
        return !blank(smtp.getHost()) && !blank(smtp.getUsername()) && !blank(smtp.getPassword());
    }

    private String configuredSender() {
        return blank(config.getFrom()) ? smtp.getUsername() : config.getFrom();
    }

    private SimpleMailMessage message(String recipient, String subject, String text)
            throws java.io.UnsupportedEncodingException {
        if (blank(config.getSenderName()) || config.getSenderName().contains("\r")
                || config.getSenderName().contains("\n")) {
            throw new java.io.UnsupportedEncodingException("Invalid sender display name");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(new InternetAddress(configuredSender(), config.getSenderName(), "UTF-8").toString());
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(text);
        return message;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean singleAddress(String value) {
        if (blank(value) || value.contains("\r") || value.contains("\n")) return false;
        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            return !address.isGroup() && address.getPersonal() == null
                    && value.equals(address.getAddress()) && value.contains("@");
        } catch (jakarta.mail.internet.AddressException ex) {
            return false;
        }
    }
}
