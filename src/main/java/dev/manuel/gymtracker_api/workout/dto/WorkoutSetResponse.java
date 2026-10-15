package dev.manuel.gymtracker_api.workout.dto;

import dev.manuel.gymtracker_api.workout.model.SideResult;

import java.math.BigDecimal;
import java.util.UUID;

public record WorkoutSetResponse(
        UUID id,
        UUID clientId,
        int setNumber,
        BigDecimal weight,
        Integer reps,
        BigDecimal rpe,
        Integer durationSeconds,
        boolean completed,
        SideResult left,
        SideResult right,
        long version
) {}
