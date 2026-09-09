package dev.manuel.gymtracker_api.user.controller;

import dev.manuel.gymtracker_api.auth.security.JwtService;
import dev.manuel.gymtracker_api.user.model.User;
import dev.manuel.gymtracker_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.manuel.gymtracker_api.user.dto.CreateUserRequest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UserControllerIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("gymtracker_test")
            .withUsername("gymtracker")
            .withPassword("gymtracker");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl);
        registry.add(
                "spring.datasource.username",
                postgres::getUsername);
        registry.add(
                "spring.datasource.password",
                postgres::getPassword);
    }

    private final MockMvc mockMvc;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    private UUID userA;
    private UUID userB;

    UserControllerIntegrationTest(
            MockMvc mockMvc,
            UserRepository userRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder) {
        this.mockMvc = mockMvc;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        userA = createUser();
        userB = createUser();
    }

    @Test
    void shouldReturnCurrentUserWithValidJwt() throws Exception {
        String token = jwtService.generateToken(userA);

        String response = mockMvc.perform(
                get("/api/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains(userA.toString()));
    }

    @Test
    void shouldRejectRequestWithoutJwt() throws Exception {
        mockMvc.perform(
                get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotExposeUserById() throws Exception {
        String token = jwtService.generateToken(userA);

        mockMvc.perform(
                get("/api/users/" + userB)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "new-user@test.com",
                "password123");

        String response = mockMvc.perform(
                post("/api/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("new-user@test.com"));
    }

    @Test
    void shouldRejectInvalidRegistration() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "not-an-email",
                "short");

        String response = mockMvc.perform(
                post("/api/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("VALIDATION_ERROR"));
    }

    @Test
    void shouldRejectDuplicateEmail() throws Exception {
        String email = "duplicate@test.com";

        CreateUserRequest request = new CreateUserRequest(
                email,
                "password123");

        mockMvc.perform(
                post("/api/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(
                post("/api/users")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(result -> assertTrue(
                        result.getResponse()
                                .getContentAsString()
                                .contains("EMAIL_ALREADY_EXISTS")));
    }

    private UUID createUser() {
        User user = new User();

        user.setId(UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@test.com");
        user.setPasswordHash(
                passwordEncoder.encode("test-password"));

        LocalDateTime now = LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return userRepository.save(user).getId();
    }
}