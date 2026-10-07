package dev.manuel.gymtracker_api.routine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record RoutineRotation(
        @NotNull
        @Schema(description = "Ordered server routine IDs. Empty is valid; omitted routines are outside the rotation.")
        List<@NotNull UUID> routineIds
) {}
