package dev.manuel.gymtracker_api.workout.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateWorkoutRequest(

        UUID clientId,

        @NotNull
        UUID routineId,

        LocalDateTime startedAt,

        LocalDateTime completedAt,

        @jakarta.validation.constraints.Size(max = 500)
        String notes

) {}
