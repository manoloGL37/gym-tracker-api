package dev.manuel.gymtracker_api.routine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "routine_exercises")
@Getter
@Setter
@NoArgsConstructor
public class RoutineExercise {

    @Id
    private UUID id;

    @Column(name = "routine_id", nullable = false)
    private UUID routineId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private int sets;

    @Column(name = "target_reps", nullable = false)
    private int targetReps;

    @Column(name = "rest_seconds", nullable = false)
    private int restSeconds;

    @Column(length = 500)
    private String notes;
}