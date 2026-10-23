package com.aquaalert.repository;

import com.aquaalert.entity.ScheduleStatus;
import com.aquaalert.entity.WaterSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface WaterScheduleRepository extends JpaRepository<WaterSchedule, Long> {

    @Query("""
           SELECT s FROM WaterSchedule s
           WHERE (:areaId IS NULL OR s.area.id = :areaId)
           AND (:status IS NULL OR s.status = :status)
           AND (:fromDate IS NULL OR s.releaseDate >= :fromDate)
           AND (:toDate IS NULL OR s.releaseDate <= :toDate)
           AND (:ward IS NULL OR :ward = '' OR LOWER(s.area.ward) = LOWER(:ward))
           """)
    Page<WaterSchedule> search(@Param("areaId") Long areaId,
                                @Param("status") ScheduleStatus status,
                                @Param("fromDate") LocalDate fromDate,
                                @Param("toDate") LocalDate toDate,
                                @Param("ward") String ward,
                                Pageable pageable);

    List<WaterSchedule> findAllByAreaIdAndReleaseDate(Long areaId, LocalDate releaseDate);

    @Query("""
           SELECT s FROM WaterSchedule s
           WHERE LOWER(s.area.ward) = LOWER(:ward)
           AND s.releaseDate = :releaseDate
           ORDER BY s.releaseTime ASC
           """)
    List<WaterSchedule> findAllByAreaWardIgnoreCaseAndReleaseDateOrderByReleaseTimeAsc(@Param("ward") String ward,
                                                                                       @Param("releaseDate") LocalDate releaseDate);

    // ---------- Dashboard ----------

    List<WaterSchedule> findAllByReleaseDateOrderByReleaseTimeAsc(LocalDate releaseDate);

    List<WaterSchedule> findAllByReleaseDateBetweenOrderByReleaseDateAscReleaseTimeAsc(LocalDate fromDate, LocalDate toDate);

    List<WaterSchedule> findAllByAreaWardIgnoreCaseAndReleaseDateBetweenOrderByReleaseDateAscReleaseTimeAsc(
            String ward, LocalDate fromDate, LocalDate toDate);

    long countByStatus(ScheduleStatus status);

    long countByStatusAndAreaWardIgnoreCase(ScheduleStatus status, String ward);

    @Query("""
           SELECT s FROM WaterSchedule s
           WHERE s.status = com.aquaalert.entity.ScheduleStatus.SCHEDULED
           AND s.releaseDate >= :fromDate
           AND (:ward IS NULL OR :ward = '' OR LOWER(s.area.ward) = LOWER(:ward))
           ORDER BY s.releaseDate ASC, s.releaseTime ASC
           """)
    List<WaterSchedule> findUpcoming(@Param("fromDate") LocalDate fromDate,
                                     @Param("ward") String ward,
                                     org.springframework.data.domain.Pageable limit);

    @Query("""
           SELECT s FROM WaterSchedule s
           WHERE s.releaseDate BETWEEN :fromDate AND :toDate
           AND (:ward IS NULL OR :ward = '' OR LOWER(s.area.ward) = LOWER(:ward))
           ORDER BY s.releaseDate ASC, s.releaseTime ASC
           """)
    List<WaterSchedule> findAllInRange(@Param("fromDate") LocalDate fromDate,
                                       @Param("toDate") LocalDate toDate,
                                       @Param("ward") String ward);

    // Candidates for the notification scheduler (Module 5): still
    // SCHEDULED and not yet triggered. The exact notify-at moment
    // (releaseDateTime - notifyBeforeMins) is compared in Java since
    // it's a derived value, not a column — this list is small (only
    // upcoming, un-notified releases) so filtering in memory is cheap.
    List<WaterSchedule> findAllByStatusAndNotificationTriggeredAtIsNull(ScheduleStatus status);
}
