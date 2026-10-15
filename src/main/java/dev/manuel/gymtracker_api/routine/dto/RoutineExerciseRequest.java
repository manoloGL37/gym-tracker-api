package dev.manuel.gymtracker_api.routine.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record RoutineExerciseRequest(

        @NotNull
        UUID exerciseId,

        @Min(0)
        int position,

        @Min(1)
        int sets,

        @Min(1)
        Integer targetReps,

        @Min(0)
        int restSeconds,

        @Size(max = 500)
        String notes,
        @Min(1) Integer targetDurationSeconds

) {
    public RoutineExerciseRequest(UUID exerciseId, int position, int sets, int targetReps, int restSeconds, String notes) {
        this(exerciseId, position, sets, targetReps, restSeconds, notes, null);
    }
}