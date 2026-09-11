package dev.manuel.gymtracker_api.exercise.controller;

import dev.manuel.gymtracker_api.exercise.dto.CreateExerciseRequest;
import dev.manuel.gymtracker_api.exercise.dto.ExercisePageResponse;
import dev.manuel.gymtracker_api.exercise.dto.ExerciseFilterOptionsResponse;
import dev.manuel.gymtracker_api.exercise.dto.ExerciseResponse;
import dev.manuel.gymtracker_api.exercise.service.ExerciseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/exercises")
@Tag(name = "Exercises", description = "Global exercise catalog and user-owned custom exercises.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an exercise")
    @ApiResponse(responseCode = "404", description = "Exercise not found")
    public ExerciseResponse getExerciseById(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return exerciseService.getExerciseById(id, userId);
    }

    @GetMapping("/filter-options")
    @Operation(summary = "Get available exercise filter options")
    @ApiResponse(responseCode = "200", description = "Visible exercise metadata values for filters")
    public ExerciseFilterOptionsResponse getFilterOptions(
            @AuthenticationPrincipal UUID userId) {
        return exerciseService.getFilterOptions(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a custom exercise")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Exercise created"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
    public ExerciseResponse createExercise(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateExerciseRequest request) {
        return exerciseService.createExercise(userId, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a custom exercise")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "404", description = "Exercise not found")
    })
    public ExerciseResponse updateExercise(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody CreateExerciseRequest request) {
        return exerciseService.updateExercise(
                id,
                userId,
                request);
    }

    @GetMapping
    @Operation(summary = "List available exercises", description = "Returns global exercises and the authenticated user's custom exercises with optional filters and pagination.")
    @ApiResponse(responseCode = "200", description = "Paginated exercise catalog")
    public ExercisePageResponse getExercises(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String equipment,
            @RequestParam(required = false) String muscleGroup,
            @RequestParam(required = false) String targetMuscle,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(page, size);

        return exerciseService.getAvailableExercises(
                userId,
                search,
                category,
                equipment,
                muscleGroup,
                targetMuscle,
                pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a custom exercise")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Exercise deleted"),
            @ApiResponse(responseCode = "404", description = "Exercise not found")
    })
    public void deleteExercise(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        exerciseService.deleteExercise(id, userId);
    }
}
