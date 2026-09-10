package dev.manuel.gymtracker_api.exercise.dto;

import java.util.List;
import java.util.UUID;

import dev.manuel.gymtracker_api.exercise.model.ExerciseSource;

public record ExerciseResponse(
        UUID id,
        UUID clientId,
        ExerciseSource source,
        String sourceId,
        boolean editable,
        boolean deletable,
        String category,
        String equipment,
        String targetMuscle,
        String muscleGroup,
        String[] secondaryMuscles,
        List<ExerciseTranslationResponse> translations,
        List<ExerciseAliasResponse> aliases
) {
}
