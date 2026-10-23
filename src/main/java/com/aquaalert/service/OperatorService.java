package com.aquaalert.service;

import com.aquaalert.dto.operator.OperatorCreateRequest;
import com.aquaalert.dto.operator.OperatorResponse;
import com.aquaalert.entity.Role;
import com.aquaalert.entity.User;
import com.aquaalert.exception.DuplicateResourceException;
import com.aquaalert.exception.ResourceNotFoundException;
import com.aquaalert.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OperatorService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final UserAccessService userAccessService;

    @Transactional
    public OperatorResponse create(OperatorCreateRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("A user with email '%s' already exists".formatted(request.email()));
        }

        User admin = getCurrentUser();
        User operator = User.builder()
                .fullName(request.fullName())
                .email(request.email().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.OPERATOR)
                .ward(request.ward().trim())
                .phoneNumber(blankToNull(request.phoneNumber()))
                .active(true)
                .build();

        User saved = userRepository.save(operator);
        auditLogService.log(admin, "OPERATOR_CREATED", "User", saved.getId(),
                "Created operator '%s'".formatted(saved.getEmail()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OperatorResponse> list() {
        return userRepository.findAllByRoleOrderByCreatedAtDesc(Role.OPERATOR).stream()
                .map(this::toResponse)
                .toList();
    }

    private OperatorResponse toResponse(User user) {
        return new OperatorResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getWard(),
                user.getPhoneNumber(),
                user.isActive(),
                user.getCreatedAt()
        );
    }

    private User getCurrentUser() {
        return userAccessService.currentUser();
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
