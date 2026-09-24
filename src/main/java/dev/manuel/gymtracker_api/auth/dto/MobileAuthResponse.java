package dev.manuel.gymtracker_api.auth.dto;

import dev.manuel.gymtracker_api.auth.service.AuthSession;

import java.time.Instant;

public record MobileAuthResponse(String accessToken, String refreshToken, Instant refreshExpiresAt) {
    public static MobileAuthResponse from(AuthSession session) {
        return new MobileAuthResponse(session.response().accessToken(), session.refreshToken(), session.refreshExpiresAt());
    }

    @Override
    public String toString() {
        return "MobileAuthResponse[accessToken=REDACTED, refreshToken=REDACTED, refreshExpiresAt="
                + refreshExpiresAt + "]";
    }
}
