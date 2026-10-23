package com.aquaalert.controller;

import com.aquaalert.dto.publicapi.PublicScheduleResponse;
import com.aquaalert.entity.WaterSchedule;
import com.aquaalert.repository.WaterScheduleRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@Tag(name = "Public")
public class PublicScheduleController {

    private final WaterScheduleRepository scheduleRepository;

    @GetMapping("/today-schedules")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PublicScheduleResponse>> today(@RequestParam(required = false) String ward) {
        LocalDate today = LocalDate.now();
        List<WaterSchedule> schedules = (ward == null || ward.isBlank())
                ? scheduleRepository.findAllByReleaseDateOrderByReleaseTimeAsc(today)
                : scheduleRepository.findAllByAreaWardIgnoreCaseAndReleaseDateOrderByReleaseTimeAsc(ward, today);

        return ResponseEntity.ok(schedules.stream()
                .map(s -> new PublicScheduleResponse(
                        s.getArea().getName(),
                        s.getArea().getWard(),
                        s.getArea().getZone(),
                        s.getReleaseDate(),
                        s.getReleaseTime(),
                        s.getNotifyBeforeMins(),
                        s.getStatus(),
                        s.getNotes()
                ))
                .toList());
    }
}
