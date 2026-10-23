package com.aquaalert.controller;

import com.aquaalert.dto.common.PagedResponse;
import com.aquaalert.dto.notification.NotificationResponse;
import com.aquaalert.dto.notification.NotificationStatsResponse;
import com.aquaalert.entity.NotificationChannel;
import com.aquaalert.entity.NotificationStatus;
import com.aquaalert.notification.SendResult;
import com.aquaalert.service.NotificationDispatchService;
import com.aquaalert.service.NotificationQueryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationQueryService queryService;
    private final NotificationDispatchService dispatchService;

    @PostMapping("/test-email")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SendResult> testEmail(
            @RequestBody(required = false) String body,
            @RequestParam Map<String, String> parameters) {
        if ((body != null && !body.isBlank()) || !parameters.isEmpty()) {
            throw new IllegalArgumentException("Test email accepts no recipient, message, or other parameters");
        }
        var result = dispatchService.sendTestEmail();
        return ResponseEntity.status(result.success() ? 200 : 503).body(result);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<NotificationResponse>> search(
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(required = false) Long residentId,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationChannel channel,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(queryService.search(scheduleId, residentId, status, channel, pageable));
    }

    @GetMapping("/stats")
    public ResponseEntity<NotificationStatsResponse> stats() {
        return ResponseEntity.ok(queryService.stats());
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<NotificationResponse> retry(@PathVariable Long id) {
        queryService.getById(id);
        dispatchService.retry(id);
        return ResponseEntity.ok(queryService.getById(id));
    }

    @PostMapping("/retry-failed")
    public ResponseEntity<Void> retryAllFailed() {
        dispatchService.retryAllEligibleFailed();
        return ResponseEntity.accepted().build();
    }
}
