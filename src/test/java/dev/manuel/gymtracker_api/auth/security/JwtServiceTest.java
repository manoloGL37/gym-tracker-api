package dev.manuel.gymtracker_api.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET =
            "una-clave-larga-y-secreta-para-desarrollo-local-123456789";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 3600000);
    }

    @Test
    void shouldGenerateValidToken() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateToken(userId);

        assertNotNull(token);
        assertTrue(jwtService.isValid(token));
    }

    @Test
    void shouldExtractUserIdFromToken() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateToken(userId);

        UUID extractedUserId =
                jwtService.extractUserId(token);

        assertEquals(userId, extractedUserId);
    }

    @Test
    void shouldRejectInvalidToken() {
        String invalidToken = "this-is-not-a-valid-jwt";

        assertFalse(jwtService.isValid(invalidToken));
    }

    @Test
    void shouldRejectTamperedToken() {
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateToken(userId);
        String tamperedToken = token + "tampered";

        assertFalse(jwtService.isValid(tamperedToken));
    }

    @Test
    void shouldRejectExpiredToken() {
        UUID userId = UUID.randomUUID();

        JwtService expiredJwtService =
                new JwtService(SECRET, 0);

        String token =
                expiredJwtService.generateToken(userId);

        assertFalse(expiredJwtService.isValid(token));
    }
}