package dev.manuel.gymtracker_api.auth.dto;

public record AuthResponse(
        String accessToken
) {
    @Override
    public String toString() {
        return "AuthResponse[accessToken=REDACTED]";
    }
}
