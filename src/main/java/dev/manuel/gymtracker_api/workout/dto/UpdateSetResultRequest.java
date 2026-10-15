package dev.manuel.gymtracker_api.workout.dto;

import dev.manuel.gymtracker_api.workout.model.Side;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record UpdateSetResultRequest(
        @NotNull @Min(0) Long expectedVersion,
        @NotNull java.util.UUID clientMutationId,
        Side side,
        @Min(1) Integer repetitions,
        @Min(1) Integer durationSeconds,
        @DecimalMin("0") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2) BigDecimal additionalWeightKg,
        Boolean clearAdditionalWeight,
        Boolean completed
) {}
