package dev.manuel.gymtracker_api.workout.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateWorkoutRequest(

        @NotNull
        UUID routineId

) {}