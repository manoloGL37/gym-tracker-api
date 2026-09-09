package dev.manuel.gymtracker_api.routine.controller;

import dev.manuel.gymtracker_api.routine.dto.CreateRoutineRequest;
import dev.manuel.gymtracker_api.routine.dto.RoutineResponse;
import dev.manuel.gymtracker_api.routine.dto.RoutineSummaryResponse;
import dev.manuel.gymtracker_api.routine.service.RoutineService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@RestController
@RequestMapping("/api/routines")
public class RoutineController {

    private final RoutineService routineService;

    public RoutineController(
            RoutineService routineService
    ) {
        this.routineService = routineService;
    }

    @GetMapping("/{id}")
    public RoutineResponse getRoutine(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id
    ) {
        return routineService.getRoutineById(
                userId,
                id
        );
    }

    @GetMapping
    public Page<RoutineSummaryResponse> getRoutines(
            @AuthenticationPrincipal UUID userId,
            @PageableDefault(
                    size = 10,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        return routineService.getRoutines(
                userId,
                pageable
        );
    }

    @PutMapping("/{id}")
    public RoutineResponse updateRoutine(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody CreateRoutineRequest request
    ) {
        return routineService.updateRoutine(
                userId,
                id,
                request
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoutineResponse createRoutine(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody CreateRoutineRequest request
    ) {
        return routineService.createRoutine(
                userId,
                request
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoutine(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id
    ) {
        routineService.deleteRoutine(
                userId,
                id
        );
    }
}