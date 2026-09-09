package dev.manuel.gymtracker_api.workout.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record WorkoutResponse(
        UUID id,
        UUID routineId,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String notes,
        List<WorkoutExerciseResponse> exercises,
        LocalDateTime createdAt
) {}