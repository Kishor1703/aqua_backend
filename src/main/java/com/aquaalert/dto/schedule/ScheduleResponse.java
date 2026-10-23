package com.aquaalert.dto.schedule;

import com.aquaalert.entity.ScheduleStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ScheduleResponse(
        Long id,
        Long areaId,
        String areaName,
        String areaWard,
        LocalDate releaseDate,
        LocalTime releaseTime,
        Integer notifyBeforeMins,
        LocalDateTime notifyAt,
        ScheduleStatus status,
        String notes,
        String createdByName,
        Instant createdAt,
        Instant updatedAt
) {}
