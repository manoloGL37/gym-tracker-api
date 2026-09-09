package dev.manuel.gymtracker_api.workout.dto;

import jakarta.validation.constraints.Size;

public record UpdateWorkoutRequest(

        Boolean completed,

        @Size(max = 500)
        String notes

) {}