package com.aquaalert.dto.report;

import java.time.LocalDate;

public record ReportPointResponse(
        LocalDate date,
        long scheduledCount,
        long releasedCount,
        long delayedCount,
        long cancelledCount,
        long notificationsSent,
        long notificationsFailed
) {}
