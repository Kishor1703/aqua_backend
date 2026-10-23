package com.aquaalert.repository;

import com.aquaalert.entity.Notification;
import com.aquaalert.entity.NotificationChannel;
import com.aquaalert.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
           SELECT n FROM Notification n
           WHERE (:scheduleId IS NULL OR n.schedule.id = :scheduleId)
           AND (:residentId IS NULL OR n.resident.id = :residentId)
           AND (:status IS NULL OR n.status = :status)
           AND (:channel IS NULL OR n.channel = :channel)
           AND (:ward IS NULL OR :ward = '' OR LOWER(n.schedule.area.ward) = LOWER(:ward))
           """)
    Page<Notification> search(@Param("scheduleId") Long scheduleId,
                               @Param("residentId") Long residentId,
                               @Param("status") NotificationStatus status,
                               @Param("channel") NotificationChannel channel,
                               @Param("ward") String ward,
                               Pageable pageable);

    List<Notification> findAllByScheduleId(Long scheduleId);

    // Retry candidates: failed and under the max attempt ceiling
    // (enforced in service, this just narrows by status).
    List<Notification> findAllByStatus(NotificationStatus status);

    long countByScheduleIdAndStatus(Long scheduleId, NotificationStatus status);

    long countByStatus(NotificationStatus status);

    long countByStatusAndScheduleAreaWardIgnoreCase(NotificationStatus status, String ward);

    // Powers the Dashboard charts: fetch-joins schedule+area so the
    // service can group by date/area in Java without N+1 queries. The
    // date range is bounded by the caller (e.g. last 6 months), so this
    // never pulls the full notification history into memory.
    @Query("""
           SELECT n FROM Notification n
           JOIN FETCH n.schedule s
           JOIN FETCH s.area
           WHERE n.createdAt >= :since
           AND (:ward IS NULL OR :ward = '' OR LOWER(s.area.ward) = LOWER(:ward))
           """)
    List<Notification> findAllForChartsSince(@Param("since") java.time.Instant since,
                                             @Param("ward") String ward);

    @Query("""
           SELECT n FROM Notification n
           JOIN FETCH n.schedule s
           JOIN FETCH s.area
           WHERE n.createdAt BETWEEN :from AND :to
           AND (:ward IS NULL OR :ward = '' OR LOWER(s.area.ward) = LOWER(:ward))
           ORDER BY n.createdAt ASC
           """)
    List<Notification> findAllInRange(@Param("from") java.time.Instant from,
                                      @Param("to") java.time.Instant to,
                                      @Param("ward") String ward);
}
