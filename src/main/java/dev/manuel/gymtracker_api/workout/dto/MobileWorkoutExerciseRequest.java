package dev.manuel.gymtracker_api.workout.dto;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record MobileWorkoutExerciseRequest(
        @Schema(description = "Optional stable local exercise-row UUID for reconciliation") UUID clientId,
        @Schema(description = "Optional visible global or caller-owned source exercise; soft-deleted sources are accepted") UUID exerciseId,
        @Schema(description = "Immutable historical display name") @NotBlank @Size(max = 255) String exerciseNameSnapshot,
        @Min(0) int position,
        @Size(max = 500) String notes,
        @Schema(description = "Completed performed sets; ordered by unique setNumber in responses") @NotNull List<@Valid WorkoutSetRequest> sets
) {}
