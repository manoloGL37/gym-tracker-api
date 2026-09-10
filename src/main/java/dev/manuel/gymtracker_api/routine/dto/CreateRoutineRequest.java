package dev.manuel.gymtracker_api.routine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateRoutineRequest(

        UUID clientId,

        @NotBlank
        @Size(max = 150)
        String name,

        @Size(max = 500)
        String description,

        @Size(max = 50)
        @jakarta.validation.constraints.NotNull
        List<@Valid RoutineExerciseRequest> exercises

) {}
