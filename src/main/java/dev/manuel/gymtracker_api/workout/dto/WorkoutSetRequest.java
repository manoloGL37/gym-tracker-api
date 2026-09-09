package dev.manuel.gymtracker_api.workout.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WorkoutSetRequest(

        @Min(1)
        int setNumber,

        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("9999.99")
        BigDecimal weight,

        @Min(1)
        int reps,

        @DecimalMin("0.0")
        @DecimalMax("10.0")
        BigDecimal rpe

) {}