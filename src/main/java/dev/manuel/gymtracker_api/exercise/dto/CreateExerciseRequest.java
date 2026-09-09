package dev.manuel.gymtracker_api.exercise.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateExerciseRequest(

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
        @Size(min = 1)
        List<ExerciseTranslationRequest> translations
) {}