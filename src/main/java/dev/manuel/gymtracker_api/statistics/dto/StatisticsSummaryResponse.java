package dev.manuel.gymtracker_api.statistics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatisticsSummaryResponse(
        LocalDate from,
        LocalDate to,
        long workouts,
        long sets,
        long reps,
        BigDecimal volume,
        BigDecimal maxWeight
) {}