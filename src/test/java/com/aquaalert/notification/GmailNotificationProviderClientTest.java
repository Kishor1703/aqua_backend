package com.aquaalert.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GmailNotificationProviderClientTest {
    JavaMailSender sender;
    MailProperties smtp;
    EmailNotificationProperties config;
    GmailNotificationProviderClient provider;

    @BeforeEach
    void setUp() {
        sender = mock(JavaMailSender.class);
        smtp = new MailProperties();
        smtp.setHost("smtp.gmail.com");
        smtp.setUsername("sender@example.test");
        smtp.setPassword("fake-test-password");
        config = new EmailNotificationProperties();
        config.setEnabled(true);
        config.setTestRecipient("test@example.test");
        provider = new GmailNotificationProviderClient(sender, smtp, config,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void sendsOnlyFixedContentToConfiguredRecipient() {
        assertThat(provider.sendTestEmail().success()).isTrue();
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("test@example.test");
        assertThat(message.getValue().getCc()).isNull();
        assertThat(message.getValue().getBcc()).isNull();
        assertThat(message.getValue().getFrom()).isEqualTo("AquaAlert <sender@example.test>");
        assertThat(message.getValue().getSubject()).isEqualTo("AquaAlert test email");
        assertThat(message.getValue().getText()).contains("not a scheduled water-release alert");
    }

    @Test
    void scheduleAlertUsesResidentAddressAndHasNoTestCooldown() {
        assertThat(provider.sendScheduleAlert("resident@example.test", "Schedule details").success()).isTrue();
        assertThat(provider.sendScheduleAlert("other@example.test", "Schedule details").success()).isTrue();
        var messages = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(2)).send(messages.capture());
        assertThat(messages.getAllValues().getFirst().getTo()).containsExactly("resident@example.test");
        assertThat(messages.getAllValues().getFirst().getText()).isEqualTo("Schedule details");
        assertThat(messages.getAllValues().getLast().getTo()).containsExactly("other@example.test");
    }

    @Test
    void scheduleAlertRejectsMissingEmailAndReportsAuthFailure() {
        assertThat(provider.sendScheduleAlert(null, "details").errorMessage()).contains("Resident email");
        verifyNoInteractions(sender);
        doThrow(new MailAuthenticationException("secret")).when(sender).send(any(SimpleMailMessage.class));
        assertThat(provider.sendScheduleAlert("resident@example.test", "details").errorMessage())
                .contains("authentication failed").doesNotContain("secret");
    }

    @Test
    void disabledByDefault() {
        config.setEnabled(false);
        assertThat(provider.sendTestEmail().success()).isFalse();
        verifyNoInteractions(sender);
    }

    @Test
    void missingCredentialsCannotSend() {
        smtp.setPassword("");
        assertThat(provider.sendTestEmail().errorMessage()).contains("SMTP configuration is missing");
        smtp.setPassword("fake");
        smtp.setUsername("");
        assertThat(provider.sendTestEmail().success()).isFalse();
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "invalid", "a@example.test,b@example.test", "Team <a@example.test>",
            "group:a@example.test;", "a@example.test\r\nBcc: b@example.test"})
    void rejectsMissingOrMultipleOrInjectedRecipients(String recipient) {
        config.setTestRecipient(recipient);
        assertThat(provider.sendTestEmail().success()).isFalse();
        verifyNoInteractions(sender);
    }

    @Test
    void invalidSenderCannotSend() {
        config.setFrom("a@example.test,b@example.test");
        assertThat(provider.sendTestEmail().success()).isFalse();
        verifyNoInteractions(sender);
    }

    @Test
    void failureDoesNotLeakProviderDetailsOrAutomaticallyRetry() {
        doThrow(new MailSendException("secret recipient and password")).when(sender).send(any(SimpleMailMessage.class));
        var result = provider.sendTestEmail();
        assertThat(result.success()).isFalse();
        assertThat(result.providerMessageId()).isNull();
        assertThat(result.errorMessage()).doesNotContain("secret recipient and password");
        assertThat(provider.sendTestEmail().errorMessage()).contains("60 seconds");
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void authenticationFailureIsSanitized() {
        doThrow(new MailAuthenticationException("secret")).when(sender).send(any(SimpleMailMessage.class));
        assertThat(provider.sendTestEmail().errorMessage()).contains("authentication failed").doesNotContain("secret");
    }

    @Test
    void cooldownAllowsAnExplicitLaterAttempt() {
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        provider = new GmailNotificationProviderClient(sender, smtp, config, clock);
        assertThat(provider.sendTestEmail().success()).isTrue();
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        assertThat(provider.sendTestEmail().success()).isTrue();
        verify(sender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void concurrentRequestsSendAtMostOnceDuringCooldown() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(provider::sendTestEmail);
            var second = executor.submit(provider::sendTestEmail);
            assertThat(java.util.List.of(first.get().success(), second.get().success())).containsExactlyInAnyOrder(true, false);
        }
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
    }
}
