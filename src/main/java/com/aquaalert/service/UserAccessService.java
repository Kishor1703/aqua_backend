package com.aquaalert.service;

import com.aquaalert.entity.Role;
import com.aquaalert.entity.User;
import com.aquaalert.exception.ResourceNotFoundException;
import com.aquaalert.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAccessService {

    private final UserRepository userRepository;

    public User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found: " + email));
    }

    public boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    public boolean isOperator(User user) {
        return user.getRole() == Role.OPERATOR;
    }

    public String currentWard() {
        User user = currentUser();
        if (isAdmin(user)) {
            return null;
        }

        if (user.getWard() == null || user.getWard().isBlank()) {
            throw new AccessDeniedException("Operator ward is not configured");
        }
        return user.getWard();
    }

    public void requireWardAccess(String ward) {
        User user = currentUser();
        if (isAdmin(user)) {
            return;
        }

        if (user.getWard() == null || user.getWard().isBlank()) {
            throw new AccessDeniedException("Operator ward is not configured");
        }

        if (ward == null || !ward.equalsIgnoreCase(user.getWard())) {
            throw new AccessDeniedException("You do not have access to this ward");
        }
    }

    public boolean canAccessWard(String ward) {
        User user = currentUser();
        return isAdmin(user)
                || (user.getWard() != null && !user.getWard().isBlank() && ward != null && ward.equalsIgnoreCase(user.getWard()));
    }
}
