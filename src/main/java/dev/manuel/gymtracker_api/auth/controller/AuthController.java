package dev.manuel.gymtracker_api.auth.controller;

import dev.manuel.gymtracker_api.auth.dto.AuthResponse;
import dev.manuel.gymtracker_api.auth.dto.LoginRequest;
import dev.manuel.gymtracker_api.auth.service.AuthService;
import dev.manuel.gymtracker_api.auth.service.AuthSession;
import dev.manuel.gymtracker_api.auth.service.RefreshCookieService;
import dev.manuel.gymtracker_api.auth.exception.InvalidRefreshTokenException;
import dev.manuel.gymtracker_api.config.GlobalExceptionHandler.ErrorResponse;
import dev.manuel.gymtracker_api.config.GlobalExceptionHandler.ValidationErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Login, refresh-token rotation, and logout.")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieService refreshCookieService;

    public AuthController(AuthService authService, RefreshCookieService refreshCookieService) {
        this.authService = authService;
        this.refreshCookieService = refreshCookieService;
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in", description = "Validates user credentials and returns a JWT access token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload", content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        return writeSession(authService.login(request), response);
    }

    @PostMapping("/refresh")
    @SecurityRequirement(name = "refreshCookie")
    @Operation(summary = "Refresh access token", description = "Rotates the HttpOnly refresh cookie and returns a new access JWT. No request body is accepted.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refresh successful"),
            @ApiResponse(responseCode = "401", description = "Refresh session invalid, expired, revoked, or reused", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AuthResponse refresh(
            @CookieValue(name = "${app.auth.refresh-cookie-name}", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        try {
            return writeSession(authService.refresh(refreshToken), response);
        } catch (InvalidRefreshTokenException exception) {
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookieService.clear().toString());
            throw exception;
        }
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "refreshCookie")
    @Operation(summary = "Log out", description = "Revokes the refresh-token family when present and always clears the refresh cookie.")
    @ApiResponse(responseCode = "204", description = "Logged out; response has no body")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${app.auth.refresh-cookie-name}", required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, refreshCookieService.clear().toString())
                .build();
    }

    private AuthResponse writeSession(AuthSession session, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshCookieService.create(session.refreshToken(), session.refreshExpiresAt()).toString()
        );
        return session.response();
    }
}
