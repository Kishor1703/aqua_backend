package com.aquaalert.controller;

import com.aquaalert.dto.area.AreaRequest;
import com.aquaalert.dto.area.AreaResponse;
import com.aquaalert.dto.common.PagedResponse;
import com.aquaalert.service.AreaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/areas")
@RequiredArgsConstructor
@Tag(name = "Area Management")
@PreAuthorize("isAuthenticated()") // Operators and Admins can both manage areas
public class AreaController {

    private final AreaService areaService;

    @GetMapping
    public ResponseEntity<PagedResponse<AreaResponse>> search(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return ResponseEntity.ok(areaService.search(search, activeOnly, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AreaResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(areaService.getById(id));
    }

    @PostMapping
    public ResponseEntity<AreaResponse> create(@Valid @RequestBody AreaRequest request) {
        AreaResponse created = areaService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AreaResponse> update(@PathVariable Long id, @Valid @RequestBody AreaRequest request) {
        return ResponseEntity.ok(areaService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> setActive(@PathVariable Long id, @RequestParam boolean active) {
        areaService.setActive(id, active);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        areaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
