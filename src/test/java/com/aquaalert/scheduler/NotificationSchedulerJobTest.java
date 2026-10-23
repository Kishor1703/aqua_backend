package com.aquaalert.scheduler;

import com.aquaalert.entity.*;
import com.aquaalert.repository.AreaRepository;
import com.aquaalert.repository.UserRepository;
import com.aquaalert.repository.WaterScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NotificationSchedulerJobTest {

    @Autowired NotificationSchedulerJob schedulerJob;
    @Autowired WaterScheduleRepository scheduleRepository;
    @Autowired AreaRepository areaRepository;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    Area area;
    User operator;

    @BeforeEach
    void setUp() {
        scheduleRepository.deleteAll();
        areaRepository.deleteAll();
        userRepository.deleteAll();

        operator = userRepository.save(User.builder()
                .fullName("Operator One").email("scheduler-test@aquaalert.test")
                .passwordHash(passwordEncoder.encode("SecurePass123"))
                .role(Role.OPERATOR).active(true).build());

        area = areaRepository.save(Area.builder()
                .name("Gandhi Nagar").ward("Ward 5").zone("North Zone").active(true).build());
    }

    @Test
    void dueSchedule_getsMarkedTriggered() {
        // notifyAt = now (releaseTime is "now", notifyBeforeMins = 0) — already due.
        WaterSchedule due = scheduleRepository.save(WaterSchedule.builder()
                .area(area).releaseDate(LocalDate.now()).releaseTime(LocalTime.now())
                .notifyBeforeMins(0).status(ScheduleStatus.SCHEDULED).createdBy(operator).build());

        schedulerJob.checkAndDispatchDueSchedules();

        WaterSchedule reloaded = scheduleRepository.findById(due.getId()).orElseThrow();
        assertThat(reloaded.getNotificationTriggeredAt()).isNotNull();
    }

    @Test
    void notYetDueSchedule_isNotTriggered() {
        // Release is tomorrow — notifyAt is far in the future.
        WaterSchedule notDue = scheduleRepository.save(WaterSchedule.builder()
                .area(area).releaseDate(LocalDate.now().plusDays(1)).releaseTime(LocalTime.of(18, 0))
                .notifyBeforeMins(60).status(ScheduleStatus.SCHEDULED).createdBy(operator).build());

        schedulerJob.checkAndDispatchDueSchedules();

        WaterSchedule reloaded = scheduleRepository.findById(notDue.getId()).orElseThrow();
        assertThat(reloaded.getNotificationTriggeredAt()).isNull();
    }

    @Test
    void alreadyTriggeredSchedule_isSkipped() {
        WaterSchedule schedule = scheduleRepository.save(WaterSchedule.builder()
                .area(area).releaseDate(LocalDate.now()).releaseTime(LocalTime.now())
                .notifyBeforeMins(0).status(ScheduleStatus.SCHEDULED).createdBy(operator).build());

        schedulerJob.checkAndDispatchDueSchedules();
        var firstTrigger = scheduleRepository.findById(schedule.getId()).orElseThrow().getNotificationTriggeredAt();
        assertThat(firstTrigger).isNotNull();

        // Second pass should not touch it again (repository query excludes
        // schedules with notificationTriggeredAt already set).
        schedulerJob.checkAndDispatchDueSchedules();
        var secondTrigger = scheduleRepository.findById(schedule.getId()).orElseThrow().getNotificationTriggeredAt();
        assertThat(secondTrigger).isEqualTo(firstTrigger);
    }
}
