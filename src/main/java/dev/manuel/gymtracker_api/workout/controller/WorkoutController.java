package dev.manuel.gymtracker_api.workout.controller;

import dev.manuel.gymtracker_api.workout.dto.CreateWorkoutRequest;
import dev.manuel.gymtracker_api.workout.dto.UpdateWorkoutRequest;
import dev.manuel.gymtracker_api.workout.dto.WorkoutResponse;
import dev.manuel.gymtracker_api.workout.dto.WorkoutSetRequest;
import dev.manuel.gymtracker_api.workout.dto.WorkoutSetResponse;
import dev.manuel.gymtracker_api.workout.service.WorkoutService;
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
public class WorkoutController {

    private final WorkoutService workoutService;

    public WorkoutController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    @PostMapping("/{workoutId}/exercises/{workoutExerciseId}/sets")
    @ResponseStatus(HttpStatus.CREATED)
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
    public WorkoutResponse getWorkout(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return workoutService.getWorkoutById(userId, id);
    }

    @GetMapping
    public Page<WorkoutResponse> getWorkouts(
            @AuthenticationPrincipal UUID userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return workoutService.getWorkouts(userId, pageable);
    }

    @PatchMapping("/{id}")
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
    public WorkoutResponse createWorkout(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateWorkoutRequest request) {
        return workoutService.createWorkout(userId, request);
    }
}