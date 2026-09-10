package dev.manuel.gymtracker_api.workout.controller;

import dev.manuel.gymtracker_api.workout.dto.CreateWorkoutRequest;
import dev.manuel.gymtracker_api.workout.dto.UpdateWorkoutRequest;
import dev.manuel.gymtracker_api.workout.dto.WorkoutResponse;
import dev.manuel.gymtracker_api.workout.dto.WorkoutSetRequest;
import dev.manuel.gymtracker_api.workout.dto.WorkoutSetResponse;
import dev.manuel.gymtracker_api.workout.service.WorkoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/workouts")
@Tag(name = "Workouts", description = "Recorded workout sessions and their performed sets.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
public class WorkoutController {

    private final WorkoutService workoutService;

    public WorkoutController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    @PostMapping("/{workoutId}/exercises/{workoutExerciseId}/sets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a set to a workout exercise")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Workout set created"),
            @ApiResponse(responseCode = "400", description = "Invalid request or duplicate set number"),
            @ApiResponse(responseCode = "404", description = "Workout or workout exercise not found")
    })
    public WorkoutSetResponse createWorkoutSet(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID workoutId,
            @PathVariable UUID workoutExerciseId,
            @Valid @RequestBody WorkoutSetRequest request) {
        return workoutService.createWorkoutSet(
                userId,
                workoutId,
                workoutExerciseId,
                request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a workout")
    @ApiResponse(responseCode = "404", description = "Workout not found")
    public WorkoutResponse getWorkout(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return workoutService.getWorkoutById(userId, id);
    }

    @GetMapping
    @Operation(summary = "List workouts", description = "Returns workout sessions ordered by creation date, with pagination.")
    @ApiResponse(responseCode = "200", description = "Paginated workouts")
    public Page<WorkoutResponse> getWorkouts(
            @AuthenticationPrincipal UUID userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return workoutService.getWorkouts(userId, pageable);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a workout")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workout updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "404", description = "Workout not found")
    })
    public WorkoutResponse updateWorkout(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWorkoutRequest request) {
        return workoutService.updateWorkout(
                userId,
                id,
                request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a workout")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Workout created"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "404", description = "Routine not found")
    })
    public WorkoutResponse createWorkout(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateWorkoutRequest request) {
        return workoutService.createWorkout(userId, request);
    }
}
