package dev.manuel.gymtracker_api.routine;

import dev.manuel.gymtracker_api.auth.security.JwtService;
import dev.manuel.gymtracker_api.exercise.model.Exercise;
import dev.manuel.gymtracker_api.exercise.model.ExerciseSource;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseRepository;
import dev.manuel.gymtracker_api.routine.dto.RoutineExerciseRequest;
import dev.manuel.gymtracker_api.routine.model.Routine;
import dev.manuel.gymtracker_api.routine.model.RoutineExercise;
import dev.manuel.gymtracker_api.routine.repository.RoutineExerciseRepository;
import dev.manuel.gymtracker_api.routine.repository.RoutineRepository;
import dev.manuel.gymtracker_api.user.model.User;
import dev.manuel.gymtracker_api.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RoutineControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry) {
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private RoutineRepository routineRepository;

    @Autowired
    private RoutineExerciseRepository routineExerciseRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private UUID userA;
    private UUID userB;

    private UUID globalExerciseId;
    private UUID userAExerciseId;
    private UUID userBExerciseId;
    private UUID deletedExerciseId;

    @BeforeEach
    void setUp() {

        routineExerciseRepository.deleteAll();
        routineRepository.deleteAll();
        exerciseRepository.deleteAll();
        userRepository.deleteAll();

        User firstUser = createUser(
                "routine-a@test.com");

        User secondUser = createUser(
                "routine-b@test.com");

        userA = firstUser.getId();
        userB = secondUser.getId();

        globalExerciseId = createExercise(
                null,
                "Press banca");

        userAExerciseId = createExercise(
                userA,
                "Mi press");

        userBExerciseId = createExercise(
                userB,
                "Press privado B");

        deletedExerciseId = createExercise(
                userA,
                "Ejercicio eliminado");

        Exercise deletedExercise = exerciseRepository.findById(
                deletedExerciseId).orElseThrow();

        deletedExercise.setDeletedAt(
                LocalDateTime.now());

        exerciseRepository.save(deletedExercise);
    }

    @Test
    void shouldCreateRoutineWithGlobalExercise()
            throws Exception {

        String token = jwtService.generateToken(userA);

        String request = """
                {
                    "name": "Push A",
                    "description": "Rutina de empuje",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 120,
                            "notes": "Controlar la bajada"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(
                        status().isCreated());
    }

    @Test
    void shouldCreateRoutineWithOwnPrivateExercise()
            throws Exception {

        String token = jwtService.generateToken(userA);

        String request = """
                {
                    "name": "Rutina privada",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 4,
                            "targetReps": 8,
                            "restSeconds": 90
                        }
                    ]
                }
                """.formatted(userAExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(
                        status().isCreated());
    }

    @Test
    void shouldRejectAnotherUsersPrivateExercise()
            throws Exception {

        String token = jwtService.generateToken(userA);

        String request = """
                {
                    "name": "Rutina inválida",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90
                        }
                    ]
                }
                """.formatted(userBExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(
                        status().isNotFound());
    }

    @Test
    void shouldRejectDeletedExercise()
            throws Exception {

        String token = jwtService.generateToken(userA);

        String request = """
                {
                    "name": "Rutina inválida",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90
                        }
                    ]
                }
                """.formatted(deletedExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(
                        status().isNotFound());
    }

    @Test
    void shouldRejectNonExistingExercise()
            throws Exception {

        String token = jwtService.generateToken(userA);

        UUID nonExistingExerciseId = UUID.randomUUID();

        String request = """
                {
                    "name": "Rutina inválida",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90
                        }
                    ]
                }
                """.formatted(nonExistingExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(
                        status().isNotFound());
    }

    @Test
    void shouldGetOwnRoutine()
            throws Exception {

        String token = jwtService.generateToken(userA);

        String request = """
                {
                    "name": "Push A",
                    "description": "Pecho, hombros y tríceps",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 4,
                            "targetReps": 8,
                            "restSeconds": 120,
                            "notes": "Controlar la bajada"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(
                        status().isCreated());

        Routine routine = routineRepository
                .findAll()
                .stream()
                .filter(routineEntity -> routineEntity.getUserId()
                        .equals(userA)
                        && routineEntity.getName()
                                .equals("Push A"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(
                get("/api/routines/" + routine.getId())
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(routine.getId().toString()))
                .andExpect(
                        jsonPath("$.name")
                                .value("Push A"))
                .andExpect(
                        jsonPath("$.description")
                                .value("Pecho, hombros y tríceps"))
                .andExpect(
                        jsonPath("$.exercises")
                                .isArray())
                .andExpect(
                        jsonPath("$.exercises.length()")
                                .value(1))
                .andExpect(
                        jsonPath("$.exercises[0].exerciseId")
                                .value(globalExerciseId.toString()))
                .andExpect(
                        jsonPath("$.exercises[0].position")
                                .value(0))
                .andExpect(
                        jsonPath("$.exercises[0].sets")
                                .value(4))
                .andExpect(
                        jsonPath("$.exercises[0].targetReps")
                                .value(8))
                .andExpect(
                        jsonPath("$.exercises[0].restSeconds")
                                .value(120))
                .andExpect(
                        jsonPath("$.exercises[0].notes")
                                .value("Controlar la bajada"));
    }

    @Test
    void shouldReturnRoutineExercisesOrderedByPosition()
            throws Exception {

        String token = jwtService.generateToken(userA);

        String request = """
                {
                    "name": "Pull A",
                    "description": "Espalda y bíceps",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 2,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": null
                        },
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 4,
                            "targetReps": 8,
                            "restSeconds": 120,
                            "notes": null
                        }
                    ]
                }
                """.formatted(
                globalExerciseId,
                userAExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(
                        status().isCreated());

        Routine routine = routineRepository
                .findAll()
                .stream()
                .filter(routineEntity -> routineEntity.getUserId()
                        .equals(userA)
                        && routineEntity.getName()
                                .equals("Pull A"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(
                get("/api/routines/" + routine.getId())
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.exercises.length()")
                                .value(2))
                .andExpect(
                        jsonPath("$.exercises[0].position")
                                .value(0))
                .andExpect(
                        jsonPath("$.exercises[0].exerciseId")
                                .value(userAExerciseId.toString()))
                .andExpect(
                        jsonPath("$.exercises[1].position")
                                .value(2))
                .andExpect(
                        jsonPath("$.exercises[1].exerciseId")
                                .value(globalExerciseId.toString()));
    }

    @Test
    void shouldRejectAnotherUsersRoutine()
            throws Exception {

        String tokenUserA = jwtService.generateToken(userA);

        String tokenUserB = jwtService.generateToken(userB);

        String request = """
                {
                    "name": "Routine B",
                    "description": "Rutina privada de B",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": null
                        }
                    ]
                }
                """.formatted(userBExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + tokenUserB)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(
                        status().isCreated());

        Routine routine = routineRepository
                .findAll()
                .stream()
                .filter(routineEntity -> routineEntity.getUserId()
                        .equals(userB)
                        && routineEntity.getName()
                                .equals("Routine B"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(
                get("/api/routines/" + routine.getId())
                        .header(
                                "Authorization",
                                "Bearer " + tokenUserA))
                .andExpect(
                        status().isNotFound());
    }

    @Test
    void shouldRejectNonExistingRoutine()
            throws Exception {

        String token = jwtService.generateToken(userA);

        UUID nonExistingRoutineId = UUID.randomUUID();

        mockMvc.perform(
                get("/api/routines/" + nonExistingRoutineId)
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(
                        status().isNotFound());
    }

    @Test
    void shouldRejectGetRoutineWithoutAuthentication()
            throws Exception {

        UUID routineId = UUID.randomUUID();

        mockMvc.perform(
                get("/api/routines/" + routineId)).andExpect(
                        status().isUnauthorized());
    }

    @Test
    void shouldCreateRoutineIdempotently() throws Exception {
        UUID clientId = UUID.randomUUID();
        String token = jwtService.generateToken(userA);
        long routinesBefore = routineRepository.count();
        String request = """
                { "clientId": "%s", "name": "Imported routine", "exercises": [] }
                """.formatted(clientId);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/routines")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.clientId").value(clientId.toString()));
        }

        assertThat(routineRepository.count()).isEqualTo(routinesBefore + 1);
        assertThat(routineRepository.findByUserIdAndClientId(userA, clientId)).isPresent();
    }

    private User createUser(String email) {

        User user = new User();

        user.setId(UUID.randomUUID());
        user.setEmail(email);

        user.setPasswordHash(
                passwordEncoder.encode("password123"));

        LocalDateTime now = LocalDateTime.now();

        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return userRepository.save(user);
    }

    private UUID createExercise(
            UUID ownerId,
            String name) {

        Exercise exercise = new Exercise();

        exercise.setId(UUID.randomUUID());
        exercise.setOwnerId(ownerId);

        exercise.setSource(
                ownerId == null
                        ? ExerciseSource.EXERCISES_DATASET
                        : ExerciseSource.USER);

        exercise.setSourceId(null);
        exercise.setCategory("strength");
        exercise.setEquipment("barbell");
        exercise.setTargetMuscle("chest");
        exercise.setMuscleGroup("chest");

        LocalDateTime now = LocalDateTime.now();

        exercise.setCreatedAt(now);
        exercise.setUpdatedAt(now);

        return exerciseRepository
                .save(exercise)
                .getId();
    }

    @Test
    void shouldGetOnlyOwnRoutines() throws Exception {

        String tokenUserA = jwtService.generateToken(userA);

        String tokenUserB = jwtService.generateToken(userB);

        createRoutine(
                tokenUserA,
                "Rutina A");

        createRoutine(
                tokenUserB,
                "Rutina B");

        mockMvc.perform(
                get("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + tokenUserA))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(1))
                .andExpect(
                        jsonPath("$.content[0].name")
                                .value("Rutina A"));
    }

    @Test
    void shouldPaginateRoutines() throws Exception {

        String token = jwtService.generateToken(userA);

        createRoutine(token, "Rutina 1");
        createRoutine(token, "Rutina 2");
        createRoutine(token, "Rutina 3");

        mockMvc.perform(
                get("/api/routines")
                        .param("page", "0")
                        .param("size", "2")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(2))
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(3))
                .andExpect(
                        jsonPath("$.totalPages")
                                .value(2));
    }

    @Test
    void shouldReturnSecondPage() throws Exception {

        String token = jwtService.generateToken(userA);

        createRoutine(token, "Rutina 1");
        createRoutine(token, "Rutina 2");
        createRoutine(token, "Rutina 3");

        mockMvc.perform(
                get("/api/routines")
                        .param("page", "1")
                        .param("size", "2")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(1))
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(3));
    }

    @Test
    void shouldRejectGetRoutinesWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                get("/api/routines")).andExpect(
                        status().isUnauthorized());
    }

    private void createRoutine(
            String token,
            String name) throws Exception {

        String request = """
                {
                    "name": "%s",
                    "description": "Descripción",
                    "exercises": []
                }
                """.formatted(name);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(
                        status().isCreated());
    }

    @Test
    void shouldUpdateOwnRoutine() throws Exception {

        String token = jwtService.generateToken(userA);

        String createRequest = """
                {
                    "name": "Rutina original",
                    "description": "Descripción original",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Original"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(
                        status().isCreated());

        Routine routine = routineRepository.findAll()
                .stream()
                .filter(routineEntity -> routineEntity.getUserId().equals(userA)
                        && routineEntity.getName()
                                .equals("Rutina original"))
                .findFirst()
                .orElseThrow();

        String updateRequest = """
                {
                    "name": "Rutina actualizada",
                    "description": "Nueva descripción",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 4,
                            "targetReps": 8,
                            "restSeconds": 120,
                            "notes": "Actualizado"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                put("/api/routines/{id}", routine.getId())
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.name")
                                .value("Rutina actualizada"))
                .andExpect(
                        jsonPath("$.description")
                                .value("Nueva descripción"))
                .andExpect(
                        jsonPath("$.exercises.length()")
                                .value(1))
                .andExpect(
                        jsonPath("$.exercises[0].exerciseId")
                                .value(globalExerciseId.toString()))
                .andExpect(
                        jsonPath("$.exercises[0].position")
                                .value(0))
                .andExpect(
                        jsonPath("$.exercises[0].sets")
                                .value(4))
                .andExpect(
                        jsonPath("$.exercises[0].targetReps")
                                .value(8))
                .andExpect(
                        jsonPath("$.exercises[0].restSeconds")
                                .value(120))
                .andExpect(
                        jsonPath("$.exercises[0].notes")
                                .value("Actualizado"));
    }

    @Test
    void shouldReplaceRoutineExercisesOnUpdate() throws Exception {

        String token = jwtService.generateToken(userA);

        String createRequest = """
                {
                    "name": "Push A",
                    "description": "Rutina original",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Ejercicio original 1"
                        },
                        {
                            "exerciseId": "%s",
                            "position": 1,
                            "sets": 4,
                            "targetReps": 8,
                            "restSeconds": 120,
                            "notes": "Ejercicio original 2"
                        }
                    ]
                }
                """.formatted(
                globalExerciseId,
                userAExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(
                        status().isCreated());

        Routine routine = routineRepository.findAll()
                .stream()
                .filter(routineEntity -> routineEntity.getUserId().equals(userA)
                        && routineEntity.getName()
                                .equals("Push A"))
                .findFirst()
                .orElseThrow();

        String updateRequest = """
                {
                    "name": "Push A actualizada",
                    "description": "Rutina modificada",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 5,
                            "targetReps": 6,
                            "restSeconds": 150,
                            "notes": "Único ejercicio"
                        }
                    ]
                }
                """.formatted(userAExerciseId);

        mockMvc.perform(
                put("/api/routines/{id}", routine.getId())
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.name")
                                .value("Push A actualizada"))
                .andExpect(
                        jsonPath("$.description")
                                .value("Rutina modificada"))
                .andExpect(
                        jsonPath("$.exercises.length()")
                                .value(1))
                .andExpect(
                        jsonPath("$.exercises[0].exerciseId")
                                .value(userAExerciseId.toString()))
                .andExpect(
                        jsonPath("$.exercises[0].position")
                                .value(0))
                .andExpect(
                        jsonPath("$.exercises[0].sets")
                                .value(5))
                .andExpect(
                        jsonPath("$.exercises[0].targetReps")
                                .value(6))
                .andExpect(
                        jsonPath("$.exercises[0].restSeconds")
                                .value(150))
                .andExpect(
                        jsonPath("$.exercises[0].notes")
                                .value("Único ejercicio"));
    }

    @Test
    void shouldRemoveOldExercisesWhenReplacingRoutine() throws Exception {

        String token = jwtService.generateToken(userA);

        String createRequest = """
                {
                    "name": "Rutina reemplazo",
                    "description": "Rutina original",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Ejercicio global"
                        },
                        {
                            "exerciseId": "%s",
                            "position": 1,
                            "sets": 4,
                            "targetReps": 8,
                            "restSeconds": 120,
                            "notes": "Ejercicio privado"
                        }
                    ]
                }
                """.formatted(
                globalExerciseId,
                userAExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated());

        Routine routine = routineRepository.findAll()
                .stream()
                .filter(routineEntity -> routineEntity.getUserId().equals(userA)
                        && routineEntity.getName()
                                .equals("Rutina reemplazo"))
                .findFirst()
                .orElseThrow();

        String updateRequest = """
                {
                    "name": "Rutina reemplazo",
                    "description": "Rutina modificada",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 5,
                            "targetReps": 6,
                            "restSeconds": 150,
                            "notes": "Ejercicio que permanece"
                        }
                    ]
                }
                """.formatted(userAExerciseId);

        mockMvc.perform(
                put("/api/routines/{id}", routine.getId())
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isOk());

        List<RoutineExercise> exercises = routineExerciseRepository
                .findByRoutineIdOrderByPositionAsc(
                        routine.getId());

        assertThat(exercises)
                .hasSize(1);

        assertThat(exercises.get(0).getExerciseId())
                .isEqualTo(userAExerciseId);

        assertThat(exercises.get(0).getPosition())
                .isEqualTo(0);

        assertThat(exercises.get(0).getSets())
                .isEqualTo(5);

        assertThat(exercises.get(0).getTargetReps())
                .isEqualTo(6);

        assertThat(exercises.get(0).getRestSeconds())
                .isEqualTo(150);

        assertThat(exercises.get(0).getNotes())
                .isEqualTo("Ejercicio que permanece");
    }

    @Test
    void shouldRejectUpdatingAnotherUsersRoutine() throws Exception {

        String tokenA = jwtService.generateToken(userA);
        String tokenB = jwtService.generateToken(userB);

        String createRequest = """
                {
                    "name": "Rutina de usuario B",
                    "description": "Privada",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Privada"
                        }
                    ]
                }
                """.formatted(userBExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated());

        Routine routine = routineRepository.findAll()
                .stream()
                .filter(routineEntity -> routineEntity.getUserId().equals(userB)
                        && routineEntity.getName()
                                .equals("Rutina de usuario B"))
                .findFirst()
                .orElseThrow();

        String updateRequest = """
                {
                    "name": "Intento de modificación",
                    "description": "No debería permitirse",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 5,
                            "targetReps": 6,
                            "restSeconds": 120,
                            "notes": "Intento"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                put("/api/routines/{id}", routine.getId())
                        .header(
                                "Authorization",
                                "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldRejectUpdatingNonExistingRoutine() throws Exception {

        String token = jwtService.generateToken(userA);

        UUID nonExistingRoutineId = UUID.randomUUID();

        String updateRequest = """
                {
                    "name": "Rutina inexistente",
                    "description": "No debería actualizarse",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Test"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                put("/api/routines/{id}", nonExistingRoutineId)
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldRejectUpdatingRoutineWithoutAuthentication() throws Exception {

        UUID routineId = UUID.randomUUID();

        String updateRequest = """
                {
                    "name": "Rutina actualizada",
                    "description": "Intento sin autenticación",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Test"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                put("/api/routines/{id}", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectUpdatingRoutineWithAnotherUsersPrivateExercise()
            throws Exception {

        String token = jwtService.generateToken(userA);

        String createRequest = """
                {
                    "name": "Rutina original",
                    "description": "Original",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Original"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated());

        Routine routine = routineRepository.findAll()
                .stream()
                .filter(routineEntity -> routineEntity.getUserId().equals(userA)
                        && routineEntity.getName()
                                .equals("Rutina original"))
                .findFirst()
                .orElseThrow();

        String updateRequest = """
                {
                    "name": "Rutina modificada",
                    "description": "No debería actualizarse",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Intento"
                        }
                    ]
                }
                """.formatted(userBExerciseId);

        mockMvc.perform(
                put("/api/routines/{id}", routine.getId())
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldRejectUpdatingRoutineWithDeletedExercise()
            throws Exception {

        String token = jwtService.generateToken(userA);

        String createRequest = """
                {
                    "name": "Rutina original",
                    "description": "Original",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Original"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated());

        Routine routine = routineRepository.findAll()
                .stream()
                .filter(routineEntity -> routineEntity.getUserId().equals(userA)
                        && routineEntity.getName()
                                .equals("Rutina original"))
                .findFirst()
                .orElseThrow();

        String updateRequest = """
                {
                    "name": "Rutina modificada",
                    "description": "No debería actualizarse",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Intento"
                        }
                    ]
                }
                """.formatted(deletedExerciseId);

        mockMvc.perform(
                put("/api/routines/{id}", routine.getId())
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.code")
                                .value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldRejectDuplicateExercisePositionsOnUpdate()
            throws Exception {

        String token = jwtService.generateToken(userA);

        String createRequest = """
                {
                    "name": "Rutina original",
                    "description": "Original",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Original"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated());

        Routine routine = routineRepository.findAll()
                .stream()
                .filter(routineEntity -> routineEntity.getUserId().equals(userA)
                        && routineEntity.getName()
                                .equals("Rutina original"))
                .findFirst()
                .orElseThrow();

        String updateRequest = """
                {
                    "name": "Rutina modificada",
                    "description": "No debería actualizarse",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Ejercicio 1"
                        },
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 4,
                            "targetReps": 8,
                            "restSeconds": 120,
                            "notes": "Ejercicio 2"
                        }
                    ]
                }
                """.formatted(
                globalExerciseId,
                userAExerciseId);

        mockMvc.perform(
                put("/api/routines/{id}", routine.getId())
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("DUPLICATE_EXERCISE_POSITION"));
    }

    @Test
    void shouldRejectNotesLongerThan500CharactersOnUpdate()
            throws Exception {

        String token = jwtService.generateToken(userA);

        String createRequest = """
                {
                    "name": "Rutina original",
                    "description": "Original",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Original"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated());

        Routine routine = routineRepository.findAll()
                .stream()
                .filter(routineEntity -> routineEntity.getUserId().equals(userA)
                        && routineEntity.getName()
                                .equals("Rutina original"))
                .findFirst()
                .orElseThrow();

        String longNotes = "a".repeat(501);

        String updateRequest = """
                {
                    "name": "Rutina modificada",
                    "description": "Descripción",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "%s"
                        }
                    ]
                }
                """.formatted(
                globalExerciseId,
                longNotes);

        mockMvc.perform(
                put("/api/routines/{id}", routine.getId())
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR"));
    }

    @Test
    void shouldRollbackRoutineUpdateWhenExerciseIsInvalid()
            throws Exception {

        String token = jwtService.generateToken(userA);

        String createRequest = """
                {
                    "name": "Rutina original",
                    "description": "Descripción original",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Original"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated());

        Routine routine = routineRepository.findAll()
                .stream()
                .filter(routineEntity -> routineEntity.getUserId().equals(userA)
                        && routineEntity.getName()
                                .equals("Rutina original"))
                .findFirst()
                .orElseThrow();

        UUID nonExistingExerciseId = UUID.randomUUID();

        String updateRequest = """
                {
                    "name": "Rutina modificada",
                    "description": "No debería persistirse",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 5,
                            "targetReps": 6,
                            "restSeconds": 150,
                            "notes": "No debería persistirse"
                        }
                    ]
                }
                """.formatted(nonExistingExerciseId);

        mockMvc.perform(
                put("/api/routines/{id}", routine.getId())
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest))
                .andExpect(status().isNotFound());

        Routine persistedRoutine = routineRepository.findById(routine.getId())
                .orElseThrow();

        assertThat(persistedRoutine.getName())
                .isEqualTo("Rutina original");

        assertThat(persistedRoutine.getDescription())
                .isEqualTo("Descripción original");

        List<RoutineExercise> persistedExercises = routineExerciseRepository
                .findByRoutineIdOrderByPositionAsc(
                        routine.getId());

        assertThat(persistedExercises)
                .hasSize(1);

        assertThat(persistedExercises.get(0).getExerciseId())
                .isEqualTo(globalExerciseId);

        assertThat(persistedExercises.get(0).getSets())
                .isEqualTo(3);

        assertThat(persistedExercises.get(0).getTargetReps())
                .isEqualTo(10);

        assertThat(persistedExercises.get(0).getRestSeconds())
                .isEqualTo(90);

        assertThat(persistedExercises.get(0).getNotes())
                .isEqualTo("Original");
    }

    @Autowired
    private jakarta.validation.Validator validator;

    @Test
    void shouldValidateRoutineExerciseNotes() {

        RoutineExerciseRequest request = new RoutineExerciseRequest(
                globalExerciseId,
                0,
                3,
                10,
                90,
                "a".repeat(501));

        Set<jakarta.validation.ConstraintViolation<RoutineExerciseRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(violation -> violation.getPropertyPath()
                        .toString()
                        .equals("notes"));
    }

    @Test
    void shouldRejectNestedInvalidRoutineExercise()
            throws Exception {

        String token = jwtService.generateToken(userA);

        String longNotes = "a".repeat(501);

        String request = """
                {
                    "name": "Rutina inválida",
                    "description": "Descripción",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "%s"
                        }
                    ]
                }
                """.formatted(
                globalExerciseId,
                longNotes);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR"));
    }

    @Test
    void shouldDeleteOwnRoutine() throws Exception {

        String token = jwtService.generateToken(userA);

        String createRequest = """
                {
                    "name": "Rutina para eliminar",
                    "description": "Descripción",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Original"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header(
                                "Authorization",
                                "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated());

        Routine routine = routineRepository.findAll()
                .stream()
                .filter(routineEntity -> routineEntity.getUserId().equals(userA)
                        && routineEntity.getName()
                                .equals("Rutina para eliminar"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(
                delete("/api/routines/{id}", routine.getId())
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isNoContent());

        Routine deletedRoutine = routineRepository.findById(routine.getId())
                .orElseThrow();

        assertThat(deletedRoutine.getDeletedAt())
                .isNotNull();
    }

    @Test
    void shouldNotReturnDeletedRoutine() throws Exception {

        String token = jwtService.generateToken(userA);

        String createRequest = """
                {
                    "name": "Rutina eliminada",
                    "description": "Descripción",
                    "exercises": [
                        {
                            "exerciseId": "%s",
                            "position": 0,
                            "sets": 3,
                            "targetReps": 10,
                            "restSeconds": 90,
                            "notes": "Original"
                        }
                    ]
                }
                """.formatted(globalExerciseId);

        mockMvc.perform(
                post("/api/routines")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated());

        Routine routine = routineRepository.findAll()
                .stream()
                .filter(r -> r.getUserId().equals(userA)
                        && r.getName().equals("Rutina eliminada"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(
                delete("/api/routines/{id}", routine.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                get("/api/routines/{id}", routine.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldNotIncludeDeletedRoutineInList() throws Exception {

        String token = jwtService.generateToken(userA);

        createRoutine(token, "Rutina que permanece");

        createRoutine(token, "Rutina que se elimina");

        Routine routineToDelete = routineRepository.findAll()
                .stream()
                .filter(r -> r.getUserId().equals(userA)
                        && r.getName().equals("Rutina que se elimina"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(
                delete("/api/routines/{id}", routineToDelete.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                get("/api/routines")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name")
                        .value("Rutina que permanece"));
    }

    @Test
    void shouldRejectDeletingAnotherUsersRoutine() throws Exception {

        String tokenA = jwtService.generateToken(userA);
        String tokenB = jwtService.generateToken(userB);

        createRoutine(tokenA, "Rutina de usuario A");

        Routine routine = routineRepository.findAll()
                .stream()
                .filter(r -> r.getUserId().equals(userA)
                        && r.getName().equals("Rutina de usuario A"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(
                delete("/api/routines/{id}", routine.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        Routine persistedRoutine = routineRepository.findById(routine.getId())
                .orElseThrow();

        assertThat(persistedRoutine.getDeletedAt())
                .isNull();
    }

    @Test
    void shouldRejectDeletingNonExistingRoutine() throws Exception {

        String token = jwtService.generateToken(userA);

        UUID nonExistingRoutineId = UUID.randomUUID();

        mockMvc.perform(
                delete("/api/routines/{id}", nonExistingRoutineId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectDeletingRoutineWithoutAuthentication() throws Exception {

        UUID routineId = UUID.randomUUID();

        mockMvc.perform(
                delete("/api/routines/{id}", routineId)).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectDeletingAlreadyDeletedRoutine() throws Exception {

        String token = jwtService.generateToken(userA);

        createRoutine(token, "Rutina para eliminar dos veces");

        Routine routine = routineRepository.findAll()
                .stream()
                .filter(r -> r.getUserId().equals(userA)
                        && r.getName()
                                .equals("Rutina para eliminar dos veces"))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(
                delete("/api/routines/{id}", routine.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                delete("/api/routines/{id}", routine.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
