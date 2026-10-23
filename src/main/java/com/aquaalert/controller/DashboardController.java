package com.aquaalert.controller;

import com.aquaalert.dto.dashboard.DashboardChartsResponse;
import com.aquaalert.dto.dashboard.DashboardSummaryResponse;
import com.aquaalert.dto.schedule.ScheduleResponse;
import com.aquaalert.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard")
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary() {
        return ResponseEntity.ok(dashboardService.summary());
    }

    @GetMapping("/today")
    public ResponseEntity<List<ScheduleResponse>> today() {
        return ResponseEntity.ok(dashboardService.todaysReleases());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<ScheduleResponse>> upcoming() {
        return ResponseEntity.ok(dashboardService.upcomingReleases());
    }

    @GetMapping("/charts")
    public ResponseEntity<DashboardChartsResponse> charts() {
        return ResponseEntity.ok(dashboardService.charts());
    }
}
