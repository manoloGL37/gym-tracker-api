package dev.manuel.gymtracker_api.statistics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExerciseStatisticsResponse(
        UUID exerciseId,
        LocalDate from,
        LocalDate to,
        long totalSets,
        long totalReps,
        BigDecimal totalVolume,
        BigDecimal maxWeight,
        List<ExerciseStatisticsEvolutionPoint> evolution
) {}