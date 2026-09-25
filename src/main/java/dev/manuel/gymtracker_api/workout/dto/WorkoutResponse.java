package dev.manuel.gymtracker_api.workout.dto;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record WorkoutResponse(
        UUID id,
        UUID clientId,
        UUID routineId,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String notes,
        List<WorkoutExerciseResponse> exercises,
        LocalDateTime createdAt,
        Instant startedAtInstant,
        Instant completedAtInstant,
        String calendarZone,
        @Schema(description = "Persisted historical display name; nullable for legacy rows whose name cannot be recovered") String nameSnapshot
) {}
