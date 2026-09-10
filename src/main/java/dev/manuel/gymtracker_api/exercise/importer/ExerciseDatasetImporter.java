package dev.manuel.gymtracker_api.exercise.importer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        name = "gymtracker.import.exercises",
        havingValue = "true"
)
public class ExerciseDatasetImporter implements ApplicationRunner {

    private static final int BATCH_SIZE = 100;

    private static final Logger logger =
            LoggerFactory.getLogger(ExerciseDatasetImporter.class);

    private final ExerciseDatasetReader reader;
    private final ExerciseDatasetBatchImporter batchImporter;

    public ExerciseDatasetImporter(
            ExerciseDatasetReader reader,
            ExerciseDatasetBatchImporter batchImporter
    ) {
        this.reader = reader;
        this.batchImporter = batchImporter;
    }

    @Override
    public void run(ApplicationArguments args) {

        logger.info(">>> EXERCISE DATASET IMPORT STARTING <<<");

        List<ExerciseDatasetItem> items = reader.read();

        logger.info(
                "Dataset loaded: {} exercises",
                items.size()
        );

        int totalBatches =
                (int) Math.ceil((double) items.size() / BATCH_SIZE);

        int totalImported = 0;

        for (int start = 0; start < items.size(); start += BATCH_SIZE) {

            int end = Math.min(
                    start + BATCH_SIZE,
                    items.size()
            );

            int batchNumber =
                    (start / BATCH_SIZE) + 1;

            List<ExerciseDatasetItem> batch =
                    items.subList(start, end);

            logger.info(
                    "Starting batch {}/{}: exercises {}-{}",
                    batchNumber,
                    totalBatches,
                    start + 1,
                    end
            );

            int imported =
                    batchImporter.importBatch(batch);

            totalImported += imported;

            logger.info(
                    "Batch {}/{} completed successfully: {} exercises",
                    batchNumber,
                    totalBatches,
                    imported
            );
        }

        logger.info(
                "Exercise dataset imported successfully: {} exercises",
                totalImported
        );
    }
}