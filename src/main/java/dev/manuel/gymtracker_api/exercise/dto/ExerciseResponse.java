package dev.manuel.gymtracker_api.exercise.dto;

import java.util.List;
import java.util.UUID;

public record ExerciseResponse(
        UUID id,
        String category,
        String equipment,
        String targetMuscle,
        String muscleGroup,
        String[] secondaryMuscles,
        List<ExerciseTranslationResponse> translations,
        List<ExerciseAliasResponse> aliases
) {
}