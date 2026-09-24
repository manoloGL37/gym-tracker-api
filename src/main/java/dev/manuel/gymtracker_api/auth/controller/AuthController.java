package dev.manuel.gymtracker_api.auth.controller;

import dev.manuel.gymtracker_api.auth.dto.AuthResponse;
import dev.manuel.gymtracker_api.auth.dto.LoginRequest;
import dev.manuel.gymtracker_api.auth.dto.MobileAuthResponse;
import dev.manuel.gymtracker_api.auth.dto.MobileRefreshRequest;
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

    @PostMapping("/mobile/login")
    @Operation(summary = "Native mobile sign in", description = "Returns an access JWT and an explicit opaque refresh credential. Does not set a refresh cookie.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload", content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MobileAuthResponse> mobileLogin(@Valid @RequestBody LoginRequest request) {
        return mobileSession(authService.login(request));
    }

    @PostMapping("/mobile/refresh")
    @Operation(summary = "Rotate native mobile refresh credential", description = "Reads the credential from JSON, rotates within its existing family, and returns both new tokens. No cookie is read or set.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refresh successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload", content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid, expired, revoked, or reused refresh credential", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MobileAuthResponse> mobileRefresh(@Valid @RequestBody MobileRefreshRequest request) {
        return mobileSession(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/mobile/logout")
    @Operation(summary = "Revoke native mobile session", description = "Revokes all active refresh credentials in the supplied token's family; safe to retry. No cookie is read or set.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session revoked or already absent"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload", content = @Content(schema = @Schema(implementation = ValidationErrorResponse.class)))
    })
    public ResponseEntity<Void> mobileLogout(@Valid @RequestBody MobileRefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
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

    private ResponseEntity<MobileAuthResponse> mobileSession(AuthSession session) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(MobileAuthResponse.from(session));
    }
}
