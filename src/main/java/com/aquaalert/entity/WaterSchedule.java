package com.aquaalert.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "water_schedules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaterSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @Column(name = "release_date", nullable = false)
    private LocalDate releaseDate;

    @Column(name = "release_time", nullable = false)
    private LocalTime releaseTime;

    @Column(name = "notify_before_mins", nullable = false)
    @Builder.Default
    private Integer notifyBeforeMins = 60;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ScheduleStatus status = ScheduleStatus.SCHEDULED;

    @Column(length = 500)
    private String notes;

    // Set once the automatic-notification scheduler (Module 5) has
    // actually dispatched the pre-release alert for this schedule, so
    // the job never fires the same notification twice.
    @Column(name = "notification_triggered_at")
    private Instant notificationTriggeredAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    @Transient
    public java.time.LocalDateTime getReleaseDateTime() {
        return java.time.LocalDateTime.of(releaseDate, releaseTime);
    }

    @Transient
    public java.time.LocalDateTime getNotifyAt() {
        return getReleaseDateTime().minusMinutes(notifyBeforeMins);
    }
}
