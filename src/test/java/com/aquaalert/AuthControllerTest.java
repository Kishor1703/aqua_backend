package com.aquaalert;

import com.aquaalert.entity.Role;
import com.aquaalert.entity.User;
import com.aquaalert.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        User user = User.builder()
                .fullName("Test Operator")
                .email("operator@aquaalert.test")
                .passwordHash(passwordEncoder.encode("SecurePass123"))
                .role(Role.OPERATOR)
                .active(true)
                .build();
        userRepository.save(user);
    }

    @Test
    void login_withValidCredentials_returnsTokens() throws Exception {
        String body = objectMapper.writeValueAsString(
                new com.aquaalert.dto.auth.LoginRequest("operator@aquaalert.test", "SecurePass123"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("operator@aquaalert.test"));
    }

    @Test
    void login_withWrongPassword_returns401WithGenericMessage() throws Exception {
        String body = objectMapper.writeValueAsString(
                new com.aquaalert.dto.auth.LoginRequest("operator@aquaalert.test", "WrongPassword"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void login_withInvalidEmailFormat_returns400ValidationError() throws Exception {
        String body = objectMapper.writeValueAsString(
                new com.aquaalert.dto.auth.LoginRequest("not-an-email", "SecurePass123"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void forgotPassword_alwaysReturns204_evenForUnknownEmail() throws Exception {
        String body = objectMapper.writeValueAsString(
                new com.aquaalert.dto.auth.ForgotPasswordRequest("unknown@nowhere.test"));

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNoContent());
    }
}
