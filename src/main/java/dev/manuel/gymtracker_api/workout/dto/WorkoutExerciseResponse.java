package dev.manuel.gymtracker_api.workout.dto;

import java.util.List;
import java.util.UUID;

public record WorkoutExerciseResponse(
        UUID id,
        UUID exerciseId,
        int position,
        String notes,
        List<WorkoutSetResponse> sets
) {}