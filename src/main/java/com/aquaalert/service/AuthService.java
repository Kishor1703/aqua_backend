package com.aquaalert.service;

import com.aquaalert.dto.auth.*;
import com.aquaalert.entity.PasswordResetToken;
import com.aquaalert.entity.User;
import com.aquaalert.exception.InvalidCredentialsException;
import com.aquaalert.exception.ResourceNotFoundException;
import com.aquaalert.repository.PasswordResetTokenRepository;
import com.aquaalert.repository.UserRepository;
import com.aquaalert.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final com.aquaalert.security.AppUserDetailsService userDetailsService;
    private final NotificationDispatchService notificationDispatchService; // sends the reset email/SMS

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails, user.getId(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(userDetails, user.getId());

        UserSummary summary = new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getWard());
        return new LoginResponse(accessToken, refreshToken, summary);
    }

    @Transactional(readOnly = true)
    public LoginResponse refresh(RefreshTokenRequest request) {
        String token = request.refreshToken();
        String tokenType = jwtService.extractTokenType(token);
        if (!"refresh".equals(tokenType)) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }

        String email = jwtService.extractUsername(token);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (!jwtService.isTokenValid(token, userDetails)) {
            throw new InvalidCredentialsException("Refresh token expired or invalid");
        }

        String newAccessToken = jwtService.generateAccessToken(userDetails, user.getId(), user.getRole().name());
        String newRefreshToken = jwtService.generateRefreshToken(userDetails, user.getId());
        UserSummary summary = new UserSummary(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.getWard());
        return new LoginResponse(newAccessToken, newRefreshToken, summary);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Always behave the same way whether or not the email exists —
        // otherwise this endpoint becomes a user-enumeration oracle.
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(user -> {
            String rawToken = generateSecureToken();
            String tokenHash = sha256(rawToken);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .expiresAt(Instant.now().plusSeconds(30 * 60)) // 30 minutes
                    .build();
            resetTokenRepository.save(resetToken);

            notificationDispatchService.sendPasswordResetLink(user, rawToken);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = sha256(request.token());
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired reset link"));

        if (!resetToken.isValid()) {
            throw new ResourceNotFoundException("Invalid or expired reset link");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        resetTokenRepository.save(resetToken);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Hashing algorithm unavailable", e);
        }
    }
}
