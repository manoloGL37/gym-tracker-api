package dev.manuel.gymtracker_api.exercise.controller;

import dev.manuel.gymtracker_api.exercise.dto.ExercisePageResponse;
import dev.manuel.gymtracker_api.exercise.service.ExerciseService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping
    public ExercisePageResponse getExercises(
            @RequestParam UUID userId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String equipment,
            @RequestParam(required = false) String muscleGroup,
            @RequestParam(required = false) String targetMuscle,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);

        return exerciseService.getAvailableExercises(
                userId,
                search,
                category,
                equipment,
                muscleGroup,
                targetMuscle,
                pageable
        );
    }
}