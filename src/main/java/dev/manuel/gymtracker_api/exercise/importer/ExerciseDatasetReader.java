package dev.manuel.gymtracker_api.exercise.importer;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class ExerciseDatasetReader {

    private final ObjectMapper objectMapper;

    public ExerciseDatasetReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ExerciseDatasetItem> read() {
        ClassPathResource resource =
                new ClassPathResource("data/exercises.json");

        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(
                    inputStream,
                    new TypeReference<>() {}
            );
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not read exercise dataset",
                    e
            );
        }
    }
}