package dev.manuel.gymtracker_api.auth.service;

import dev.manuel.gymtracker_api.auth.dto.AuthResponse;

import java.time.Instant;

public record AuthSession(
        AuthResponse response,
        String refreshToken,
        Instant refreshExpiresAt
) {
}
