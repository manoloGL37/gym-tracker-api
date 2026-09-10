package dev.manuel.gymtracker_api.statistics.controller;

import dev.manuel.gymtracker_api.statistics.dto.ExerciseStatisticsResponse;
import dev.manuel.gymtracker_api.statistics.dto.StatisticsComparisonResponse;
import dev.manuel.gymtracker_api.statistics.dto.StatisticsEvolutionResponse;
import dev.manuel.gymtracker_api.statistics.dto.StatisticsSummaryResponse;
import dev.manuel.gymtracker_api.statistics.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Statistics", description = "Training metrics for the authenticated user.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
public class StatisticsController {

    private final StatisticsService statisticsService;

    public StatisticsController(
            StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get a training summary", description = "Aggregates workout metrics for an inclusive date range.")
    @ApiResponse(responseCode = "200", description = "Training summary")
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
    @Operation(summary = "Compare training periods")
    @ApiResponse(responseCode = "200", description = "Comparison between two date ranges")
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
    @Operation(summary = "Get training evolution", description = "Returns metrics over the requested date range.")
    @ApiResponse(responseCode = "200", description = "Training evolution")
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
    @Operation(summary = "Get exercise statistics", description = "Returns performance statistics for one exercise in the requested date range.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise statistics, including zero-valued aggregates when no matching data exists")
    })
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
