package dev.manuel.gymtracker_api.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class RefreshCookieService {

    private final String cookieName;
    private final boolean secure;
    private final String sameSite;

    public RefreshCookieService(
            @Value("${app.auth.refresh-cookie-name}") String cookieName,
            @Value("${app.auth.cookie-secure}") boolean secure,
            @Value("${app.auth.cookie-same-site}") String sameSite
    ) {
        if ("None".equalsIgnoreCase(sameSite) && !secure) {
            throw new IllegalArgumentException("SameSite=None refresh cookies must be Secure");
        }
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = switch (sameSite.toLowerCase()) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            case "none" -> "None";
            default -> throw new IllegalArgumentException("Refresh cookie SameSite must be Strict, Lax, or None");
        };
    }

    public String cookieName() {
        return cookieName;
    }

    public ResponseCookie create(String token, Instant expiresAt) {
        long maxAge = Math.max(0, Duration.between(Instant.now(), expiresAt).toSeconds());
        return baseCookie(token).maxAge(maxAge).build();
    }

    public ResponseCookie clear() {
        return baseCookie("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api/auth");
    }
}
