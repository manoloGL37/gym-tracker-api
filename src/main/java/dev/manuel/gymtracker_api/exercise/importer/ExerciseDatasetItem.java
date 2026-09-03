package dev.manuel.gymtracker_api.exercise.importer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExerciseDatasetItem(
        String id,
        String name,
        String category,
        String equipment,
        Map<String, String> instructions,
        String muscle_group,
        String[] secondary_muscles,
        String target
) {
}