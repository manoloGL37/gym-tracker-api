package dev.manuel.gymtracker_api.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration
    ) {
        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expiration = expiration;
    }

    public String generateToken(UUID userId) {
        Date now = new Date();
        Date expirationDate =
                new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expirationDate)
                .signWith(signingKey)
                .compact();
    }

    public UUID extractUserId(String token) {
        Claims claims = parseClaims(token);

        return UUID.fromString(claims.getSubject());
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}