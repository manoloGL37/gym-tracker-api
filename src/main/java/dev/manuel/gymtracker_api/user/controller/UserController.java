package dev.manuel.gymtracker_api.user.controller;

import dev.manuel.gymtracker_api.user.dto.CreateUserRequest;
import dev.manuel.gymtracker_api.user.dto.UserResponse;
import dev.manuel.gymtracker_api.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
}