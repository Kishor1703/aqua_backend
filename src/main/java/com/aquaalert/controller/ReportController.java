package com.aquaalert.controller;

import com.aquaalert.dto.report.ReportPeriod;
import com.aquaalert.dto.report.ReportResponse;
import com.aquaalert.service.ReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports")
@PreAuthorize("isAuthenticated()")
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<ReportResponse> summary(@RequestParam(defaultValue = "WEEKLY") ReportPeriod period) {
        return ResponseEntity.ok(reportService.build(period));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(defaultValue = "WEEKLY") ReportPeriod period) {
        byte[] csv = reportService.exportCsv(period);
        String filename = "aquaalert-report-%s.csv".formatted(period.name().toLowerCase());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(csv);
    }

    @GetMapping(value = "/export.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(@RequestParam(defaultValue = "WEEKLY") ReportPeriod period) {
        byte[] pdf = reportService.exportPdf(period);
        String filename = "aquaalert-report-%s.pdf".formatted(period.name().toLowerCase());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(pdf);
    }
}
