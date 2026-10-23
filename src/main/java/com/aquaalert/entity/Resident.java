package com.aquaalert.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "residents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Resident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resident_code", nullable = false, unique = true, length = 30)
    private String residentCode;

    @Column(name = "house_number", nullable = false, length = 30)
    private String houseNumber;

    @Column(name = "owner_name", nullable = false, length = 150)
    private String ownerName;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(length = 254)
    private String email;

    @Column(name = "alternate_phone_number", length = 20)
    private String alternatePhoneNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private Area area;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_preference", nullable = false, length = 20)
    @Builder.Default
    private NotificationPreference notificationPreference = NotificationPreference.SMS;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ResidentStatus status = ResidentStatus.ACTIVE;

    @Column(name = "fcm_token")
    private String fcmToken;

    @Column(name = "preferred_language", nullable = false, length = 10)
    @Builder.Default
    private String preferredLanguage = "en";

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
}
