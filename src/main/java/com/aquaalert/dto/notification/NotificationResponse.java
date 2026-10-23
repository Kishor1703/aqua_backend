package com.aquaalert.dto.notification;

import com.aquaalert.entity.NotificationChannel;
import com.aquaalert.entity.NotificationStatus;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long scheduleId,
        String areaName,
        Long residentId,
        String residentName,
        String residentPhone,
        NotificationChannel channel,
        NotificationStatus status,
        String providerMessageId,
        String errorMessage,
        int attemptCount,
        Instant sentAt,
        Instant createdAt
) {}
