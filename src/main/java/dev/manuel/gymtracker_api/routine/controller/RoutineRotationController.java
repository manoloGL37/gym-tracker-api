package dev.manuel.gymtracker_api.routine.controller;

import dev.manuel.gymtracker_api.routine.dto.RoutineRotation;
import dev.manuel.gymtracker_api.routine.service.RoutineRotationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/routine-rotation")
@Tag(name = "Routine rotation", description = "Account-scoped ordered training cycle, independent of weekdays.")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
public class RoutineRotationController {
    private final RoutineRotationService service;

    public RoutineRotationController(RoutineRotationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get routine rotation", description = "Returns ordered routine IDs; an unconfigured account receives an empty list.")
    @ApiResponse(responseCode = "200", description = "Current rotation, including an empty rotation")
    public RoutineRotation get(@AuthenticationPrincipal UUID userId) {
        return service.get(userId);
    }

    @PutMapping
    @Operation(summary = "Replace entire routine rotation", description = "Atomic, idempotent full replacement. Omission excludes a routine. Empty clears the rotation. Last successful write wins; retries can overwrite newer edits. Creation never inserts routines automatically; deletion removes them. No next-session calculation.")
    @ApiResponse(responseCode = "200", description = "Persisted ordered rotation")
    @ApiResponse(responseCode = "400", description = "Missing/null list, null/malformed UUID, or duplicate IDs")
    @ApiResponse(responseCode = "404", description = "A routine is missing, deleted or owned by another account; no changes applied")
    public RoutineRotation replace(@AuthenticationPrincipal UUID userId,
                                   @Valid @RequestBody RoutineRotation request) {
        return service.replace(userId, request);
    }
}
