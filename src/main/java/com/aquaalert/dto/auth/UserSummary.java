package com.aquaalert.dto.auth;

import com.aquaalert.entity.Role;

public record UserSummary(
        Long id,
        String fullName,
        String email,
        Role role,
        String ward
) {}
