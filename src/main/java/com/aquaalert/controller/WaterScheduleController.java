package com.aquaalert.controller;

import com.aquaalert.dto.common.PagedResponse;
import com.aquaalert.dto.schedule.ScheduleRequest;
import com.aquaalert.dto.schedule.ScheduleResponse;
import com.aquaalert.dto.schedule.StatusUpdateRequest;
import com.aquaalert.entity.ScheduleStatus;
import com.aquaalert.service.WaterScheduleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "Water Schedule Management")
@PreAuthorize("isAuthenticated()")
public class WaterScheduleController {

    private final WaterScheduleService scheduleService;

    @GetMapping
    public ResponseEntity<PagedResponse<ScheduleResponse>> search(
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) ScheduleStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @PageableDefault(size = 20, sort = "releaseDate") Pageable pageable
    ) {
        return ResponseEntity.ok(scheduleService.search(areaId, status, fromDate, toDate, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduleResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ScheduleResponse> create(@Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scheduleService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ScheduleResponse> update(@PathVariable Long id, @Valid @RequestBody ScheduleRequest request) {
        return ResponseEntity.ok(scheduleService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ScheduleResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return ResponseEntity.ok(scheduleService.updateStatus(id, request.status(), request.notes()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
