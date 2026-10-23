package com.aquaalert;

import com.aquaalert.entity.Role;
import com.aquaalert.entity.User;
import com.aquaalert.repository.AreaRepository;
import com.aquaalert.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AreaControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AreaRepository areaRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        areaRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .fullName("Operator One")
                .email("operator1@aquaalert.test")
                .passwordHash(passwordEncoder.encode("SecurePass123"))
                .role(Role.OPERATOR)
                .active(true)
                .build());
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void create_withValidPayload_returns201() throws Exception {
        String body = """
                {"name":"Gandhi Nagar","ward":"Ward 5","zone":"North Zone","street":"Main Street"}
                """;

        mockMvc.perform(post("/api/v1/areas").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Gandhi Nagar"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void create_duplicateNameAndWard_returns409() throws Exception {
        String body = """
                {"name":"Gandhi Nagar","ward":"Ward 5","zone":"North Zone"}
                """;

        mockMvc.perform(post("/api/v1/areas").contentType("application/json").content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/areas").contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void create_missingRequiredField_returns400() throws Exception {
        String body = """
                {"ward":"Ward 5","zone":"North Zone"}
                """;

        mockMvc.perform(post("/api/v1/areas").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void search_returnsPagedResults() throws Exception {
        for (String name : new String[]{"Gandhi Nagar", "Nehru Colony", "Anna Nagar"}) {
            String body = "{\"name\":\"%s\",\"ward\":\"Ward 1\",\"zone\":\"Zone A\"}".formatted(name);
            mockMvc.perform(post("/api/v1/areas").contentType("application/json").content(body));
        }

        mockMvc.perform(get("/api/v1/areas").param("search", "nagar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void unauthenticated_request_returns401or403() throws Exception {
        mockMvc.perform(get("/api/v1/areas"))
                .andExpect(status().is4xxClientError());
    }
}
