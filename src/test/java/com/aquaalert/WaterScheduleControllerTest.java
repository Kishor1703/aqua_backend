package com.aquaalert;

import com.aquaalert.entity.Area;
import com.aquaalert.entity.Role;
import com.aquaalert.entity.User;
import com.aquaalert.repository.AreaRepository;
import com.aquaalert.repository.UserRepository;
import com.aquaalert.repository.WaterScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WaterScheduleControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AreaRepository areaRepository;
    @Autowired WaterScheduleRepository scheduleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    Area area;

    @BeforeEach
    void setUp() {
        scheduleRepository.deleteAll();
        areaRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .fullName("Operator One")
                .email("operator1@aquaalert.test")
                .passwordHash(passwordEncoder.encode("SecurePass123"))
                .role(Role.OPERATOR)
                .active(true)
                .build());

        area = areaRepository.save(Area.builder()
                .name("Gandhi Nagar").ward("Ward 5").zone("North Zone").active(true).build());
    }

    private String futureSchedulePayload() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        return """
                {"areaId":%d,"releaseDate":"%s","releaseTime":"18:00:00","notifyBeforeMins":60}
                """.formatted(area.getId(), tomorrow);
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void create_withValidFutureDate_returns201WithScheduledStatus() throws Exception {
        mockMvc.perform(post("/api/v1/schedules").contentType("application/json").content(futureSchedulePayload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.areaName").value("Gandhi Nagar"));
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void create_withPastDate_returns400() throws Exception {
        String body = """
                {"areaId":%d,"releaseDate":"2020-01-01","releaseTime":"18:00:00","notifyBeforeMins":60}
                """.formatted(area.getId());

        mockMvc.perform(post("/api/v1/schedules").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void statusTransition_scheduledToReleased_succeeds() throws Exception {
        String created = mockMvc.perform(post("/api/v1/schedules").contentType("application/json").content(futureSchedulePayload()))
                .andReturn().getResponse().getContentAsString();
        Long id = extractId(created);

        mockMvc.perform(patch("/api/v1/schedules/{id}/status", id)
                        .contentType("application/json")
                        .content("{\"status\":\"RELEASED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void statusTransition_releasedToScheduled_isRejected() throws Exception {
        String created = mockMvc.perform(post("/api/v1/schedules").contentType("application/json").content(futureSchedulePayload()))
                .andReturn().getResponse().getContentAsString();
        Long id = extractId(created);

        mockMvc.perform(patch("/api/v1/schedules/{id}/status", id)
                        .contentType("application/json")
                        .content("{\"status\":\"RELEASED\"}"))
                .andExpect(status().isOk());

        // RELEASED is terminal — cannot go back to SCHEDULED.
        mockMvc.perform(patch("/api/v1/schedules/{id}/status", id)
                        .contentType("application/json")
                        .content("{\"status\":\"SCHEDULED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void delete_releasedSchedule_isRejected() throws Exception {
        String created = mockMvc.perform(post("/api/v1/schedules").contentType("application/json").content(futureSchedulePayload()))
                .andReturn().getResponse().getContentAsString();
        Long id = extractId(created);

        mockMvc.perform(patch("/api/v1/schedules/{id}/status", id)
                        .contentType("application/json")
                        .content("{\"status\":\"RELEASED\"}"));

        mockMvc.perform(delete("/api/v1/schedules/{id}", id))
                .andExpect(status().isConflict());
    }

    private Long extractId(String json) {
        // Minimal extraction to avoid pulling in a full JSON path dependency here.
        int idx = json.indexOf("\"id\":");
        String rest = json.substring(idx + 5);
        return Long.parseLong(rest.substring(0, rest.indexOf(',')).trim());
    }
}
