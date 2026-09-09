package dev.manuel.gymtracker_api.exercise.importer;

import dev.manuel.gymtracker_api.exercise.model.Exercise;
import dev.manuel.gymtracker_api.exercise.model.ExerciseSource;
import dev.manuel.gymtracker_api.exercise.model.ExerciseTranslation;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseRepository;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseTranslationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ExerciseDatasetImporter {

    private static final ExerciseSource SOURCE =
        ExerciseSource.EXERCISES_DATASET;

    private static final Logger logger =
            LoggerFactory.getLogger(ExerciseDatasetImporter.class);

    private final ExerciseDatasetReader reader;
    private final ExerciseRepository exerciseRepository;
    private final ExerciseTranslationRepository translationRepository;

    public ExerciseDatasetImporter(
            ExerciseDatasetReader reader,
            ExerciseRepository exerciseRepository,
            ExerciseTranslationRepository translationRepository
    ) {
        this.reader = reader;
        this.exerciseRepository = exerciseRepository;
        this.translationRepository = translationRepository;
    }

    @Transactional
    public void importDataset() {
        List<ExerciseDatasetItem> items = reader.read();

        List<String> sourceIds = items.stream()
                .map(ExerciseDatasetItem::id)
                .toList();

        List<Exercise> existingExercises =
                exerciseRepository.findBySourceAndSourceIdIn(
                        SOURCE,
                        sourceIds
                );

        Map<String, Exercise> exercisesBySourceId =
                new HashMap<>();

        for (Exercise exercise : existingExercises) {
            exercisesBySourceId.put(
                    exercise.getSourceId(),
                    exercise
            );
        }

        logger.info(
            "Exercise dataset imported successfully: {} exercises",
            items.size()
        );

        List<UUID> existingExerciseIds = existingExercises.stream()
                .map(Exercise::getId)
                .toList();

        List<ExerciseTranslation> existingTranslations =
                existingExerciseIds.isEmpty()
                        ? List.of()
                        : translationRepository.findByExerciseIdIn(
                                existingExerciseIds
                        );

        Map<String, ExerciseTranslation> translationsByExerciseAndLanguage =
                new HashMap<>();

        for (ExerciseTranslation translation : existingTranslations) {
            String key = translation.getExerciseId()
                    + ":" + translation.getLanguage();

            translationsByExerciseAndLanguage.put(
                    key,
                    translation
            );
        }

        LocalDateTime now = LocalDateTime.now();

        for (ExerciseDatasetItem item : items) {
            Exercise exercise = exercisesBySourceId.get(item.id());

            if (exercise == null) {
                exercise = new Exercise();
                exercise.setId(UUID.randomUUID());
                exercise.setOwnerId(null);
                exercise.setSource(SOURCE);
                exercise.setSourceId(item.id());
                exercise.setCreatedAt(now);

                exercisesBySourceId.put(item.id(), exercise);
            }

            exercise.setCategory(item.category());
            exercise.setEquipment(item.equipment());
            exercise.setTargetMuscle(item.target());
            exercise.setMuscleGroup(item.muscle_group());
            exercise.setSecondaryMuscles(item.secondary_muscles());
            exercise.setUpdatedAt(now);
            exercise.setDeletedAt(null);

            Exercise savedExercise =
                    exerciseRepository.save(exercise);

            importTranslations(
                    savedExercise,
                    item,
                    translationsByExerciseAndLanguage
            );
        }
    }

    private void importTranslations(
            Exercise exercise,
            ExerciseDatasetItem item,
            Map<String, ExerciseTranslation> translationsByExerciseAndLanguage
    ) {
        for (Map.Entry<String, String> entry :
                item.instructions().entrySet()) {

            String language = entry.getKey();
            String instructions = entry.getValue();

            String key = exercise.getId()
                    + ":" + language;

            ExerciseTranslation translation =
                    translationsByExerciseAndLanguage.get(key);

            if (translation == null) {
                translation = new ExerciseTranslation();
                translation.setId(UUID.randomUUID());
                translation.setExerciseId(exercise.getId());
                translation.setLanguage(language);

                translationsByExerciseAndLanguage.put(
                        key,
                        translation
                );
            }

            translation.setName(item.name());

            translation.setInstructions(instructions);

            translationRepository.save(translation);
        }
    }
}