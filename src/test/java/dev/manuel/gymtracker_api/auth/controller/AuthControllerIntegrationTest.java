package dev.manuel.gymtracker_api.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.manuel.gymtracker_api.auth.dto.LoginRequest;
import dev.manuel.gymtracker_api.auth.model.RefreshToken;
import dev.manuel.gymtracker_api.auth.repository.RefreshTokenRepository;
import dev.manuel.gymtracker_api.auth.security.JwtService;
import dev.manuel.gymtracker_api.user.model.User;
import dev.manuel.gymtracker_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import static org.springframework.http.HttpHeaders.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("gymtracker_test")
                    .withUsername("gymtracker")
                    .withPassword("gymtracker");

    @DynamicPropertySource
    static void configureDatasource(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );

        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );

        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
        registry.add("jwt.secret", () -> "test-secret-long-enough-for-hmac-sha-256-signing");
        registry.add("app.auth.cookie-secure", () -> "true");
        registry.add("app.auth.cookie-same-site", () -> "None");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {

        User user = createUser(
                "manuel@example.com",
                "password123"
        );

        userRepository.save(user);

        LoginRequest request = new LoginRequest(
                "manuel@example.com",
                "password123"
        );

        mockMvc.perform(
                post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)
                        )
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(header().string(SET_COOKIE, org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("refreshToken="),
                org.hamcrest.Matchers.containsString("HttpOnly"),
                org.hamcrest.Matchers.containsString("Secure"),
                org.hamcrest.Matchers.containsString("SameSite=None"),
                org.hamcrest.Matchers.containsString("Path=/api/auth")
        )));

        org.junit.jupiter.api.Assertions.assertEquals(1, refreshTokenRepository.count());
        RefreshToken stored = refreshTokenRepository.findAll().getFirst();
        org.junit.jupiter.api.Assertions.assertEquals(user.getId(), stored.getUser().getId());
        org.junit.jupiter.api.Assertions.assertEquals(64, stored.getTokenHash().length());
    }

    @Test
    void shouldRejectLoginWithIncorrectPassword()
            throws Exception {

        User user = createUser(
                "manuel@example.com",
                "password123"
        );

        userRepository.save(user);

        LoginRequest request = new LoginRequest(
                "manuel@example.com",
                "wrong-password"
        );

        mockMvc.perform(
                post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)
                        )
        )
        .andExpect(status().isUnauthorized())
        .andExpect(
                jsonPath("$.code")
                        .value("INVALID_CREDENTIALS")
        );
    }

    @Test
    void shouldRejectLoginWhenUserDoesNotExist()
            throws Exception {

        LoginRequest request = new LoginRequest(
                "unknown@example.com",
                "password123"
        );

        mockMvc.perform(
                post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(request)
                        )
        )
        .andExpect(status().isUnauthorized())
        .andExpect(
                jsonPath("$.code")
                        .value("INVALID_CREDENTIALS")
        );
    }

    @Test
    void shouldExposeOpenApiDocumentationWithoutAuthentication()
            throws Exception {

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Gym Tracker API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRotateRefreshTokenAndReturnNewAccessToken() throws Exception {
        User user = userRepository.save(createUser("refresh@example.com", "password123"));
        String firstToken = loginAndGetRefreshToken(user.getEmail(), "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String secondToken = refreshTokenFrom(result);
        org.junit.jupiter.api.Assertions.assertNotEquals(firstToken, secondToken);
        org.junit.jupiter.api.Assertions.assertEquals(2, refreshTokenRepository.count());
        UUID accessUserId = jwtService.extractUserId(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText()
        );
        org.junit.jupiter.api.Assertions.assertEquals(user.getId(), accessUserId);
    }

    @Test
    void shouldRejectExpiredRefreshToken() throws Exception {
        User user = userRepository.save(createUser("expired@example.com", "password123"));
        String token = loginAndGetRefreshToken(user.getEmail(), "password123");
        RefreshToken stored = refreshTokenRepository.findAll().getFirst();
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.save(stored);

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void shouldRejectMissingAndInvalidRefreshTokens() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", "not-a-real-refresh-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void shouldRevokeTokenFamilyWhenRotatedTokenIsReused() throws Exception {
        User user = userRepository.save(createUser("reuse@example.com", "password123"));
        String firstToken = loginAndGetRefreshToken(user.getEmail(), "password123");
        MvcResult rotation = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", firstToken)))
                .andExpect(status().isOk())
                .andReturn();
        String secondToken = refreshTokenFrom(rotation);

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", firstToken)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", secondToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLogoutRevokeRefreshSessionAndClearCookie() throws Exception {
        User user = userRepository.save(createUser("logout@example.com", "password123"));
        String token = loginAndGetRefreshToken(user.getEmail(), "password123");

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refreshToken", token)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(SET_COOKIE, org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("refreshToken="),
                        org.hamcrest.Matchers.containsString("Max-Age=0")
                )));
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("refreshToken", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldKeepRefreshSessionsIsolatedByUser() throws Exception {
        User first = userRepository.save(createUser("first@example.com", "password123"));
        User second = userRepository.save(createUser("second@example.com", "password123"));
        String firstToken = loginAndGetRefreshToken(first.getEmail(), "password123");
        String secondToken = loginAndGetRefreshToken(second.getEmail(), "password123");

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("refreshToken", firstToken)))
                .andExpect(status().isNoContent());
        MvcResult result = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", secondToken)))
                .andExpect(status().isOk())
                .andReturn();
        UUID refreshedUserId = jwtService.extractUserId(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText()
        );
        org.junit.jupiter.api.Assertions.assertEquals(second.getId(), refreshedUserId);
    }

    @Test
    void shouldAllowCredentialedCorsOnlyForConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/refresh")
                        .header(ORIGIN, "http://localhost:4200")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/auth/refresh")
                        .header(ORIGIN, "https://attacker.example")
                        .header(ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private String loginAndGetRefreshToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return refreshTokenFrom(result);
    }

    private String refreshTokenFrom(MvcResult result) {
        String setCookie = result.getResponse().getHeader(SET_COOKIE);
        org.junit.jupiter.api.Assertions.assertNotNull(setCookie);
        return setCookie.substring("refreshToken=".length(), setCookie.indexOf(';'));
    }

    private User createUser(
            String email,
            String password
    ) {
        User user = new User();

        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(
                passwordEncoder.encode(password)
        );
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        return user;
    }
}
