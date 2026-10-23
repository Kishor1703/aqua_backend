package com.aquaalert;

import com.aquaalert.entity.*;
import com.aquaalert.repository.*;
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
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AreaRepository areaRepository;
    @Autowired ResidentRepository residentRepository;
    @Autowired WaterScheduleRepository scheduleRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PasswordEncoder passwordEncoder;

    Notification failedNotification;
    Notification sentNotification;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        scheduleRepository.deleteAll();
        residentRepository.deleteAll();
        areaRepository.deleteAll();
        userRepository.deleteAll();

        User operator = userRepository.save(User.builder()
                .fullName("Operator One").email("operator1@aquaalert.test")
                .passwordHash(passwordEncoder.encode("SecurePass123"))
                .role(Role.OPERATOR).active(true).build());

        Area area = areaRepository.save(Area.builder()
                .name("Gandhi Nagar").ward("Ward 5").zone("North Zone").active(true).build());

        Resident resident = residentRepository.save(Resident.builder()
                .residentCode("RES-TEST01").houseNumber("12A").ownerName("Ramesh Kumar")
                .phoneNumber("9876543210").area(area)
                .notificationPreference(NotificationPreference.SMS)
                .status(ResidentStatus.ACTIVE).build());

        WaterSchedule schedule = scheduleRepository.save(WaterSchedule.builder()
                .area(area).releaseDate(LocalDate.now().plusDays(1)).releaseTime(LocalTime.of(18, 0))
                .notifyBeforeMins(60).status(ScheduleStatus.SCHEDULED).createdBy(operator).build());

        failedNotification = notificationRepository.save(Notification.builder()
                .schedule(schedule).resident(resident).channel(NotificationChannel.SMS)
                .status(NotificationStatus.FAILED).attemptCount(1).errorMessage("Simulated failure").build());

        sentNotification = notificationRepository.save(Notification.builder()
                .schedule(schedule).resident(resident).channel(NotificationChannel.SMS)
                .status(NotificationStatus.SENT).attemptCount(1).build());
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void search_returnsAllNotifications() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void search_filtersByStatus() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"));
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void stats_computesSuccessRate() throws Exception {
        // 1 SENT, 1 FAILED => 50% success rate
        mockMvc.perform(get("/api/v1/notifications/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSent").value(1))
                .andExpect(jsonPath("$.totalFailed").value(1))
                .andExpect(jsonPath("$.successRatePercent").value(50.0));
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void retry_onAlreadySentNotification_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/{id}/retry", sentNotification.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void retry_onFailedNotification_movesToSentViaLoggingProvider() throws Exception {
        // The default LoggingNotificationProviderClient always simulates
        // success, so a retry on a FAILED notification should succeed.
        mockMvc.perform(post("/api/v1/notifications/{id}/retry", failedNotification.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.attemptCount").value(2));
    }
}
