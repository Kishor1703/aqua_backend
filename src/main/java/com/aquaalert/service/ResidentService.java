package com.aquaalert.service;

import com.aquaalert.dto.common.PagedResponse;
import com.aquaalert.dto.resident.CsvImportResult;
import com.aquaalert.dto.resident.ResidentRequest;
import com.aquaalert.dto.resident.ResidentResponse;
import com.aquaalert.entity.Area;
import com.aquaalert.entity.NotificationPreference;
import com.aquaalert.entity.Resident;
import com.aquaalert.entity.ResidentStatus;
import com.aquaalert.entity.User;
import com.aquaalert.exception.DuplicateResourceException;
import com.aquaalert.exception.ResourceNotFoundException;
import com.aquaalert.repository.AreaRepository;
import com.aquaalert.repository.ResidentRepository;
import com.aquaalert.util.ResidentCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResidentService {
    private static final String AUTO_IMPORTED_ZONE = "Imported";

    private final ResidentRepository residentRepository;
    private final AreaRepository areaRepository;
    private final ResidentCodeGenerator codeGenerator;
    private final AuditLogService auditLogService;
    private final UserAccessService userAccessService;

    @Transactional
    public ResidentResponse create(ResidentRequest request) {
        Area area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new ResourceNotFoundException("Area not found: " + request.areaId()));
        userAccessService.requireWardAccess(area.getWard());

        if (residentRepository.existsByPhoneNumberAndAreaId(request.phoneNumber(), area.getId())) {
            throw new DuplicateResourceException(
                    "A resident with phone number %s already exists in this area".formatted(request.phoneNumber()));
        }

        Resident resident = Resident.builder()
                .residentCode(codeGenerator.generate())
                .houseNumber(request.houseNumber())
                .ownerName(request.ownerName())
                .phoneNumber(request.phoneNumber())
                .alternatePhoneNumber(blankToNull(request.alternatePhoneNumber()))
                .email(blankToNull(request.email()))
                .area(area)
                .notificationPreference(request.notificationPreference())
                .status(ResidentStatus.ACTIVE)
                .preferredLanguage(request.preferredLanguage() == null || request.preferredLanguage().isBlank()
                        ? "en" : request.preferredLanguage())
                .build();

        Resident saved = residentRepository.save(resident);
        auditLogService.log(userAccessService.currentUser(), "RESIDENT_CREATED", "Resident", saved.getId(),
                "Added resident %s (%s)".formatted(saved.getOwnerName(), saved.getResidentCode()));

        return toResponse(saved);
    }

    @Transactional
    public ResidentResponse update(Long id, ResidentRequest request) {
        Resident resident = residentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resident not found: " + id));

        Area area = areaRepository.findById(request.areaId())
                .orElseThrow(() -> new ResourceNotFoundException("Area not found: " + request.areaId()));
        userAccessService.requireWardAccess(resident.getArea().getWard());
        userAccessService.requireWardAccess(area.getWard());

        boolean phoneOrAreaChanged = !resident.getPhoneNumber().equals(request.phoneNumber())
                || !resident.getArea().getId().equals(area.getId());
        if (phoneOrAreaChanged
                && residentRepository.existsByPhoneNumberAndAreaId(request.phoneNumber(), area.getId())) {
            throw new DuplicateResourceException(
                    "A resident with phone number %s already exists in this area".formatted(request.phoneNumber()));
        }

        resident.setHouseNumber(request.houseNumber());
        resident.setOwnerName(request.ownerName());
        resident.setPhoneNumber(request.phoneNumber());
        resident.setAlternatePhoneNumber(blankToNull(request.alternatePhoneNumber()));
        resident.setEmail(blankToNull(request.email()));
        resident.setArea(area);
        resident.setNotificationPreference(request.notificationPreference());
        if (request.preferredLanguage() != null && !request.preferredLanguage().isBlank()) {
            resident.setPreferredLanguage(request.preferredLanguage());
        }

        Resident saved = residentRepository.save(resident);
        auditLogService.log(userAccessService.currentUser(), "RESIDENT_UPDATED", "Resident", saved.getId(),
                "Updated resident %s".formatted(saved.getResidentCode()));

        return toResponse(saved);
    }

    @Transactional
    public void setStatus(Long id, ResidentStatus status) {
        Resident resident = residentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resident not found: " + id));
        userAccessService.requireWardAccess(resident.getArea().getWard());
        resident.setStatus(status);
        residentRepository.save(resident);
        auditLogService.log(userAccessService.currentUser(), "RESIDENT_STATUS_CHANGED", "Resident", id,
                "Resident %s set to %s".formatted(resident.getResidentCode(), status));
    }

    @Transactional
    public void delete(Long id) {
        Resident resident = residentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resident not found: " + id));
        userAccessService.requireWardAccess(resident.getArea().getWard());
        residentRepository.delete(resident);
        auditLogService.log(userAccessService.currentUser(), "RESIDENT_DELETED", "Resident", id,
                "Deleted resident %s (%s)".formatted(resident.getOwnerName(), resident.getResidentCode()));
    }

    @Transactional(readOnly = true)
    public ResidentResponse getById(Long id) {
        Resident resident = residentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resident not found: " + id));
        userAccessService.requireWardAccess(resident.getArea().getWard());
        return toResponse(resident);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ResidentResponse> search(String search, Long areaId, ResidentStatus status, Pageable pageable) {
        String ward = userAccessService.isAdmin(userAccessService.currentUser()) ? null : userAccessService.currentWard();
        Page<Resident> page = residentRepository.search(search, areaId, status, ward, pageable);
        return PagedResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(Long areaId, ResidentStatus status) {
        String ward = userAccessService.isAdmin(userAccessService.currentUser()) ? null : userAccessService.currentWard();
        List<Resident> residents = residentRepository.findAllForExport(areaId, status, ward);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder()
                     .setHeader("residentCode", "houseNumber", "ownerName", "phoneNumber",
                             "alternatePhoneNumber", "email", "areaName", "ward", "zone", "street",
                             "notificationPreference", "status", "preferredLanguage")
                     .build())) {

            for (Resident resident : residents) {
                printer.printRecord(
                        resident.getResidentCode(),
                        resident.getHouseNumber(),
                        resident.getOwnerName(),
                        resident.getPhoneNumber(),
                        resident.getAlternatePhoneNumber(),
                        resident.getEmail(),
                        resident.getArea().getName(),
                        resident.getArea().getWard(),
                        resident.getArea().getZone(),
                        resident.getArea().getStreet(),
                        resident.getNotificationPreference(),
                        resident.getStatus(),
                        resident.getPreferredLanguage()
                );
            }
            printer.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate CSV export", e);
        }
    }

    @Transactional
    public CsvImportResult importCsv(MultipartFile file) {
        List<CsvImportResult.RowError> errors = new ArrayList<>();
        int total = 0;
        int success = 0;
        User currentUser = userAccessService.currentUser();
        String operatorWard = userAccessService.isAdmin(currentUser) ? null : userAccessService.currentWard();

        Map<String, Area> areasByKey = areaRepository.findAll().stream()
                .filter(area -> operatorWard == null || area.getWard().equalsIgnoreCase(operatorWard))
                .collect(java.util.stream.Collectors.toMap(
                        area -> (area.getName() + "|" + area.getWard()).toLowerCase(Locale.ROOT),
                        area -> area,
                        (first, second) -> first));

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                total++;
                int rowNumber = (int) record.getRecordNumber() + 1;

                try {
                    importRow(record, areasByKey, currentUser, operatorWard);
                    success++;
                } catch (Exception rowEx) {
                    errors.add(new CsvImportResult.RowError(rowNumber, rowEx.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read CSV file: " + e.getMessage(), e);
        }

        auditLogService.log(currentUser, "RESIDENT_CSV_IMPORT", "Resident", null,
                "Imported %d/%d residents (%d failed)".formatted(success, total, errors.size()));

        return new CsvImportResult(total, success, errors.size(), errors);
    }

    private void importRow(CSVRecord record, Map<String, Area> areasByKey, User currentUser, String operatorWard) {
        String houseNumber = required(record, "houseNumber");
        String ownerName = required(record, "ownerName");
        String phoneNumber = required(record, "phoneNumber");
        String email = optional(record, "email");
        String areaName = required(record, "areaName");
        String ward = required(record, "ward");

        if (operatorWard != null && !ward.equalsIgnoreCase(operatorWard)) {
            throw new IllegalArgumentException("CSV row ward '%s' does not match your assigned ward '%s'".formatted(ward, operatorWard));
        }

        if (!phoneNumber.matches("^[+]?[0-9]{10,15}$")) {
            throw new IllegalArgumentException("Invalid phone number format: " + phoneNumber);
        }
        if (email != null && (!email.matches("^[^\\s@,;]+@[^\\s@,;]+\\.[^\\s@,;]+$") || email.length() > 254)) {
            throw new IllegalArgumentException("Invalid email address");
        }

        Area area = findOrCreateArea(record, areasByKey, currentUser, operatorWard, areaName, ward);

        if (residentRepository.existsByPhoneNumberAndAreaId(phoneNumber, area.getId())) {
            throw new IllegalArgumentException(
                    "A resident with phone number %s already exists in this area".formatted(phoneNumber));
        }

        NotificationPreference preference = parsePreference(record);

        Resident resident = Resident.builder()
                .residentCode(codeGenerator.generate())
                .houseNumber(houseNumber)
                .ownerName(ownerName)
                .phoneNumber(phoneNumber)
                .alternatePhoneNumber(optional(record, "alternatePhoneNumber"))
                .email(email)
                .area(area)
                .notificationPreference(preference)
                .status(ResidentStatus.ACTIVE)
                .preferredLanguage(optionalOrDefault(record, "preferredLanguage", "en"))
                .build();

        residentRepository.save(resident);
    }

    private Area findOrCreateArea(CSVRecord record, Map<String, Area> areasByKey, User currentUser,
                                  String operatorWard, String areaName, String ward) {
        String key = (areaName + "|" + ward).toLowerCase(Locale.ROOT);
        Area existing = areasByKey.get(key);
        if (existing != null) {
            return existing;
        }

        if (operatorWard != null && !ward.equalsIgnoreCase(operatorWard)) {
            throw new IllegalArgumentException("Cannot auto-create areas outside your assigned ward");
        }

        Area created = areaRepository.save(Area.builder()
                .name(areaName)
                .ward(ward)
                .zone(optionalOrDefault(record, "zone", AUTO_IMPORTED_ZONE))
                .street(optional(record, "street"))
                .active(true)
                .createdBy(currentUser)
                .build());

        areasByKey.put(key, created);
        auditLogService.log(currentUser, "AREA_AUTO_CREATED_FROM_RESIDENT_CSV", "Area", created.getId(),
                "Auto-created area '%s' in ward '%s' during resident CSV import".formatted(areaName, ward));
        return created;
    }

    private NotificationPreference parsePreference(CSVRecord record) {
        String raw = optional(record, "notificationPreference");
        if (raw == null || raw.isBlank()) {
            return NotificationPreference.SMS;
        }

        try {
            return NotificationPreference.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Invalid notificationPreference '%s' - expected one of SMS, VOICE, PUSH, WHATSAPP, ALL"
                            .formatted(raw));
        }
    }

    private String required(CSVRecord record, String column) {
        if (!record.isSet(column) || record.get(column) == null || record.get(column).isBlank()) {
            throw new IllegalArgumentException("Missing required column: " + column);
        }
        return record.get(column).trim();
    }

    private String optional(CSVRecord record, String column) {
        if (!record.isSet(column)) {
            return null;
        }

        String value = record.get(column);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private String optionalOrDefault(CSVRecord record, String column, String fallback) {
        String value = optional(record, column);
        return value == null ? fallback : value;
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private ResidentResponse toResponse(Resident resident) {
        return new ResidentResponse(
                resident.getId(),
                resident.getResidentCode(),
                resident.getHouseNumber(),
                resident.getOwnerName(),
                resident.getPhoneNumber(),
                resident.getAlternatePhoneNumber(),
                resident.getEmail(),
                resident.getArea().getId(),
                resident.getArea().getName(),
                resident.getNotificationPreference(),
                resident.getStatus(),
                resident.getPreferredLanguage(),
                resident.getCreatedAt(),
                resident.getUpdatedAt()
        );
    }
}
