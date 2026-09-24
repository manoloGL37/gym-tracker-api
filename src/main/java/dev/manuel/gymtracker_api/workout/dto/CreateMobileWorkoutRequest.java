package dev.manuel.gymtracker_api.workout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateMobileWorkoutRequest(
        UUID clientId,
        @NotNull UUID routineId,
        @NotNull Instant startedAt,
        Instant completedAt,
        @NotBlank String calendarZone,
        @Size(max = 500) String notes
) {
}
