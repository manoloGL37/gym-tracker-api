package dev.manuel.gymtracker_api.routine.dto;

import java.util.UUID;

public record RoutineExerciseResponse(
        UUID id,
        UUID exerciseId,
        int position,
        int sets,
        int targetReps,
        int restSeconds,
        String notes
) {}