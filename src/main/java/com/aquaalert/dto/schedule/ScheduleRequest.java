package com.aquaalert.dto.schedule;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record ScheduleRequest(
        @NotNull Long areaId,
        @NotNull @FutureOrPresent(message = "Release date cannot be in the past") LocalDate releaseDate,
        @NotNull LocalTime releaseTime,
        @NotNull @Min(5) @Max(1440) Integer notifyBeforeMins,
        @Size(max = 500) String notes
) {}
