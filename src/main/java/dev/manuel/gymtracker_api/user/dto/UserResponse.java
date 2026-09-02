package dev.manuel.gymtracker_api.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        LocalDateTime createdAt
) {
}