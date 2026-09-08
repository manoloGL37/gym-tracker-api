package dev.manuel.gymtracker_api.user.controller;

import dev.manuel.gymtracker_api.user.dto.CreateUserRequest;
import dev.manuel.gymtracker_api.user.dto.UserResponse;
import dev.manuel.gymtracker_api.user.service.UserService;
import jakarta.validation.Valid;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return userService.createUser(request);
    }

    @GetMapping("/me")
    public UserResponse getCurrentUser(
            @AuthenticationPrincipal UUID userId) {
        return userService.getUserById(userId);
    }
}