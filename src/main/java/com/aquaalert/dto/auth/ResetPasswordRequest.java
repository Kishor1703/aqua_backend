package com.aquaalert.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 100,
                message = "Password must be between 8 and 100 characters")
        String newPassword
) {}
