package dev.manuel.gymtracker_api.statistics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StatisticsEvolutionPoint(
        LocalDate date,
        long workouts,
        long sets,
        long reps,
        BigDecimal volume,
        BigDecimal maxWeight
) {}