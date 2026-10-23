package com.aquaalert.dto.area;

import java.time.Instant;

public record AreaResponse(
        Long id,
        String name,
        String ward,
        String zone,
        String street,
        boolean active,
        long residentCount,
        Instant createdAt,
        Instant updatedAt
) {}
