package com.aquaalert.dto.dashboard;

public record DashboardSummaryResponse(
        long todayScheduledCount,
        long upcomingCount,
        long delayedCount,
        long cancelledCount,
        long totalActiveResidents,
        long totalActiveAreas,
        long notificationsSentTotal,
        long notificationsFailedTotal,
        double successRatePercent
) {}
