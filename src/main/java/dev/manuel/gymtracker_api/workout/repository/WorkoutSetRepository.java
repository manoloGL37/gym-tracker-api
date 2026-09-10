package dev.manuel.gymtracker_api.workout.repository;

import dev.manuel.gymtracker_api.workout.model.WorkoutSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkoutSetRepository
        extends JpaRepository<WorkoutSet, UUID> {

    List<WorkoutSet> findByWorkoutExerciseIdOrderBySetNumberAsc(
            UUID workoutExerciseId
    );

    Optional<WorkoutSet> findByIdAndWorkoutExerciseId(
            UUID id,
            UUID workoutExerciseId
    );

    Optional<WorkoutSet> findByWorkoutExerciseIdAndClientId(
            UUID workoutExerciseId,
            UUID clientId
    );

    List<WorkoutSet> findByWorkoutExerciseIdInOrderByWorkoutExerciseIdAscSetNumberAsc(
            List<UUID> workoutExerciseIds
    );

    boolean existsByWorkoutExerciseIdAndSetNumber(
            UUID workoutExerciseId,
            int setNumber
    );
}
