package dev.manuel.gymtracker_api.statistics.controller;

import dev.manuel.gymtracker_api.statistics.dto.ExerciseStatisticsResponse;
import dev.manuel.gymtracker_api.statistics.dto.StatisticsComparisonResponse;
import dev.manuel.gymtracker_api.statistics.dto.StatisticsEvolutionResponse;
import dev.manuel.gymtracker_api.statistics.dto.StatisticsSummaryResponse;
import dev.manuel.gymtracker_api.statistics.service.StatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(
            StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/summary")
    public StatisticsSummaryResponse getSummary(
            @AuthenticationPrincipal UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statisticsService.getSummary(
                userId,
                from,
                to);
    }

    @GetMapping("/comparison")
    public StatisticsComparisonResponse getComparison(
            @AuthenticationPrincipal UUID userId,

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentFrom,

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentTo,

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate previousFrom,

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate previousTo) {
        return statisticsService.getComparison(
                userId,
                currentFrom,
                currentTo,
                previousFrom,
                previousTo);
    }

    @GetMapping("/evolution")
    public StatisticsEvolutionResponse getEvolution(
            @AuthenticationPrincipal UUID userId,

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statisticsService.getEvolution(
                userId,
                from,
                to);
    }

    @GetMapping("/exercises/{exerciseId}")
    public ExerciseStatisticsResponse getExerciseStatistics(
            @AuthenticationPrincipal UUID userId,

            @PathVariable UUID exerciseId,

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,

            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return statisticsService.getExerciseStatistics(
                userId,
                exerciseId,
                from,
                to);
    }
}