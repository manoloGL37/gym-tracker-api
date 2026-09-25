package dev.manuel.gymtracker_api.workout.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "workout_exercises")
@Getter
@Setter
@NoArgsConstructor
public class WorkoutExercise {

    @Id
    private UUID id;

    @Column(name = "workout_id", nullable = false)
    private UUID workoutId;

    @Column(name = "exercise_id")
    private UUID exerciseId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "exercise_name_snapshot", length = 255)
    private String exerciseNameSnapshot;

    @Column(nullable = false)
    private int position;

    @Column(length = 500)
    private String notes;
}
