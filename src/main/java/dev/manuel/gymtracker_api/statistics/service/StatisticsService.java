package dev.manuel.gymtracker_api.statistics.service;

import dev.manuel.gymtracker_api.statistics.dto.ExerciseStatisticsEvolutionPoint;
import dev.manuel.gymtracker_api.statistics.dto.ExerciseStatisticsResponse;
import dev.manuel.gymtracker_api.statistics.dto.StatisticsComparisonResponse;
import dev.manuel.gymtracker_api.statistics.dto.StatisticsEvolutionPoint;
import dev.manuel.gymtracker_api.statistics.dto.StatisticsEvolutionResponse;
import dev.manuel.gymtracker_api.statistics.dto.StatisticsSummaryResponse;
import dev.manuel.gymtracker_api.statistics.repository.StatisticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class StatisticsService {

    private final StatisticsRepository statisticsRepository;

    public StatisticsService(
            StatisticsRepository statisticsRepository) {
        this.statisticsRepository = statisticsRepository;
    }

    @Transactional(readOnly = true)
    public StatisticsSummaryResponse getSummary(
            UUID userId,
            LocalDate from,
            LocalDate to) {

        if (to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "'to' date must not be before 'from' date");
        }

        LocalDateTime fromDateTime = from.atStartOfDay();

        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        StatisticsRepository.StatisticsSummaryProjection summary = statisticsRepository.getSummary(
                userId,
                fromDateTime,
                toDateTime);

        return new StatisticsSummaryResponse(
                from,
                to,
                summary.workouts(),
                summary.sets(),
                summary.reps(),
                summary.volume(),
                summary.maxWeight());
    }

    @Transactional(readOnly = true)
    public StatisticsComparisonResponse getComparison(
            UUID userId,
            LocalDate currentFrom,
            LocalDate currentTo,
            LocalDate previousFrom,
            LocalDate previousTo) {
        validateDateRange(currentFrom, currentTo);
        validateDateRange(previousFrom, previousTo);

        StatisticsSummaryResponse current = getSummary(userId, currentFrom, currentTo);

        StatisticsSummaryResponse previous = getSummary(userId, previousFrom, previousTo);

        return new StatisticsComparisonResponse(
                current,
                previous,
                new StatisticsComparisonResponse.StatisticsChanges(
                        calculatePercentageChange(
                                previous.workouts(),
                                current.workouts()),
                        calculatePercentageChange(
                                previous.sets(),
                                current.sets()),
                        calculatePercentageChange(
                                previous.reps(),
                                current.reps()),
                        calculatePercentageChange(
                                previous.volume(),
                                current.volume()),
                        calculatePercentageChange(
                                previous.maxWeight(),
                                current.maxWeight())));
    }

    private double calculatePercentageChange(
            Number previous,
            Number current) {
        double previousValue = previous.doubleValue();
        double currentValue = current.doubleValue();

        if (previousValue == 0) {
            if (currentValue == 0) {
                return 0.0;
            }

            return 100.0;
        }

        return ((currentValue - previousValue) / previousValue) * 100.0;
    }

    private void validateDateRange(
            LocalDate from,
            LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "'to' date must not be before 'from' date");
        }
    }

    @Transactional(readOnly = true)
    public StatisticsEvolutionResponse getEvolution(
            UUID userId,
            LocalDate from,
            LocalDate to) {
        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();

        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        List<StatisticsEvolutionPoint> points = statisticsRepository
                .getEvolution(
                        userId,
                        fromDateTime,
                        toDateTime)
                .stream()
                .map(point -> new StatisticsEvolutionPoint(
                        point.date(),
                        point.workouts(),
                        point.sets(),
                        point.reps(),
                        point.volume(),
                        point.maxWeight()))
                .toList();

        return new StatisticsEvolutionResponse(
                from,
                to,
                points);
    }

    @Transactional(readOnly = true)
    public ExerciseStatisticsResponse getExerciseStatistics(
            UUID userId,
            UUID exerciseId,
            LocalDate from,
            LocalDate to) {
        validateDateRange(from, to);

        LocalDateTime fromDateTime = from.atStartOfDay();
        LocalDateTime toDateTime = to.plusDays(1).atStartOfDay();

        StatisticsRepository.ExerciseStatisticsSummaryProjection summary = statisticsRepository.getExerciseSummary(
                userId,
                exerciseId,
                fromDateTime,
                toDateTime);

        List<ExerciseStatisticsEvolutionPoint> evolution = statisticsRepository.getExerciseEvolution(
                userId,
                exerciseId,
                fromDateTime,
                toDateTime)
                .stream()
                .map(point -> new ExerciseStatisticsEvolutionPoint(
                        point.date(),
                        point.volume(),
                        point.maxWeight()))
                .toList();

        return new ExerciseStatisticsResponse(
                exerciseId,
                from,
                to,
                summary.totalSets(),
                summary.totalReps(),
                summary.totalVolume(),
                summary.maxWeight(),
                evolution);
    }
}