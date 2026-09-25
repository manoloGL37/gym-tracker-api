package dev.manuel.gymtracker_api.workout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

import java.time.Instant;
import java.util.UUID;

public record CreateMobileWorkoutRequest(
        @Schema(description = "Stable idempotency UUID scoped to the authenticated user") @NotNull UUID clientId,
        @Schema(description = "Optional owned source routine; soft-deleted routines are accepted") UUID routineId,
        @Schema(description = "Exact start instant; ISO-8601 with UTC or offset") @NotNull Instant startedAt,
        @Schema(description = "Exact completion instant; must be at or after startedAt") @NotNull Instant completedAt,
        @Schema(description = "IANA zone captured for the workout calendar date") @NotBlank String calendarZone,
        @Size(max = 500) String notes,
        @Schema(description = "Immutable historical workout display name; independent of routineId") @NotBlank @Size(max = 150) String nameSnapshot,
        @Schema(description = "Authoritative performed exercise snapshots; empty is allowed") @NotNull @Size(max = 50) List<@Valid MobileWorkoutExerciseRequest> exercises
) {
}
