package dev.manuel.gymtracker_api.workout.dto;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public record WorkoutExerciseResponse(
        UUID id,
        UUID exerciseId,
        int position,
        String notes,
        List<WorkoutSetResponse> sets,
        UUID clientId,
        @Schema(description = "Persisted historical exercise name; nullable for unrecoverable legacy rows") String exerciseNameSnapshot
) {}
