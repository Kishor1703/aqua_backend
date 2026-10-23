package com.aquaalert.dto.resident;

import com.aquaalert.entity.NotificationPreference;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResidentRequest(
        @NotBlank @Size(max = 30) String houseNumber,
        @NotBlank @Size(max = 150) String ownerName,
        @NotBlank @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Enter a valid phone number (10-15 digits, optional +)")
        String phoneNumber,
        @Pattern(regexp = "^$|^[+]?[0-9]{10,15}$", message = "Enter a valid alternate phone number")
        String alternatePhoneNumber,
        @Email(message = "Enter a valid email address") @Size(max = 254) String email,
        @NotNull Long areaId,
        @NotNull NotificationPreference notificationPreference,
        @Size(max = 10) String preferredLanguage
) {
    public ResidentRequest {
        email = email == null || email.isBlank() ? null : email.trim();
    }
}
