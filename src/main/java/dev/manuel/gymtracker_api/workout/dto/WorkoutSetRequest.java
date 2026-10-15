package dev.manuel.gymtracker_api.workout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import dev.manuel.gymtracker_api.workout.model.SideResult;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.UUID;

public record WorkoutSetRequest(

        UUID clientId,

        @Min(1)
        int setNumber,

        @Digits(integer = 4, fraction = 2)
        @DecimalMin("0.00")
        @DecimalMax("9999.99")
        BigDecimal weight,

        @Min(1)
        Integer reps,

        @DecimalMin("0.0")
        @DecimalMax("10.0")
        BigDecimal rpe,
        @Min(1) Integer durationSeconds,
        Boolean completed,
        @Valid SideResult left,
        @Valid SideResult right

) {
    public WorkoutSetRequest {
        // ponytail: normalize redundant zeros only; @Digits still rejects real sub-cent precision.
        if (weight != null && weight.scale() > 2 && weight.stripTrailingZeros().scale() <= 2)
            weight = weight.setScale(2);
    }
}
