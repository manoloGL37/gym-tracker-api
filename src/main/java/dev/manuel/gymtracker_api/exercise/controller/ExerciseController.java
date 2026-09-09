package dev.manuel.gymtracker_api.exercise.controller;

import dev.manuel.gymtracker_api.exercise.dto.CreateExerciseRequest;
import dev.manuel.gymtracker_api.exercise.dto.ExercisePageResponse;
import dev.manuel.gymtracker_api.exercise.dto.ExerciseResponse;
import dev.manuel.gymtracker_api.exercise.service.ExerciseService;
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
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping("/{id}")
    public ExerciseResponse getExerciseById(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return exerciseService.getExerciseById(id, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExerciseResponse createExercise(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateExerciseRequest request) {
        return exerciseService.createExercise(userId, request);
    }

    @PutMapping("/{id}")
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
    public void deleteExercise(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        exerciseService.deleteExercise(id, userId);
    }
}