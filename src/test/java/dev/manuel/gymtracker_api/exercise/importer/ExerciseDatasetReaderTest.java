package dev.manuel.gymtracker_api.exercise.importer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class ExerciseDatasetReaderTest {

    @Autowired
    private ExerciseDatasetReader reader;

    @Test
    void shouldReadExerciseDataset() {
        List<ExerciseDatasetItem> exercises = reader.read();

        assertEquals(1324, exercises.size());
        assertFalse(exercises.isEmpty());
        assertEquals("0001", exercises.get(0).id());
        assertEquals("3/4 sit-up", exercises.get(0).name());
    }
}