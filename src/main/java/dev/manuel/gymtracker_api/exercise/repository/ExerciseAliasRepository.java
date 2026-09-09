package dev.manuel.gymtracker_api.exercise.repository;

import dev.manuel.gymtracker_api.exercise.model.ExerciseAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExerciseAliasRepository
        extends JpaRepository<ExerciseAlias, UUID> {

    List<ExerciseAlias> findByExerciseIdIn(List<UUID> exerciseIds);

    List<ExerciseAlias> findByExerciseId(UUID exerciseId);
}