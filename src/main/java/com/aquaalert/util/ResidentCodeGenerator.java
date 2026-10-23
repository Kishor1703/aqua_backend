package com.aquaalert.util;

import com.aquaalert.repository.ResidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates human-scannable resident codes like "RES-A3F9K2" — short
 * enough to write on a paper form during door-to-door registration,
 * random enough to avoid collisions, and independent of the DB's
 * auto-increment id (so codes don't leak row counts).
 */
@Component
@RequiredArgsConstructor
public class ResidentCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no O/0/I/1 ambiguity
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int LENGTH = 6;
    private static final int MAX_ATTEMPTS = 10;

    private final ResidentRepository residentRepository;

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = "RES-" + randomSuffix();
            if (!residentRepository.existsByResidentCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique resident code after " + MAX_ATTEMPTS + " attempts");
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
