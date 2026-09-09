package dev.manuel.gymtracker_api.workout.repository;

import dev.manuel.gymtracker_api.workout.model.WorkoutExercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkoutExerciseRepository
        extends JpaRepository<WorkoutExercise, UUID> {

    List<WorkoutExercise> findByWorkoutIdOrderByPositionAsc(
            UUID workoutId
    );
}