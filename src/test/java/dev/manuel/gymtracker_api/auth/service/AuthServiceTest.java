package dev.manuel.gymtracker_api.auth.service;

import dev.manuel.gymtracker_api.auth.dto.AuthResponse;
import dev.manuel.gymtracker_api.auth.dto.LoginRequest;
import dev.manuel.gymtracker_api.auth.exception.InvalidCredentialsException;
import dev.manuel.gymtracker_api.auth.security.JwtService;
import dev.manuel.gymtracker_api.user.model.User;
import dev.manuel.gymtracker_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService
        );
    }

    @Test
    void shouldRejectLoginWhenUserDoesNotExist() {
        LoginRequest request =
                new LoginRequest(
                        "unknown@example.com",
                        "password123"
                );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        verify(passwordEncoder, never())
                .matches(anyString(), anyString());

        verify(jwtService, never())
                .generateToken(any());
    }

    @Test
    void shouldRejectLoginWhenPasswordIsIncorrect() {
        UUID userId = UUID.randomUUID();

        User user = createUser(
                userId,
                "manuel@example.com",
                "$2a$10$hashed-password"
        );

        LoginRequest request =
                new LoginRequest(
                        "manuel@example.com",
                        "wrong-password"
                );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )).thenReturn(false);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        verify(jwtService, never())
                .generateToken(any());
    }

    @Test
    void shouldLoginSuccessfully() {
        UUID userId = UUID.randomUUID();

        User user = createUser(
                userId,
                "manuel@example.com",
                "$2a$10$hashed-password"
        );

        LoginRequest request =
                new LoginRequest(
                        "manuel@example.com",
                        "correct-password"
                );

        String token = "generated-jwt-token";

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )).thenReturn(true);

        when(jwtService.generateToken(userId))
                .thenReturn(token);

        AuthResponse response =
                authService.login(request);

        assertNotNull(response);
        assertEquals(token, response.accessToken());

        verify(jwtService)
                .generateToken(userId);
    }

    @Test
    void shouldUseStoredPasswordHashForVerification() {
        UUID userId = UUID.randomUUID();

        String storedHash = "$2a$10$stored-hash";

        User user = createUser(
                userId,
                "manuel@example.com",
                storedHash
        );

        LoginRequest request =
                new LoginRequest(
                        "manuel@example.com",
                        "plain-password"
                );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                request.password(),
                storedHash
        )).thenReturn(true);

        when(jwtService.generateToken(userId))
                .thenReturn("token");

        authService.login(request);

        verify(passwordEncoder)
                .matches("plain-password", storedHash);
    }

    private User createUser(
            UUID id,
            String email,
            String passwordHash
    ) {
        User user = new User();

        user.setId(id);
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        return user;
    }
}