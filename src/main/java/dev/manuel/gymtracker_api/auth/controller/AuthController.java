package dev.manuel.gymtracker_api.auth.controller;

import dev.manuel.gymtracker_api.auth.dto.AuthResponse;
import dev.manuel.gymtracker_api.auth.dto.LoginRequest;
import dev.manuel.gymtracker_api.auth.service.AuthService;
import dev.manuel.gymtracker_api.config.GlobalExceptionHandler.ErrorResponse;
import dev.manuel.gymtracker_api.config.GlobalExceptionHandler.ValidationErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Public endpoints for obtaining access tokens.")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in", description = "Validates user credentials and returns a JWT access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload", content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }
}
