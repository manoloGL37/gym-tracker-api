package dev.manuel.gymtracker_api.statistics.dto;

import java.time.LocalDate;
import java.util.List;

public record StatisticsEvolutionResponse(
        LocalDate from,
        LocalDate to,
        List<StatisticsEvolutionPoint> data
) {}