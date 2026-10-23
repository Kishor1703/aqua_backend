package com.aquaalert.service;

import com.aquaalert.dto.report.ReportPeriod;
import com.aquaalert.dto.report.ReportPointResponse;
import com.aquaalert.dto.report.ReportResponse;
import com.aquaalert.dto.report.ReportSummaryResponse;
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final WaterScheduleRepository scheduleRepository;
    private final ResidentRepository residentRepository;
    private final AreaRepository areaRepository;
    private final NotificationRepository notificationRepository;
    private final UserAccessService userAccessService;

    @Transactional(readOnly = true)
    public ReportResponse build(ReportPeriod period) {
        ReportWindow window = window(period);
        String ward = wardScope();

        List<com.aquaalert.entity.WaterSchedule> schedules = ward == null
                ? scheduleRepository.findAllByReleaseDateBetweenOrderByReleaseDateAscReleaseTimeAsc(window.fromDate, window.toDate)
                : scheduleRepository.findAllByAreaWardIgnoreCaseAndReleaseDateBetweenOrderByReleaseDateAscReleaseTimeAsc(ward, window.fromDate, window.toDate);

        List<Notification> notifications = notificationRepository.findAllInRange(window.fromInstant, window.toInstant, ward);

        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        for (LocalDate day = window.fromDate; !day.isAfter(window.toDate); day = day.plusDays(1)) {
            byDay.put(day, new long[7]); // scheduled, released, delayed, cancelled, sent, failed, total
        }

        for (var schedule : schedules) {
            long[] row = byDay.computeIfAbsent(schedule.getReleaseDate(), d -> new long[7]);
            row[0]++;
            switch (schedule.getStatus()) {
                case RELEASED -> row[1]++;
                case DELAYED -> row[2]++;
                case CANCELLED -> row[3]++;
                default -> { }
            }
        }

        for (Notification notification : notifications) {
            LocalDate day = notification.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            long[] row = byDay.computeIfAbsent(day, d -> new long[7]);
            if (notification.getStatus() == NotificationStatus.SENT) {
                row[4]++;
            } else if (notification.getStatus() == NotificationStatus.FAILED) {
                row[5]++;
            }
        }

        List<ReportPointResponse> breakdown = new ArrayList<>();
        long totalScheduled = 0, released = 0, delayed = 0, cancelled = 0, sent = 0, failed = 0;
        for (Map.Entry<LocalDate, long[]> entry : byDay.entrySet()) {
            long[] row = entry.getValue();
            totalScheduled += row[0];
            released += row[1];
            delayed += row[2];
            cancelled += row[3];
            sent += row[4];
            failed += row[5];
            breakdown.add(new ReportPointResponse(entry.getKey(), row[0], row[1], row[2], row[3], row[4], row[5]));
        }

        long activeAreas = ward == null ? areaRepository.countByActiveTrue() : areaRepository.countByActiveTrueAndWardIgnoreCase(ward);
        long activeResidents = ward == null
                ? residentRepository.countByStatus(ResidentStatus.ACTIVE)
                : residentRepository.countByStatusAndAreaWardIgnoreCase(ResidentStatus.ACTIVE, ward);
        long attempted = sent + failed;
        double successRate = attempted == 0 ? 0.0 : Math.round((sent * 1000.0) / attempted) / 10.0;

        ReportSummaryResponse summary = new ReportSummaryResponse(
                period, window.fromDate, window.toDate,
                totalScheduled, released, delayed, cancelled,
                activeAreas, activeResidents, sent, failed, successRate
        );

        return new ReportResponse(summary, breakdown);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(ReportPeriod period) {
        ReportResponse report = build(period);
        StringBuilder csv = new StringBuilder();
        csv.append("metric,value\n");
        csv.append("period,").append(report.summary().period()).append('\n');
        csv.append("fromDate,").append(report.summary().fromDate()).append('\n');
        csv.append("toDate,").append(report.summary().toDate()).append('\n');
        csv.append("totalSchedules,").append(report.summary().totalSchedules()).append('\n');
        csv.append("releasedCount,").append(report.summary().releasedCount()).append('\n');
        csv.append("delayedCount,").append(report.summary().delayedCount()).append('\n');
        csv.append("cancelledCount,").append(report.summary().cancelledCount()).append('\n');
        csv.append("activeAreas,").append(report.summary().activeAreas()).append('\n');
        csv.append("activeResidents,").append(report.summary().activeResidents()).append('\n');
        csv.append("notificationsSent,").append(report.summary().notificationsSent()).append('\n');
        csv.append("notificationsFailed,").append(report.summary().notificationsFailed()).append('\n');
        csv.append("successRatePercent,").append(report.summary().successRatePercent()).append('\n');
        csv.append('\n').append("date,scheduled,released,delayed,cancelled,notificationsSent,notificationsFailed\n");
        for (ReportPointResponse point : report.breakdown()) {
            csv.append(point.date()).append(',')
                    .append(point.scheduledCount()).append(',')
                    .append(point.releasedCount()).append(',')
                    .append(point.delayedCount()).append(',')
                    .append(point.cancelledCount()).append(',')
                    .append(point.notificationsSent()).append(',')
                    .append(point.notificationsFailed()).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] exportPdf(ReportPeriod period) {
        ReportResponse report = build(period);
        List<String> lines = new ArrayList<>();
        lines.add("AquaAlert Report");
        lines.add("Period: " + report.summary().period());
        lines.add("From: " + report.summary().fromDate() + " To: " + report.summary().toDate());
        lines.add("Schedules: " + report.summary().totalSchedules());
        lines.add("Released: " + report.summary().releasedCount() + " Delayed: " + report.summary().delayedCount() + " Cancelled: " + report.summary().cancelledCount());
        lines.add("Areas: " + report.summary().activeAreas() + " Residents: " + report.summary().activeResidents());
        lines.add("Sent: " + report.summary().notificationsSent() + " Failed: " + report.summary().notificationsFailed() + " Success: " + report.summary().successRatePercent() + "%");
        lines.add("Breakdown:");
        for (ReportPointResponse point : report.breakdown()) {
            lines.add(point.date() + " | S:" + point.scheduledCount() + " R:" + point.releasedCount() + " D:" + point.delayedCount()
                    + " C:" + point.cancelledCount() + " N+" + point.notificationsSent() + " N-" + point.notificationsFailed());
        }
        return buildSimplePdf(lines);
    }

    private byte[] buildSimplePdf(List<String> lines) {
        List<String> safeLines = lines.stream().map(this::escapePdfText).toList();
        StringBuilder content = new StringBuilder();
        content.append("BT\n/F1 16 Tf\n50 760 Td\n(").append(safeLines.get(0)).append(") Tj\n");
        content.append("/F1 11 Tf\n0 -24 Td\n");
        for (int i = 1; i < safeLines.size(); i++) {
            content.append("(").append(safeLines.get(i)).append(") Tj\n0 -16 Td\n");
        }
        content.append("ET");

        byte[] contentBytes = content.toString().getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            List<Integer> offsets = new ArrayList<>();
            write(out, "%PDF-1.4\n");
            offsets.add(out.size()); write(out, "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n");
            offsets.add(out.size()); write(out, "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n");
            offsets.add(out.size()); write(out, "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n");
            offsets.add(out.size()); write(out, "4 0 obj << /Length " + contentBytes.length + " >> stream\n");
            out.write(contentBytes);
            write(out, "\nendstream endobj\n");
            offsets.add(out.size()); write(out, "5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n");

            int xrefStart = out.size();
            write(out, "xref\n0 6\n0000000000 65535 f \n");
            for (int offset : offsets) {
                write(out, String.format(Locale.ROOT, "%010d 00000 n \n", offset));
            }
            write(out, "trailer << /Size 6 /Root 1 0 R >>\nstartxref\n" + xrefStart + "\n%%EOF");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate PDF report", e);
        }
        return out.toByteArray();
    }

    private void write(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }

    private String escapePdfText(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private String wardScope() {
        return userAccessService.isAdmin(userAccessService.currentUser()) ? null : userAccessService.currentWard();
    }

    private ReportWindow window(ReportPeriod period) {
        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = switch (period) {
            case DAILY -> toDate;
            case WEEKLY -> toDate.minusDays(6);
            case MONTHLY -> toDate.withDayOfMonth(1);
        };
        ZoneId zone = ZoneId.systemDefault();
        return new ReportWindow(fromDate, toDate, fromDate.atStartOfDay(zone).toInstant(), toDate.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1));
    }

    private record ReportWindow(LocalDate fromDate, LocalDate toDate, Instant fromInstant, Instant toInstant) {}
}
