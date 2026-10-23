package com.aquaalert.dto.resident;

import com.aquaalert.entity.NotificationPreference;
import com.aquaalert.entity.ResidentStatus;

import java.time.Instant;

public record ResidentResponse(
        Long id,
        String residentCode,
        String houseNumber,
        String ownerName,
        String phoneNumber,
        String alternatePhoneNumber,
        String email,
        Long areaId,
        String areaName,
        NotificationPreference notificationPreference,
        ResidentStatus status,
        String preferredLanguage,
        Instant createdAt,
        Instant updatedAt
) {}
