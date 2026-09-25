package dev.manuel.gymtracker_api.workout.controller;

import dev.manuel.gymtracker_api.workout.dto.CreateWorkoutRequest;
import dev.manuel.gymtracker_api.workout.dto.CreateMobileWorkoutRequest;
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
    @Operation(summary = "Get a workout", description = "Returns persisted historical names, ordered exercises and sets, notes, client/server IDs, and exact instants/zone for mobile workouts. Legacy instant and snapshot names may be null when unrecoverable; local timestamps are not assumed UTC.")
    @ApiResponse(responseCode = "404", description = "Workout not found")
    public WorkoutResponse getWorkout(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return workoutService.getWorkoutById(userId, id);
    }

    @GetMapping
    @Operation(summary = "List workouts", description = "Returns caller workouts ordered by creation date with pagination. Existing exercise/set graph remains for browser compatibility; nameSnapshot and exerciseNameSnapshot are persisted historical display text.")
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
    @Operation(summary = "Create a workout", description = "Browser contract: copies the current caller-owned routine exercise rows and available display names into a workout snapshot. Retrying the same user/clientId returns the existing workout.")
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

    @PostMapping("/mobile")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Import the performed mobile workout snapshot", description = "The submitted name, ordered exercises and completed sets are authoritative. Source routine/exercise IDs are optional owned references, including soft-deleted sources. Instants are exact UTC/offset values and calendarZone is an IANA zone for local dates. The same user/clientId returns the existing complete workout without duplicating children.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Workout created or existing clientId returned"),
            @ApiResponse(responseCode = "400", description = "Invalid snapshot, instant, zone, time order or duplicate position/clientId/set number"),
            @ApiResponse(responseCode = "404", description = "Referenced routine or exercise absent or not owned/visible")
    })
    public WorkoutResponse createMobileWorkout(@AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateMobileWorkoutRequest request) {
        return workoutService.createMobileWorkout(userId, request);
    }
}
