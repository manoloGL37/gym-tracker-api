package dev.manuel.gymtracker_api.auth.service;

import dev.manuel.gymtracker_api.auth.dto.AuthResponse;
import dev.manuel.gymtracker_api.auth.dto.LoginRequest;
import dev.manuel.gymtracker_api.auth.exception.InvalidCredentialsException;
import dev.manuel.gymtracker_api.auth.exception.InvalidRefreshTokenException;
import dev.manuel.gymtracker_api.auth.model.RefreshToken;
import dev.manuel.gymtracker_api.auth.repository.RefreshTokenRepository;
import dev.manuel.gymtracker_api.auth.security.JwtService;
import dev.manuel.gymtracker_api.user.model.User;
import dev.manuel.gymtracker_api.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final long refreshExpiration;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenRepository refreshTokenRepository,
            @org.springframework.beans.factory.annotation.Value("${app.auth.refresh-expiration}") long refreshExpiration
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        if (refreshExpiration <= 0) {
            throw new IllegalArgumentException("Refresh-token expiration must be positive");
        }
        this.refreshExpiration = refreshExpiration;
    }

    @Transactional
    public AuthSession login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            throw new InvalidCredentialsException();
        }

        return createSession(user);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthSession refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = Instant.now();

        if (current.getRevokedAt() != null) {
            refreshTokenRepository.revokeActiveFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            refreshTokenRepository.revokeActiveFamily(current.getFamilyId(), now);
            throw new InvalidRefreshTokenException();
        }

        String nextRawToken = randomToken();
        RefreshToken next = tokenFor(
                current.getUser(),
                current.getFamilyId(),
                nextRawToken,
                current.getExpiresAt(),
                now
        );
        current.setRevokedAt(now);
        current.setReplacedByTokenId(next.getId());
        refreshTokenRepository.save(current);
        refreshTokenRepository.save(next);

        return new AuthSession(
                new AuthResponse(jwtService.generateToken(current.getUser().getId())),
                nextRawToken,
                next.getExpiresAt()
        );
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .ifPresent(token -> refreshTokenRepository.revokeActiveFamily(token.getFamilyId(), Instant.now()));
    }

    private AuthSession createSession(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(refreshExpiration);
        String rawToken = randomToken();
        RefreshToken refreshToken = tokenFor(user, UUID.randomUUID(), rawToken, expiresAt, now);
        refreshTokenRepository.save(refreshToken);

        return new AuthSession(
                new AuthResponse(jwtService.generateToken(user.getId())),
                rawToken,
                expiresAt
        );
    }

    private RefreshToken tokenFor(
            User user,
            UUID familyId,
            String rawToken,
            Instant expiresAt,
            Instant createdAt
    ) {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUser(user);
        token.setFamilyId(familyId);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(expiresAt);
        token.setCreatedAt(createdAt);
        return token;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
