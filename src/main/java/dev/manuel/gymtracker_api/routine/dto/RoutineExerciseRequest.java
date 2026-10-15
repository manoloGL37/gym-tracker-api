package dev.manuel.gymtracker_api.routine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import dev.manuel.gymtracker_api.exercise.model.Laterality;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Getter
@Accessors(fluent = true)
@NoArgsConstructor
public class RoutineExerciseRequest {
    @JsonProperty @NotNull private UUID exerciseId;
    @JsonProperty @Min(0) private int position;
    @JsonProperty @Min(1) private int sets;
    @JsonProperty @Min(1) private Integer targetReps;
    @JsonProperty @Min(0) private int restSeconds;
    @JsonProperty @Size(max = 500) private String notes;
    @JsonProperty @Min(1) private Integer targetDurationSeconds;
    @JsonProperty private Laterality lateralityOverride;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean lateralityOverridePresent;

    @JsonSetter("lateralityOverride")
    public void setLateralityOverride(Laterality value) {
        lateralityOverride = value;
        lateralityOverridePresent = true;
    }

    public RoutineExerciseRequest(UUID exerciseId, int position, int sets, Integer targetReps,
                                  int restSeconds, String notes, Integer targetDurationSeconds) {
        this.exerciseId = exerciseId;
        this.position = position;
        this.sets = sets;
        this.targetReps = targetReps;
        this.restSeconds = restSeconds;
        this.notes = notes;
        this.targetDurationSeconds = targetDurationSeconds;
    }

    public RoutineExerciseRequest(UUID exerciseId, int position, int sets, int targetReps, int restSeconds, String notes) {
        this(exerciseId, position, sets, targetReps, restSeconds, notes, null);
    }
}
