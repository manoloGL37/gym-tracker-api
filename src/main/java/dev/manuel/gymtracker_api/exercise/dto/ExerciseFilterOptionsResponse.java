package dev.manuel.gymtracker_api.exercise.dto;

import java.util.List;

public record ExerciseFilterOptionsResponse(
        List<String> categories,
        List<String> equipment,
        List<String> muscleGroups,
        List<String> targetMuscles
) {
}
