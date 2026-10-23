package com.aquaalert.service;

import com.aquaalert.dto.dashboard.DashboardChartsResponse;
import com.aquaalert.dto.dashboard.DashboardSummaryResponse;
import com.aquaalert.dto.schedule.ScheduleResponse;
import com.aquaalert.entity.Notification;
import com.aquaalert.entity.NotificationStatus;
import com.aquaalert.entity.ResidentStatus;
import com.aquaalert.entity.ScheduleStatus;
import com.aquaalert.repository.AreaRepository;
import com.aquaalert.repository.NotificationRepository;
import com.aquaalert.repository.ResidentRepository;
import com.aquaalert.repository.WaterScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final WaterScheduleRepository scheduleRepository;
    private final WaterScheduleService scheduleService;
    private final ResidentRepository residentRepository;
    private final AreaRepository areaRepository;
    private final NotificationRepository notificationRepository;
    private final UserAccessService userAccessService;

    private static final int DAILY_CHART_DAYS = 14;
    private static final int MONTHLY_CHART_MONTHS = 6;
    private static final int UPCOMING_LIMIT = 10;

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        String ward = wardScope();
        LocalDate today = LocalDate.now();

        long todayScheduled = ward == null
                ? scheduleRepository.findAllByReleaseDateOrderByReleaseTimeAsc(today).size()
                : scheduleRepository.findAllByAreaWardIgnoreCaseAndReleaseDateOrderByReleaseTimeAsc(ward, today).size();
        long upcoming = ward == null
                ? scheduleRepository.countByStatus(ScheduleStatus.SCHEDULED)
                : scheduleRepository.countByStatusAndAreaWardIgnoreCase(ScheduleStatus.SCHEDULED, ward);
        long delayed = ward == null
                ? scheduleRepository.countByStatus(ScheduleStatus.DELAYED)
                : scheduleRepository.countByStatusAndAreaWardIgnoreCase(ScheduleStatus.DELAYED, ward);
        long cancelled = ward == null
                ? scheduleRepository.countByStatus(ScheduleStatus.CANCELLED)
                : scheduleRepository.countByStatusAndAreaWardIgnoreCase(ScheduleStatus.CANCELLED, ward);

        long totalResidents = ward == null
                ? residentRepository.countByStatus(ResidentStatus.ACTIVE)
                : residentRepository.countByStatusAndAreaWardIgnoreCase(ResidentStatus.ACTIVE, ward);
        long totalAreas = ward == null
                ? areaRepository.countByActiveTrue()
                : areaRepository.countByActiveTrueAndWardIgnoreCase(ward);

        long sent = ward == null
                ? notificationRepository.countByStatus(NotificationStatus.SENT)
                : notificationRepository.countByStatusAndScheduleAreaWardIgnoreCase(NotificationStatus.SENT, ward);
        long failed = ward == null
                ? notificationRepository.countByStatus(NotificationStatus.FAILED)
                : notificationRepository.countByStatusAndScheduleAreaWardIgnoreCase(NotificationStatus.FAILED, ward);
        long attempted = sent + failed;
        double successRate = attempted == 0 ? 0.0 : Math.round((sent * 1000.0) / attempted) / 10.0;

        return new DashboardSummaryResponse(
                todayScheduled, upcoming, delayed, cancelled,
                totalResidents, totalAreas, sent, failed, successRate
        );
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> todaysReleases() {
        return scheduleService.listForDate(LocalDate.now());
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> upcomingReleases() {
        return scheduleService.listUpcoming(UPCOMING_LIMIT);
    }

    @Transactional(readOnly = true)
    public DashboardChartsResponse charts() {
        ZoneId zone = ZoneId.systemDefault();
        Instant monthsAgo = LocalDate.now().minusMonths(MONTHLY_CHART_MONTHS).atStartOfDay(zone).toInstant();
        String ward = wardScope();

        List<Notification> recent = notificationRepository.findAllForChartsSince(monthsAgo, ward);

        return new DashboardChartsResponse(
                buildDailySeries(recent, zone),
                buildAreaSeries(recent),
                buildMonthlySeries(recent, zone)
        );
    }

    private List<DashboardChartsResponse.DailyPoint> buildDailySeries(List<Notification> notifications, ZoneId zone) {
        LocalDate start = LocalDate.now().minusDays(DAILY_CHART_DAYS - 1L);

        Map<LocalDate, long[]> byDate = new HashMap<>();
        for (Notification n : notifications) {
            LocalDate day = n.getCreatedAt().atZone(zone).toLocalDate();
            if (day.isBefore(start)) continue;
            long[] counts = byDate.computeIfAbsent(day, d -> new long[2]);
            if (n.getStatus() == NotificationStatus.SENT) counts[0]++;
            else if (n.getStatus() == NotificationStatus.FAILED) counts[1]++;
        }

        List<DashboardChartsResponse.DailyPoint> series = new ArrayList<>();
        for (int i = 0; i < DAILY_CHART_DAYS; i++) {
            LocalDate day = start.plusDays(i);
            long[] counts = byDate.getOrDefault(day, new long[2]);
            series.add(new DashboardChartsResponse.DailyPoint(day, counts[0], counts[1]));
        }
        return series;
    }

    private List<DashboardChartsResponse.AreaPoint> buildAreaSeries(List<Notification> notifications) {
        Map<String, long[]> byArea = new LinkedHashMap<>();
        for (Notification n : notifications) {
            String areaName = n.getSchedule().getArea().getName();
            long[] counts = byArea.computeIfAbsent(areaName, a -> new long[2]);
            if (n.getStatus() == NotificationStatus.SENT) counts[0]++;
            else if (n.getStatus() == NotificationStatus.FAILED) counts[1]++;
        }

        return byArea.entrySet().stream()
                .map(e -> new DashboardChartsResponse.AreaPoint(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted((a, b) -> Long.compare(b.sent() + b.failed(), a.sent() + a.failed()))
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<DashboardChartsResponse.MonthlyPoint> buildMonthlySeries(List<Notification> notifications, ZoneId zone) {
        YearMonth start = YearMonth.now().minusMonths(MONTHLY_CHART_MONTHS - 1L);

        Map<YearMonth, long[]> byMonth = new HashMap<>();
        for (Notification n : notifications) {
            YearMonth month = YearMonth.from(n.getCreatedAt().atZone(zone).toLocalDate());
            long[] counts = byMonth.computeIfAbsent(month, m -> new long[2]);
            if (n.getStatus() == NotificationStatus.SENT) counts[0]++;
            else if (n.getStatus() == NotificationStatus.FAILED) counts[1]++;
        }

        List<DashboardChartsResponse.MonthlyPoint> series = new ArrayList<>();
        for (int i = 0; i < MONTHLY_CHART_MONTHS; i++) {
            YearMonth month = start.plusMonths(i);
            long[] counts = byMonth.getOrDefault(month, new long[2]);
            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + month.getYear();
            series.add(new DashboardChartsResponse.MonthlyPoint(label, counts[0], counts[1]));
        }
        return series;
    }

    private String wardScope() {
        return userAccessService.isAdmin(userAccessService.currentUser()) ? null : userAccessService.currentWard();
    }
}
