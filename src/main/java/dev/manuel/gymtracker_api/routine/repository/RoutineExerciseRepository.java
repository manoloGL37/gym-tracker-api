package dev.manuel.gymtracker_api.routine.repository;

import dev.manuel.gymtracker_api.routine.model.RoutineExercise;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RoutineExerciseRepository
        extends JpaRepository<RoutineExercise, UUID> {

    List<RoutineExercise> findByRoutineIdOrderByPositionAsc(
            UUID routineId
    );

    @Modifying
    @Query("""
            DELETE FROM RoutineExercise re
            WHERE re.routineId = :routineId
            """)
    void deleteByRoutineId(UUID routineId);
}