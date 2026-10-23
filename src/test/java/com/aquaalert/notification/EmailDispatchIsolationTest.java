package com.aquaalert.notification;

import com.aquaalert.entity.*;
import com.aquaalert.repository.*;
import com.aquaalert.scheduler.NotificationSchedulerJob;
import com.aquaalert.service.NotificationDispatchService;
import com.aquaalert.service.UserAccessService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EmailDispatchIsolationTest {
    @Test
    void repeatedPollingSendsOneEmailInsteadOfLegacyChannels() {
        var residents = mock(ResidentRepository.class);
        var notifications = mock(NotificationRepository.class);
        var schedules = mock(WaterScheduleRepository.class);
        var provider = mock(NotificationProviderClient.class);
        var email = mock(EmailNotificationProviderClient.class);
        var access = mock(UserAccessService.class);
        var dispatch = new NotificationDispatchService(residents, notifications, schedules,
                provider, new MessageTemplateService(), access, email);
        var area = Area.builder().id(1L).name("Test area").build();
        var schedule = WaterSchedule.builder().id(1L).area(area).releaseDate(LocalDate.now())
                .releaseTime(LocalTime.now()).notifyBeforeMins(0).status(ScheduleStatus.SCHEDULED).build();
        var resident = Resident.builder().id(1L).email("resident@example.test").notificationPreference(NotificationPreference.ALL).build();
        when(schedules.findAllByStatusAndNotificationTriggeredAtIsNull(ScheduleStatus.SCHEDULED))
                .thenAnswer(call -> schedule.getNotificationTriggeredAt() == null ? List.of(schedule) : List.of());
        when(schedules.findById(1L)).thenReturn(Optional.of(schedule));
        when(residents.findAllByAreaIdAndStatus(1L, ResidentStatus.ACTIVE)).thenReturn(List.of(resident));
        when(notifications.save(any(Notification.class))).thenAnswer(call -> call.getArgument(0));
        when(email.sendScheduleAlert(any(), any())).thenReturn(SendResult.ok(null));

        var scheduler = new NotificationSchedulerJob(schedules, dispatch);
        scheduler.checkAndDispatchDueSchedules();
        scheduler.checkAndDispatchDueSchedules();

        assertThat(schedule.getNotificationTriggeredAt()).isNotNull();
        verify(email).sendScheduleAlert(eq("resident@example.test"), contains("Test area"));
        verifyNoMoreInteractions(email);
        verifyNoInteractions(provider);
        verify(notifications, times(2)).save(argThat(n -> n.getChannel() == NotificationChannel.EMAIL
                && n.getStatus() == NotificationStatus.SENT && n.getAttemptCount() == 1));
    }
}
