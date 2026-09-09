package dev.manuel.gymtracker_api.routine.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RoutineResponse(
        UUID id,
        String name,
        String description,
        List<RoutineExerciseResponse> exercises,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}