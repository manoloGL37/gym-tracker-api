package dev.manuel.gymtracker_api.statistics.dto;

public record StatisticsComparisonResponse(
        StatisticsSummaryResponse current,
        StatisticsSummaryResponse previous,
        StatisticsChanges changes
) {

    public record StatisticsChanges(
            double workouts,
            double sets,
            double reps,
            double volume,
            double maxWeight
    ) {}
}