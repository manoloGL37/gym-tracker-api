package dev.manuel.gymtracker_api.exercise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExerciseTranslationRequest(

        @NotBlank
        @Size(max = 10)
        String language,

        @NotBlank
        @Size(max = 255)
        String name,

        String instructions
) {}