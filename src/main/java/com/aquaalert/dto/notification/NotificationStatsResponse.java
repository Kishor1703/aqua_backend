package com.aquaalert.dto.notification;

public record NotificationStatsResponse(
        long totalSent,
        long totalFailed,
        long totalPending,
        long totalRetrying,
        double successRatePercent
) {}
