package com.aquaalert.service;

import com.aquaalert.dto.common.PagedResponse;
import com.aquaalert.dto.schedule.ScheduleRequest;
import com.aquaalert.dto.schedule.ScheduleResponse;
import com.aquaalert.entity.Area;
import com.aquaalert.entity.ScheduleStatus;
import com.aquaalert.entity.WaterSchedule;
import com.aquaalert.exception.InvalidStateTransitionException;
import com.aquaalert.exception.ResourceNotFoundException;
import com.aquaalert.repository.AreaRepository;
import com.aquaalert.repository.WaterScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WaterScheduleService {

    private final WaterScheduleRepository scheduleRepository;
    private final AreaRepository areaRepository;
    private final AuditLogService auditLogService;
    private final UserAccessService userAccessService;

    private static final Map<ScheduleStatus, Set<ScheduleStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(ScheduleStatus.class);
    static {
        ALLOWED_TRANSITIONS.put(ScheduleStatus.SCHEDULED, EnumSet.of(ScheduleStatus.RELEASED, ScheduleStatus.DELAYED, ScheduleStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(ScheduleStatus.DELAYED, EnumSet.of(ScheduleStatus.SCHEDULED, ScheduleStatus.RELEASED, ScheduleStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(ScheduleStatus.RELEASED, EnumSet.noneOf(ScheduleStatus.class));
        ALLOWED_TRANSITIONS.put(ScheduleStatus.CANCELLED, EnumSet.noneOf(ScheduleStatus.class));
    }

    @Transactional
    public ScheduleResponse create(ScheduleRequest request) {
        Area area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new ResourceNotFoundException("Area not found: " + request.areaId()));
        userAccessService.requireWardAccess(area.getWard());

        validateNotInPast(request.releaseDate(), request.releaseTime());

        WaterSchedule schedule = WaterSchedule.builder()
                .area(area)
                .releaseDate(request.releaseDate())
                .releaseTime(request.releaseTime())
                .notifyBeforeMins(request.notifyBeforeMins())
                .notes(request.notes())
                .status(ScheduleStatus.SCHEDULED)
                .createdBy(userAccessService.currentUser())
                .build();

        WaterSchedule saved = scheduleRepository.save(schedule);
        auditLogService.log(userAccessService.currentUser(), "SCHEDULE_CREATED", "WaterSchedule", saved.getId(),
                "Scheduled release for %s at %s on %s".formatted(area.getName(), saved.getReleaseTime(), saved.getReleaseDate()));

        return toResponse(saved);
    }

    @Transactional
    public ScheduleResponse update(Long id, ScheduleRequest request) {
        WaterSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + id));
        userAccessService.requireWardAccess(schedule.getArea().getWard());

        if (schedule.getStatus() == ScheduleStatus.RELEASED || schedule.getStatus() == ScheduleStatus.CANCELLED) {
            throw new InvalidStateTransitionException("Cannot edit a schedule that is already " + schedule.getStatus());
        }

        Area area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new ResourceNotFoundException("Area not found: " + request.areaId()));
        userAccessService.requireWardAccess(area.getWard());

        validateNotInPast(request.releaseDate(), request.releaseTime());

        schedule.setArea(area);
        schedule.setReleaseDate(request.releaseDate());
        schedule.setReleaseTime(request.releaseTime());
        schedule.setNotifyBeforeMins(request.notifyBeforeMins());
        schedule.setNotes(request.notes());

        if (schedule.getStatus() == ScheduleStatus.DELAYED) {
            schedule.setStatus(ScheduleStatus.SCHEDULED);
            schedule.setNotificationTriggeredAt(null);
        }

        WaterSchedule saved = scheduleRepository.save(schedule);
        auditLogService.log(userAccessService.currentUser(), "SCHEDULE_UPDATED", "WaterSchedule", saved.getId(),
                "Updated schedule for %s".formatted(area.getName()));

        return toResponse(saved);
    }

    @Transactional
    public ScheduleResponse updateStatus(Long id, ScheduleStatus newStatus, String notes) {
        WaterSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + id));
        userAccessService.requireWardAccess(schedule.getArea().getWard());

        Set<ScheduleStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(schedule.getStatus(), EnumSet.noneOf(ScheduleStatus.class));
        if (!allowed.contains(newStatus)) {
            throw new InvalidStateTransitionException(
                    "Cannot move schedule from %s to %s".formatted(schedule.getStatus(), newStatus));
        }

        schedule.setStatus(newStatus);
        if (notes != null && !notes.isBlank()) {
            schedule.setNotes(notes);
        }
        if (newStatus == ScheduleStatus.SCHEDULED) {
            schedule.setNotificationTriggeredAt(null);
        }

        WaterSchedule saved = scheduleRepository.save(schedule);
        auditLogService.log(userAccessService.currentUser(), "SCHEDULE_STATUS_CHANGED", "WaterSchedule", saved.getId(),
                "Status changed to %s".formatted(newStatus));

        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        WaterSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + id));
        userAccessService.requireWardAccess(schedule.getArea().getWard());

        if (schedule.getStatus() == ScheduleStatus.RELEASED) {
            throw new InvalidStateTransitionException("Cannot delete a schedule that has already been released");
        }

        scheduleRepository.delete(schedule);
        auditLogService.log(userAccessService.currentUser(), "SCHEDULE_DELETED", "WaterSchedule", id, "Deleted schedule " + id);
    }

    @Transactional(readOnly = true)
    public ScheduleResponse getById(Long id) {
        WaterSchedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found: " + id));
        userAccessService.requireWardAccess(schedule.getArea().getWard());
        return toResponse(schedule);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ScheduleResponse> search(Long areaId, ScheduleStatus status,
                                                    LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        String ward = userAccessService.isAdmin(userAccessService.currentUser()) ? null : userAccessService.currentWard();
        Page<WaterSchedule> page = scheduleRepository.search(areaId, status, fromDate, toDate, ward, pageable);
        return PagedResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public java.util.List<ScheduleResponse> listForDate(LocalDate date) {
        String ward = userAccessService.isAdmin(userAccessService.currentUser()) ? null : userAccessService.currentWard();
        return (ward == null
                ? scheduleRepository.findAllByReleaseDateOrderByReleaseTimeAsc(date)
                : scheduleRepository.findAllByAreaWardIgnoreCaseAndReleaseDateOrderByReleaseTimeAsc(ward, date))
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<ScheduleResponse> listUpcoming(int limit) {
        String ward = userAccessService.isAdmin(userAccessService.currentUser()) ? null : userAccessService.currentWard();
        return scheduleRepository
                .findUpcoming(LocalDate.now(), ward, org.springframework.data.domain.PageRequest.of(0, limit))
                .stream().map(this::toResponse).toList();
    }

    private void validateNotInPast(LocalDate date, java.time.LocalTime time) {
        LocalDateTime requested = LocalDateTime.of(date, time);
        if (requested.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Release date/time cannot be in the past");
        }
    }

    private ScheduleResponse toResponse(WaterSchedule s) {
        String createdByName = s.getCreatedBy() != null ? s.getCreatedBy().getFullName() : null;
        return new ScheduleResponse(
                s.getId(),
                s.getArea().getId(),
                s.getArea().getName(),
                s.getArea().getWard(),
                s.getReleaseDate(),
                s.getReleaseTime(),
                s.getNotifyBeforeMins(),
                s.getNotifyAt(),
                s.getStatus(),
                s.getNotes(),
                createdByName,
                s.getCreatedAt(),
                s.getUpdatedAt()
        );
    }
}
