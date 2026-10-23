package com.aquaalert.dto.publicapi;

import com.aquaalert.entity.ScheduleStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record PublicScheduleResponse(
        String areaName,
        String ward,
        String zone,
        LocalDate releaseDate,
        LocalTime releaseTime,
        Integer notifyBeforeMins,
        ScheduleStatus status,
        String notes
) {}
