package dev.manuel.gymtracker_api.exercise.repository;

import dev.manuel.gymtracker_api.exercise.model.ExerciseTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExerciseTranslationRepository
        extends JpaRepository<ExerciseTranslation, UUID> {

    List<ExerciseTranslation> findByExerciseIdIn(List<UUID> exerciseIds);
}