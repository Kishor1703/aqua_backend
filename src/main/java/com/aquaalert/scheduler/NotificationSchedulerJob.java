package com.aquaalert.scheduler;

import com.aquaalert.entity.ScheduleStatus;
import com.aquaalert.entity.WaterSchedule;
import com.aquaalert.repository.WaterScheduleRepository;
import com.aquaalert.service.NotificationDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The "Automatic Notifications" engine from the spec: an operator sets a
 * release time and a notify-before window once, and this job takes it
 * from there — no second button press required.
 *
 * Polls every {@code app.scheduler.notification-poll-interval-ms}
 * (default 60s). For each SCHEDULED, not-yet-triggered water schedule
 * whose notify-at moment (releaseDateTime - notifyBeforeMins) has
 * arrived, it fans out notifications to residents and immediately marks
 * the schedule as triggered — the trigger flag is set in the same
 * transaction as the read, before the (async) dispatch even starts, so
 * a slow poll cycle can never fire the same schedule twice.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSchedulerJob {

    private final WaterScheduleRepository scheduleRepository;
    private final NotificationDispatchService dispatchService;

    @Scheduled(fixedDelayString = "${app.scheduler.notification-poll-interval-ms:60000}")
    @Transactional
    public void checkAndDispatchDueSchedules() {
        List<WaterSchedule> candidates =
                scheduleRepository.findAllByStatusAndNotificationTriggeredAtIsNull(ScheduleStatus.SCHEDULED);

        if (candidates.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int dispatched = 0;

        for (WaterSchedule schedule : candidates) {
            if (!schedule.getNotifyAt().isAfter(now)) {
                // Mark triggered first, before dispatch fires — the flag,
                // not the dispatch outcome, is what prevents duplicate sends.
                schedule.setNotificationTriggeredAt(Instant.now());
                scheduleRepository.save(schedule);
                dispatchService.dispatchForSchedule(schedule.getId());
                dispatched++;
            }
        }

        if (dispatched > 0) {
            log.info("Notification scheduler: dispatched {} of {} candidate schedules this cycle", dispatched, candidates.size());
        }
    }
}
