package dev.manuel.gymtracker_api.exercise.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateExerciseRequest(

        UUID clientId,

        @Size(max = 100)
        String category,

        @Size(max = 100)
        String equipment,

        @Size(max = 100)
        String targetMuscle,

        @Size(max = 100)
        String muscleGroup,

        List<String> secondaryMuscles,

        @Valid
        @NotNull
        @Size(min = 1)
        List<ExerciseTranslationRequest> translations
) {}
