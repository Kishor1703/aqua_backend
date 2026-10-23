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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AreaRepository areaRepository;
    @Autowired ResidentRepository residentRepository;
    @Autowired WaterScheduleRepository scheduleRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired PasswordEncoder passwordEncoder;

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

        residentRepository.save(Resident.builder()
                .residentCode("RES-TEST01").houseNumber("12A").ownerName("Ramesh Kumar")
                .phoneNumber("9876543210").area(area)
                .notificationPreference(NotificationPreference.SMS)
                .status(ResidentStatus.ACTIVE).build());

        WaterSchedule todaySchedule = scheduleRepository.save(WaterSchedule.builder()
                .area(area).releaseDate(LocalDate.now()).releaseTime(LocalTime.of(18, 0))
                .notifyBeforeMins(60).status(ScheduleStatus.SCHEDULED).createdBy(operator).build());

        WaterSchedule delayedSchedule = scheduleRepository.save(WaterSchedule.builder()
                .area(area).releaseDate(LocalDate.now().plusDays(1)).releaseTime(LocalTime.of(18, 0))
                .notifyBeforeMins(60).status(ScheduleStatus.DELAYED).createdBy(operator).build());

        Resident resident = residentRepository.findAll().get(0);

        notificationRepository.save(Notification.builder()
                .schedule(todaySchedule).resident(resident).channel(NotificationChannel.SMS)
                .status(NotificationStatus.SENT).attemptCount(1).build());
        notificationRepository.save(Notification.builder()
                .schedule(todaySchedule).resident(resident).channel(NotificationChannel.SMS)
                .status(NotificationStatus.FAILED).attemptCount(1).build());
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void summary_reflectsSeededData() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayScheduledCount").value(1))
                .andExpect(jsonPath("$.delayedCount").value(1))
                .andExpect(jsonPath("$.totalActiveResidents").value(1))
                .andExpect(jsonPath("$.notificationsSentTotal").value(1))
                .andExpect(jsonPath("$.notificationsFailedTotal").value(1))
                .andExpect(jsonPath("$.successRatePercent").value(50.0));
    }

    @Test
    void summary_withoutAccessToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void today_returnsTodaysSchedule() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].areaName").value("Gandhi Nagar"));
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void charts_returnsAllThreeSeries() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/charts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyNotifications.length()").value(14))
                .andExpect(jsonPath("$.monthlyNotifications.length()").value(6))
                .andExpect(jsonPath("$.areaWiseNotifications[0].areaName").value("Gandhi Nagar"));
    }
}
