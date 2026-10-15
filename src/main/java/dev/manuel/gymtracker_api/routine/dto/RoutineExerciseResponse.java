package dev.manuel.gymtracker_api.routine.dto;

import java.util.UUID;
import dev.manuel.gymtracker_api.exercise.model.Laterality;

public record RoutineExerciseResponse(
        UUID id,
        UUID exerciseId,
        int position,
        int sets,
        Integer targetReps,
        int restSeconds,
        String notes,
        Integer targetDurationSeconds,
        Laterality lateralityOverride
) {}