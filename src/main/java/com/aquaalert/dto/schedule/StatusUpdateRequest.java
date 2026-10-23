package com.aquaalert.dto.schedule;

import com.aquaalert.entity.ScheduleStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotNull ScheduleStatus status,
        @Size(max = 500) String notes
) {}
