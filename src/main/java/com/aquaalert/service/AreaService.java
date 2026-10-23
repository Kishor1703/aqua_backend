package com.aquaalert.service;

import com.aquaalert.dto.area.AreaRequest;
import com.aquaalert.dto.area.AreaResponse;
import com.aquaalert.dto.common.PagedResponse;
import com.aquaalert.entity.Area;
import com.aquaalert.entity.User;
import com.aquaalert.exception.DuplicateResourceException;
import com.aquaalert.exception.ResourceNotFoundException;
import com.aquaalert.repository.AreaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AreaService {

    private final AreaRepository areaRepository;
    private final AuditLogService auditLogService;
    private final UserAccessService userAccessService;

    @Transactional
    public AreaResponse create(AreaRequest request) {
        userAccessService.requireWardAccess(request.ward());

        if (areaRepository.existsByNameIgnoreCaseAndWardIgnoreCase(request.name(), request.ward())) {
            throw new DuplicateResourceException(
                    "An area named '%s' already exists in ward '%s'".formatted(request.name(), request.ward()));
        }

        User currentUser = userAccessService.currentUser();
        Area area = Area.builder()
                .name(request.name())
                .ward(request.ward())
                .zone(request.zone())
                .street(request.street())
                .active(true)
                .createdBy(currentUser)
                .build();

        Area saved = areaRepository.save(area);
        auditLogService.log(currentUser, "AREA_CREATED", "Area", saved.getId(),
                "Created area '%s' (ward: %s)".formatted(saved.getName(), saved.getWard()));

        return toResponse(saved);
    }

    @Transactional
    public AreaResponse update(Long id, AreaRequest request) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Area not found: " + id));
        userAccessService.requireWardAccess(area.getWard());
        userAccessService.requireWardAccess(request.ward());

        boolean nameOrWardChanged = !area.getName().equalsIgnoreCase(request.name())
                || !area.getWard().equalsIgnoreCase(request.ward());
        if (nameOrWardChanged
                && areaRepository.existsByNameIgnoreCaseAndWardIgnoreCase(request.name(), request.ward())) {
            throw new DuplicateResourceException(
                    "An area named '%s' already exists in ward '%s'".formatted(request.name(), request.ward()));
        }

        area.setName(request.name());
        area.setWard(request.ward());
        area.setZone(request.zone());
        area.setStreet(request.street());

        Area saved = areaRepository.save(area);
        auditLogService.log(userAccessService.currentUser(), "AREA_UPDATED", "Area", saved.getId(),
                "Updated area '%s'".formatted(saved.getName()));

        return toResponse(saved);
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Area not found: " + id));
        userAccessService.requireWardAccess(area.getWard());

        area.setActive(active);
        areaRepository.save(area);

        auditLogService.log(userAccessService.currentUser(), active ? "AREA_ACTIVATED" : "AREA_DEACTIVATED",
                "Area", area.getId(), "Area '%s' set to active=%s".formatted(area.getName(), active));
    }

    @Transactional
    public void delete(Long id) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Area not found: " + id));
        userAccessService.requireWardAccess(area.getWard());

        long residentCount = safeResidentCount(id);
        if (residentCount > 0) {
            area.setActive(false);
            areaRepository.save(area);
            auditLogService.log(userAccessService.currentUser(), "AREA_SOFT_DELETED", "Area", id,
                    "Area '%s' has %d residents - deactivated instead of deleted".formatted(area.getName(), residentCount));
            return;
        }

        areaRepository.delete(area);
        auditLogService.log(userAccessService.currentUser(), "AREA_DELETED", "Area", id, "Deleted area '%s'".formatted(area.getName()));
    }

    @Transactional(readOnly = true)
    public AreaResponse getById(Long id) {
        Area area = areaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Area not found: " + id));
        userAccessService.requireWardAccess(area.getWard());
        return toResponse(area);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AreaResponse> search(String search, boolean activeOnly, Pageable pageable) {
        String ward = userAccessService.isAdmin(userAccessService.currentUser()) ? null : userAccessService.currentWard();
        Page<Area> page = areaRepository.search(search, activeOnly, ward, pageable);
        return PagedResponse.from(page.map(this::toResponse));
    }

    private AreaResponse toResponse(Area area) {
        return new AreaResponse(
                area.getId(),
                area.getName(),
                area.getWard(),
                area.getZone(),
                area.getStreet(),
                area.isActive(),
                safeResidentCount(area.getId()),
                area.getCreatedAt(),
                area.getUpdatedAt()
        );
    }

    private long safeResidentCount(Long areaId) {
        try {
            return areaRepository.countResidentsByAreaId(areaId);
        } catch (Exception ex) {
            log.debug("Resident count unavailable for area {}: {}", areaId, ex.getMessage());
            return 0L;
        }
    }
}
