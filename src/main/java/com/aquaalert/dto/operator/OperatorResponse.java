package com.aquaalert.dto.operator;

import java.time.Instant;

public record OperatorResponse(
        Long id,
        String fullName,
        String email,
        String ward,
        String phoneNumber,
        boolean active,
        Instant createdAt
) {}
