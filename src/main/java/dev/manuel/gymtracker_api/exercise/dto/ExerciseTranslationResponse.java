package dev.manuel.gymtracker_api.exercise.dto;

public record ExerciseTranslationResponse(
        String language,
        String name,
        String instructions
) {
}