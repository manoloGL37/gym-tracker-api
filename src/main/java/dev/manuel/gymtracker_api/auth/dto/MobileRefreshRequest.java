package dev.manuel.gymtracker_api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record MobileRefreshRequest(@NotBlank String refreshToken) {
    @Override
    public String toString() {
        return "MobileRefreshRequest[refreshToken=REDACTED]";
    }
}
