package dev.manuel.gymtracker_api.workout.model;

import jakarta.persistence.Version;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Embedded;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "workout_sets")
@Getter
@Setter
@NoArgsConstructor
public class WorkoutSet {

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "repetitions", column = @Column(name = "left_reps")),
        @AttributeOverride(name = "durationSeconds", column = @Column(name = "left_duration_seconds")),
        @AttributeOverride(name = "additionalWeightKg", column = @Column(name = "left_weight", precision = 6, scale = 2)),
        @AttributeOverride(name = "completed", column = @Column(name = "left_completed"))
    })
    private SideResult left;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "repetitions", column = @Column(name = "right_reps")),
        @AttributeOverride(name = "durationSeconds", column = @Column(name = "right_duration_seconds")),
        @AttributeOverride(name = "additionalWeightKg", column = @Column(name = "right_weight", precision = 6, scale = 2)),
        @AttributeOverride(name = "completed", column = @Column(name = "right_completed"))
    })
    private SideResult right;

    private UUID lastMutationId;

    @Id
    private UUID id;

    @Column(name = "workout_exercise_id", nullable = false)
    private UUID workoutExerciseId;

    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "set_number", nullable = false)
    private int setNumber;

    @Column(precision = 6, scale = 2)
    private BigDecimal weight;

    private Integer reps;

    private Integer durationSeconds;

    @Column(nullable = false)
    private boolean completed = true;

    @Version
    private long version;

    @Column(precision = 3, scale = 1)
    private BigDecimal rpe;
}
