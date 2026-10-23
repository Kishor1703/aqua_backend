package com.aquaalert.controller;

import com.aquaalert.dto.common.PagedResponse;
import com.aquaalert.dto.resident.CsvImportResult;
import com.aquaalert.dto.resident.ResidentRequest;
import com.aquaalert.dto.resident.ResidentResponse;
import com.aquaalert.entity.ResidentStatus;
import com.aquaalert.service.ResidentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/residents")
@RequiredArgsConstructor
@Tag(name = "Resident Management")
@PreAuthorize("isAuthenticated()")
public class ResidentController {

    private final ResidentService residentService;

    @GetMapping
    public ResponseEntity<PagedResponse<ResidentResponse>> search(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) ResidentStatus status,
            @PageableDefault(size = 20, sort = "ownerName") Pageable pageable
    ) {
        return ResponseEntity.ok(residentService.search(search, areaId, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResidentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(residentService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ResidentResponse> create(@Valid @RequestBody ResidentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(residentService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResidentResponse> update(@PathVariable Long id, @Valid @RequestBody ResidentRequest request) {
        return ResponseEntity.ok(residentService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> setStatus(@PathVariable Long id, @RequestParam ResidentStatus status) {
        residentService.setStatus(id, status);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        residentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvImportResult> importCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        return ResponseEntity.ok(residentService.importCsv(file));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false) ResidentStatus status
    ) {
        byte[] csv = residentService.exportCsv(areaId, status);
        String filename = "residents-export-%s.csv".formatted(LocalDate.now());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(csv);
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> downloadTemplate() {
        String header = "houseNumber,ownerName,phoneNumber,alternatePhoneNumber,email,areaName,ward,zone,street,notificationPreference,preferredLanguage\n";
        String example = "12A,Ramesh Kumar,9876543210,,ramesh@example.com,Gandhi Nagar,Ward 5,Central Zone,Main Road,SMS,en\n";
        byte[] content = (header + example).getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("resident-import-template.csv").build().toString())
                .body(content);
    }
}
