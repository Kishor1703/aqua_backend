package com.aquaalert.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

public record DashboardChartsResponse(
        List<DailyPoint> dailyNotifications,
        List<AreaPoint> areaWiseNotifications,
        List<MonthlyPoint> monthlyNotifications
) {
    public record DailyPoint(LocalDate date, long sent, long failed) {}
    public record AreaPoint(String areaName, long sent, long failed) {}
    public record MonthlyPoint(String monthLabel, long sent, long failed) {}
}
