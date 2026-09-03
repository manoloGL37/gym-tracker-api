package dev.manuel.gymtracker_api.exercise.dto;

import java.util.List;

public record ExercisePageResponse(
        List<ExerciseResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}