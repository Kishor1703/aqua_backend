package com.aquaalert.controller;

import com.aquaalert.dto.operator.OperatorCreateRequest;
import com.aquaalert.dto.operator.OperatorResponse;
import com.aquaalert.service.OperatorService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/operators")
@RequiredArgsConstructor
@Tag(name = "Operator Management")
@PreAuthorize("hasRole('ADMIN')")
public class OperatorController {

    private final OperatorService operatorService;

    @GetMapping
    public ResponseEntity<List<OperatorResponse>> list() {
        return ResponseEntity.ok(operatorService.list());
    }

    @PostMapping
    public ResponseEntity<OperatorResponse> create(@Valid @RequestBody OperatorCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(operatorService.create(request));
    }
}
