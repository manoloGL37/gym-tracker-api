package dev.manuel.gymtracker_api.statistics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExerciseStatisticsEvolutionPoint(
        LocalDate date,
        BigDecimal volume,
        BigDecimal maxWeight
) {}