package com.aquaalert.dto.operator;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OperatorCreateRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String ward,
        @Size(max = 20)
        @Pattern(regexp = "^[0-9+\\-() ]*$", message = "Phone number contains invalid characters")
        String phoneNumber
) {}
