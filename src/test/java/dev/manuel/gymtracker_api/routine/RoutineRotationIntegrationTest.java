package dev.manuel.gymtracker_api.routine;

import dev.manuel.gymtracker_api.auth.security.JwtService;
import dev.manuel.gymtracker_api.routine.dto.RoutineRotation;
import dev.manuel.gymtracker_api.routine.model.Routine;
import dev.manuel.gymtracker_api.routine.repository.RoutineRepository;
import dev.manuel.gymtracker_api.routine.service.RoutineRotationService;
import dev.manuel.gymtracker_api.routine.service.RoutineService;
import dev.manuel.gymtracker_api.user.model.User;
import dev.manuel.gymtracker_api.user.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RoutineRotationIntegrationTest {
    private static final String PATH = "/api/routine-rotation";
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "test-secret-long-enough-for-hmac-sha-256-signing");
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoutineRepository routines;
    @Autowired JwtService jwt;
    @Autowired RoutineRotationService rotation;
    @Autowired RoutineService routineService;
    @Autowired PlatformTransactionManager transactions;

    UUID accountA;
    UUID accountB;
    UUID a;
    UUID b;
    UUID c;
    UUID foreign;

    @BeforeEach
    void setup() {
        // Separate accounts keep tests independent without deleting historical data.
        accountA = account();
        accountB = account();
        a = routine(accountA);
        b = routine(accountA);
        c = routine(accountA);
        foreign = routine(accountB);
    }

    @Test
    void newAccountAndRoutineCreationLeaveRotationEmpty() throws Exception {
        expectRotation(accountA, List.of());
        mvc.perform(post("/api/routines").header("Authorization", token(accountA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\",\"exercises\":[]}"))
                .andExpect(status().isCreated());
        expectRotation(accountA, List.of());
        replace(accountA, List.of(a)).andExpect(status().isOk());
        mvc.perform(post("/api/routines").header("Authorization", token(accountA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Another\",\"exercises\":[]}"))
                .andExpect(status().isCreated());
        expectRotation(accountA, List.of(a));
    }

    @Test
    void orderedFullReplacementAndRetriesAreIdempotent() throws Exception {
        replace(accountA, List.of(a, b, c)).andExpect(status().isOk())
                .andExpect(content().json(body(List.of(a, b, c))));
        expectRotation(accountA, List.of(a, b, c));
        replace(accountA, List.of(c, a, b)).andExpect(status().isOk());
        replace(accountA, List.of(c, a, b)).andExpect(status().isOk());
        expectRotation(accountA, List.of(c, a, b));
        replace(accountA, List.of(c)).andExpect(status().isOk());
        expectRotation(accountA, List.of(c));
        replace(accountA, List.of()).andExpect(status().isOk());
        replace(accountA, List.of()).andExpect(status().isOk());
        expectRotation(accountA, List.of());
    }

    @Test
    void invalidReplacementsPreserveEntirePreviousRotation() throws Exception {
        replace(accountA, List.of(a, b, c)).andExpect(status().isOk());
        replace(accountA, List.of(a, a)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        expectRotation(accountA, List.of(a, b, c));
        for (UUID unavailable : List.of(UUID.randomUUID(), foreign)) {
            replace(accountA, List.of(c, unavailable, a)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            expectRotation(accountA, List.of(a, b, c));
        }
    }

    @Test
    void validatesMissingNullAndMalformedIds() throws Exception {
        for (String payload : List.of("{}", "{\"routineIds\":null}", "{\"routineIds\":[null]}")) {
            mvc.perform(put(PATH).header("Authorization", token(accountA))
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(put(PATH).header("Authorization", token(accountA))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"routineIds\":[\"bad-id\"]}"))
                .andExpect(status().isBadRequest());
        expectRotation(accountA, List.of());
    }

    @Test
    void authenticationAndAccountIsolation() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(body(List.of())))
                .andExpect(status().isUnauthorized());
        replace(accountB, List.of(foreign)).andExpect(status().isOk());
        expectRotation(accountA, List.of());
        mvc.perform(get(PATH).param("userId", accountB.toString()).header("Authorization", token(accountA)))
                .andExpect(status().isOk()).andExpect(content().json(body(List.of())));
        replace(accountA, List.of(foreign)).andExpect(status().isNotFound());
        mvc.perform(put(PATH).header("Authorization", token(accountA)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + accountB + "\",\"routineIds\":[]}"))
                .andExpect(status().isOk());
        expectRotation(accountB, List.of(foreign));
        mvc.perform(delete("/api/routines/{id}", foreign).header("Authorization", token(accountA)))
                .andExpect(status().isNotFound());
        expectRotation(accountB, List.of(foreign));
    }

    @Test
    void deletionRemovesOnlyRotationReferenceAndRetainsWorkoutIdentity() throws Exception {
        replace(accountA, List.of(a, b, c)).andExpect(status().isOk());
        String workout = mvc.perform(post("/api/workouts/mobile").header("Authorization", token(accountA))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"clientId":"%s","routineId":"%s","nameSnapshot":"Historical B",
                                "startedAt":"2026-10-01T10:00:00Z","completedAt":"2026-10-01T11:00:00Z",
                                "calendarZone":"Europe/Madrid","exercises":[]}
                                """.formatted(UUID.randomUUID(), b)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.routineId").value(b.toString()))
                .andReturn().getResponse().getContentAsString();
        UUID workoutId = UUID.fromString(new org.json.JSONObject(workout).getString("id"));
        mvc.perform(delete("/api/routines/{id}", b).header("Authorization", token(accountA)))
                .andExpect(status().isNoContent());
        expectRotation(accountA, List.of(a, c));
        assertThat(routines.findById(b).orElseThrow().getDeletedAt()).isNotNull();
        mvc.perform(get("/api/workouts/{id}", workoutId).header("Authorization", token(accountA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.routineId").value(b.toString()))
                .andExpect(jsonPath("$.nameSnapshot").value("Historical B"))
                .andExpect(jsonPath("$.completedAtInstant").value("2026-10-01T11:00:00Z"));
        mvc.perform(get("/api/workouts").header("Authorization", token(accountA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].routineId").value(b.toString()));
        replace(accountA, List.of(a, b, c)).andExpect(status().isNotFound());
        expectRotation(accountA, List.of(a, c));
    }

    @Test
    void rollbackRestoresRotationAndRoutineTogether() {
        rotation.replace(accountA, new RoutineRotation(List.of(a, b, c)));
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            routineService.deleteRoutine(accountA, b);
            throw new IllegalStateException("Simulated failure");
        }));
        assertThat(rotation.get(accountA).routineIds()).containsExactly(a, b, c);
        assertThat(routines.findById(b).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void putWaitsForConcurrentDeletionThenRejectsDeletedRoutine() throws Exception {
        rotation.replace(accountA, new RoutineRotation(List.of(a, b, c)));
        CountDownLatch deleted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch replacing = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var deletion = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                routineService.deleteRoutine(accountA, b);
                deleted.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }));
            try {
                assertThat(deleted.await(10, TimeUnit.SECONDS)).isTrue();
                var replacement = executor.submit(() -> {
                    replacing.countDown();
                    return assertThrows(dev.manuel.gymtracker_api.common.exception.ResourceNotFoundException.class,
                            () -> rotation.replace(accountA, new RoutineRotation(List.of(c, b, a))));
                });
                assertThat(replacing.await(10, TimeUnit.SECONDS)).isTrue();
                assertThrows(TimeoutException.class, () -> replacement.get(200, TimeUnit.MILLISECONDS));
                release.countDown();
                deletion.get(10, TimeUnit.SECONDS);
                replacement.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        assertThat(rotation.get(accountA).routineIds()).containsExactly(a, c);
    }

    @Test
    void openApiDocumentsAuthenticatedFullReplacement() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/routine-rotation'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/routine-rotation'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/routine-rotation'].put.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/routine-rotation'].put.security[0].bearerAuth").exists());
    }

    @Test
    void migrationPreservesExistingAccountsRoutinesAndWorkouts() throws Exception {
        String schema = "rotation_upgrade";
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).target("15").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var sql = connection.createStatement()) {
            connection.setSchema(schema);
            sql.execute("INSERT INTO users(id,email,password_hash,created_at,updated_at) VALUES ('" + accountA + "','legacy@test.com','x',now(),now())");
            sql.execute("INSERT INTO routines(id,user_id,name,created_at,updated_at) VALUES ('" + a + "','" + accountA + "','Push',now(),now())");
            sql.execute("INSERT INTO workouts(id,user_id,routine_id,started_at,completed_at,created_at,name_snapshot) VALUES ('" + b + "','" + accountA + "','" + a + "',now(),now(),now(),'Push')");
        }
        var flyway = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).load();
        flyway.migrate();
        flyway.validate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var sql = connection.createStatement()) {
            connection.setSchema(schema);
            var rows = sql.executeQuery("SELECT w.routine_id,w.name_snapshot,(SELECT count(*) FROM routine_rotation) FROM workouts w JOIN routines r ON r.id=w.routine_id JOIN users u ON u.id=r.user_id");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getObject(1)).isEqualTo(a);
            assertThat(rows.getString(2)).isEqualTo("Push");
            assertThat(rows.getInt(3)).isZero();
            assertThat(rows.next()).isFalse();
        }
    }

    private UUID account() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(user.getId() + "@test.com");
        user.setPasswordHash("unused");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return users.save(user).getId();
    }

    private UUID routine(UUID account) {
        Routine routine = new Routine();
        routine.setId(UUID.randomUUID());
        routine.setUserId(account);
        routine.setName("Routine");
        routine.setCreatedAt(LocalDateTime.now());
        routine.setUpdatedAt(LocalDateTime.now());
        return routines.save(routine).getId();
    }

    private String token(UUID account) {
        return "Bearer " + jwt.generateToken(account);
    }

    private String body(List<UUID> ids) {
        return "{\"routineIds\":[" + ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",")) + "]}";
    }

    private ResultActions replace(UUID account, List<UUID> ids) throws Exception {
        return mvc.perform(put(PATH).header("Authorization", token(account))
                .contentType(MediaType.APPLICATION_JSON).content(body(ids)));
    }

    private void expectRotation(UUID account, List<UUID> ids) throws Exception {
        ResultActions response = mvc.perform(get(PATH).header("Authorization", token(account)))
                .andExpect(status().isOk()).andExpect(content().json(body(ids)))
                .andExpect(jsonPath("$.routineIds.length()").value(ids.size()));
        for (int i = 0; i < ids.size(); i++) {
            response.andExpect(jsonPath("$.routineIds[" + i + "]").value(ids.get(i).toString()));
        }
        assertThat(rotation.get(account).routineIds()).containsExactlyElementsOf(ids);
    }
}
