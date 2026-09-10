package dev.manuel.gymtracker_api.exercise.repository;

import dev.manuel.gymtracker_api.exercise.model.Exercise;
import dev.manuel.gymtracker_api.exercise.model.ExerciseSource;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExerciseRepository
        extends JpaRepository<Exercise, UUID>, JpaSpecificationExecutor<Exercise> {

    @Query("""
            SELECT e
            FROM Exercise e
            WHERE e.deletedAt IS NULL
              AND (e.ownerId IS NULL OR e.ownerId = :ownerId)
            """)
    Page<Exercise> findAvailableExercises(
            @Param("ownerId") UUID ownerId,
            Pageable pageable
    );

    List<Exercise> findBySourceAndSourceIdIn(
                ExerciseSource source,
                List<String> sourceIds
        );

    Optional<Exercise> findByOwnerIdAndClientId(UUID ownerId, UUID clientId);
}
