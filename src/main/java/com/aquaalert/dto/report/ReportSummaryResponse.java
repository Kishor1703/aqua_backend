package com.aquaalert.dto.report;

import java.time.LocalDate;

public record ReportSummaryResponse(
        ReportPeriod period,
        LocalDate fromDate,
        LocalDate toDate,
        long totalSchedules,
        long releasedCount,
        long delayedCount,
        long cancelledCount,
        long activeAreas,
        long activeResidents,
        long notificationsSent,
        long notificationsFailed,
        double successRatePercent
) {}
