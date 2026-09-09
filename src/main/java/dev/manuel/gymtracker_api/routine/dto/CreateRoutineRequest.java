package dev.manuel.gymtracker_api.routine.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRoutineRequest(

        @NotBlank
        @Size(max = 150)
        String name,

        @Size(max = 500)
        String description,

        @Size(max = 50)
        List<@Valid RoutineExerciseRequest> exercises

) {}