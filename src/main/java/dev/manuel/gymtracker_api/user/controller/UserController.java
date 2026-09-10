package dev.manuel.gymtracker_api.user.controller;

import dev.manuel.gymtracker_api.user.dto.CreateUserRequest;
import dev.manuel.gymtracker_api.user.dto.UserResponse;
import dev.manuel.gymtracker_api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User registration and profile retrieval.")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a user", description = "Creates a user account. This endpoint is public.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "409", description = "Email address already exists")
    })
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user profile"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    public UserResponse getCurrentUser(
            @AuthenticationPrincipal UUID userId) {
        return userService.getUserById(userId);
    }
}
