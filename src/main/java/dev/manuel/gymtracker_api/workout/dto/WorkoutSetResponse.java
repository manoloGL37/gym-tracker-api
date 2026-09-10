package dev.manuel.gymtracker_api.workout.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkoutSetResponse(
        UUID id,
        UUID clientId,
        int setNumber,
        BigDecimal weight,
        int reps,
        BigDecimal rpe
) {}
