package com.aquaalert.service;

import com.aquaalert.dto.common.PagedResponse;
import com.aquaalert.dto.notification.NotificationResponse;
import com.aquaalert.dto.notification.NotificationStatsResponse;
import com.aquaalert.entity.Notification;
import com.aquaalert.entity.NotificationChannel;
import com.aquaalert.entity.NotificationStatus;
import com.aquaalert.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;
    private final UserAccessService userAccessService;

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> search(Long scheduleId, Long residentId,
                                                         NotificationStatus status, NotificationChannel channel,
                                                         Pageable pageable) {
        String ward = userAccessService.isAdmin(userAccessService.currentUser()) ? null : userAccessService.currentWard();
        Page<Notification> page = notificationRepository.search(scheduleId, residentId, status, channel, ward, pageable);
        return PagedResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public NotificationResponse getById(Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new com.aquaalert.exception.ResourceNotFoundException("Notification not found: " + id));
        userAccessService.requireWardAccess(n.getSchedule().getArea().getWard());
        return toResponse(n);
    }

    @Transactional(readOnly = true)
    public NotificationStatsResponse stats() {
        String ward = userAccessService.isAdmin(userAccessService.currentUser()) ? null : userAccessService.currentWard();
        long sent = ward == null
                ? notificationRepository.countByStatus(NotificationStatus.SENT)
                : notificationRepository.countByStatusAndScheduleAreaWardIgnoreCase(NotificationStatus.SENT, ward);
        long failed = ward == null
                ? notificationRepository.countByStatus(NotificationStatus.FAILED)
                : notificationRepository.countByStatusAndScheduleAreaWardIgnoreCase(NotificationStatus.FAILED, ward);
        long pending = ward == null
                ? notificationRepository.countByStatus(NotificationStatus.PENDING)
                : notificationRepository.countByStatusAndScheduleAreaWardIgnoreCase(NotificationStatus.PENDING, ward);
        long retrying = ward == null
                ? notificationRepository.countByStatus(NotificationStatus.RETRYING)
                : notificationRepository.countByStatusAndScheduleAreaWardIgnoreCase(NotificationStatus.RETRYING, ward);

        long totalAttempted = sent + failed;
        double successRate = totalAttempted == 0 ? 0.0 : (sent * 100.0) / totalAttempted;

        return new NotificationStatsResponse(sent, failed, pending, retrying, Math.round(successRate * 10) / 10.0);
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getSchedule().getId(),
                n.getSchedule().getArea().getName(),
                n.getResident().getId(),
                n.getResident().getOwnerName(),
                n.getResident().getPhoneNumber(),
                n.getChannel(),
                n.getStatus(),
                n.getProviderMessageId(),
                n.getErrorMessage(),
                n.getAttemptCount(),
                n.getSentAt(),
                n.getCreatedAt()
        );
    }
}
