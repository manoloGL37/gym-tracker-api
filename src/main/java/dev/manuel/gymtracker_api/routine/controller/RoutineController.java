package dev.manuel.gymtracker_api.routine.controller;

import dev.manuel.gymtracker_api.routine.dto.CreateRoutineRequest;
import dev.manuel.gymtracker_api.routine.dto.RoutineResponse;
import dev.manuel.gymtracker_api.routine.dto.RoutineSummaryResponse;
import dev.manuel.gymtracker_api.routine.service.RoutineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Routines", description = "Authenticated user's planned workout routines.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
public class RoutineController {

    private final RoutineService routineService;

    public RoutineController(
            RoutineService routineService
    ) {
        this.routineService = routineService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a routine")
    @ApiResponse(responseCode = "404", description = "Routine not found")
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
    @Operation(summary = "List routines", description = "Returns routines ordered by creation date, with pagination.")
    @ApiResponse(responseCode = "200", description = "Paginated routine summaries")
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
    @Operation(summary = "Update a routine")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Routine updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "404", description = "Routine not found")
    })
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
    @Operation(summary = "Create a routine")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Routine created"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload")
    })
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
    @Operation(summary = "Delete a routine")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Routine deleted"),
            @ApiResponse(responseCode = "404", description = "Routine not found")
    })
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
