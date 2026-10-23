package com.aquaalert.dto.auth;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        UserSummary user
) {
    public LoginResponse(String accessToken, String refreshToken, UserSummary user) {
        this(accessToken, refreshToken, "Bearer", user);
    }
}
