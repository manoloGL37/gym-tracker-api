package dev.manuel.gymtracker_api.exercise.importer;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "gymtracker.import.exercises",
        havingValue = "true"
)
public class ExerciseDatasetImportRunner implements ApplicationRunner {

    private final ExerciseDatasetImporter importer;

    public ExerciseDatasetImportRunner(ExerciseDatasetImporter importer) {
        this.importer = importer;
    }

    @Override
    public void run(ApplicationArguments args) {
        importer.importDataset();
    }
}