package dev.manuel.gymtracker_api.routine.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RoutineSummaryResponse(
        UUID id,
        UUID clientId,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
