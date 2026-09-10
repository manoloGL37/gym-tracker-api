package dev.manuel.gymtracker_api.workout.controller;

import dev.manuel.gymtracker_api.auth.security.JwtService;
import dev.manuel.gymtracker_api.exercise.model.Exercise;
import dev.manuel.gymtracker_api.exercise.model.ExerciseSource;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseRepository;
import dev.manuel.gymtracker_api.routine.model.Routine;
import dev.manuel.gymtracker_api.routine.model.RoutineExercise;
import dev.manuel.gymtracker_api.routine.repository.RoutineExerciseRepository;
import dev.manuel.gymtracker_api.routine.repository.RoutineRepository;
import dev.manuel.gymtracker_api.user.model.User;
import dev.manuel.gymtracker_api.user.repository.UserRepository;
import dev.manuel.gymtracker_api.workout.model.Workout;
import dev.manuel.gymtracker_api.workout.model.WorkoutExercise;
import dev.manuel.gymtracker_api.workout.model.WorkoutSet;
import dev.manuel.gymtracker_api.workout.repository.WorkoutExerciseRepository;
import dev.manuel.gymtracker_api.workout.repository.WorkoutRepository;
import dev.manuel.gymtracker_api.workout.repository.WorkoutSetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WorkoutControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
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
    private WorkoutRepository workoutRepository;

    @Autowired
    private WorkoutExerciseRepository workoutExerciseRepository;

    @Autowired
    private WorkoutSetRepository workoutSetRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        workoutSetRepository.deleteAll();
        workoutExerciseRepository.deleteAll();
        workoutRepository.deleteAll();
        routineExerciseRepository.deleteAll();
        routineRepository.deleteAll();
        exerciseRepository.deleteAll();
        userRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // CREATE WORKOUT
    // -------------------------------------------------------------------------

    @Test
    void shouldCreateWorkoutFromOwnRoutine() throws Exception {
        User user = createUser("manuel@test.com");

        UUID exerciseId =
                createExercise(user.getId(), "Bench Press");

        Routine routine =
                createRoutine(
                        user.getId(),
                        "Push A",
                        "Chest workout"
                );

        createRoutineExercise(
                routine.getId(),
                exerciseId,
                0,
                3,
                10,
                120,
                "Control the movement"
        );

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "routineId": "%s"
                }
                """.formatted(routine.getId());

        mockMvc.perform(
                        post("/api/workouts")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(
                        jsonPath("$.routineId")
                                .value(routine.getId().toString())
                )
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").value((Object) null))
                .andExpect(jsonPath("$.notes").value((Object) null))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.exercises").isArray())
                .andExpect(jsonPath("$.exercises.length()").value(1))
                .andExpect(
                        jsonPath("$.exercises[0].exerciseId")
                                .value(exerciseId.toString())
                )
                .andExpect(
                        jsonPath("$.exercises[0].position")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.exercises[0].notes")
                                .value("Control the movement")
                )
                .andExpect(
                        jsonPath("$.exercises[0].sets.length()")
                                .value(0)
                );
    }

    @Test
    void shouldCopyAllRoutineExercisesToWorkout() throws Exception {
        User user = createUser("manuel@test.com");

        UUID firstExerciseId =
                createExercise(user.getId(), "Bench Press");

        UUID secondExerciseId =
                createExercise(
                        user.getId(),
                        "Incline Dumbbell Press"
                );

        Routine routine =
                createRoutine(
                        user.getId(),
                        "Push A",
                        "Chest workout"
                );

        createRoutineExercise(
                routine.getId(),
                firstExerciseId,
                0,
                3,
                10,
                120,
                "First exercise"
        );

        createRoutineExercise(
                routine.getId(),
                secondExerciseId,
                1,
                3,
                12,
                90,
                "Second exercise"
        );

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "routineId": "%s"
                }
                """.formatted(routine.getId());

        mockMvc.perform(
                        post("/api/workouts")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.exercises.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.exercises[0].exerciseId")
                                .value(firstExerciseId.toString())
                )
                .andExpect(
                        jsonPath("$.exercises[0].position")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.exercises[0].notes")
                                .value("First exercise")
                )
                .andExpect(
                        jsonPath("$.exercises[1].exerciseId")
                                .value(secondExerciseId.toString())
                )
                .andExpect(
                        jsonPath("$.exercises[1].position")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.exercises[1].notes")
                                .value("Second exercise")
                );
    }

    @Test
    void shouldCreateWorkoutWithoutExercisesWhenRoutineIsEmpty()
            throws Exception {

        User user = createUser("manuel@test.com");

        Routine routine =
                createRoutine(
                        user.getId(),
                        "Empty Routine",
                        null
                );

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "routineId": "%s"
                }
                """.formatted(routine.getId());

        mockMvc.perform(
                        post("/api/workouts")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.routineId")
                                .value(routine.getId().toString())
                )
                .andExpect(
                        jsonPath("$.exercises.length()")
                                .value(0)
                );
    }

    @Test
    void shouldRejectCreatingWorkoutFromAnotherUsersRoutine()
            throws Exception {

        User owner = createUser("owner@test.com");
        User otherUser = createUser("other@test.com");

        Routine routine =
                createRoutine(
                        owner.getId(),
                        "Owner Routine",
                        null
                );

        String token = jwtService.generateToken(otherUser.getId());

        String request = """
                {
                    "routineId": "%s"
                }
                """.formatted(routine.getId());

        mockMvc.perform(
                        post("/api/workouts")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectCreatingWorkoutFromNonExistingRoutine()
            throws Exception {

        User user = createUser("manuel@test.com");

        UUID routineId = UUID.randomUUID();

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "routineId": "%s"
                }
                """.formatted(routineId);

        mockMvc.perform(
                        post("/api/workouts")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectCreatingWorkoutFromDeletedRoutine()
            throws Exception {

        User user = createUser("manuel@test.com");

        Routine routine =
                createRoutine(
                        user.getId(),
                        "Deleted Routine",
                        null
                );

        routine.setDeletedAt(LocalDateTime.now());
        routineRepository.save(routine);

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "routineId": "%s"
                }
                """.formatted(routine.getId());

        mockMvc.perform(
                        post("/api/workouts")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectCreatingWorkoutWithoutAuthentication()
            throws Exception {

        User user = createUser("manuel@test.com");

        Routine routine =
                createRoutine(
                        user.getId(),
                        "Push A",
                        null
                );

        String request = """
                {
                    "routineId": "%s"
                }
                """.formatted(routine.getId());

        mockMvc.perform(
                        post("/api/workouts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectCreatingWorkoutWithoutRoutineId()
            throws Exception {

        User user = createUser("manuel@test.com");

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "routineId": null
                }
                """;

        mockMvc.perform(
                        post("/api/workouts")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                );
    }

    // -------------------------------------------------------------------------
    // GET WORKOUT
    // -------------------------------------------------------------------------

    @Test
    void shouldGetOwnWorkout() throws Exception {
        User user = createUser("manuel@test.com");

        Workout workout =
                createWorkout(user.getId(), null);

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                        get("/api/workouts/{id}", workout.getId())
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(workout.getId().toString())
                )
                .andExpect(
                        jsonPath("$.routineId").value((Object) null)
                )
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(
                        jsonPath("$.exercises.length()")
                                .value(0)
                );
    }

    @Test
    void shouldRejectGettingNonExistingWorkout()
            throws Exception {

        User user = createUser("manuel@test.com");

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                        get(
                                "/api/workouts/{id}",
                                UUID.randomUUID()
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectGettingAnotherUsersWorkout()
            throws Exception {

        User owner = createUser("owner@test.com");
        User otherUser = createUser("other@test.com");

        Workout workout =
                createWorkout(owner.getId(), null);

        String token = jwtService.generateToken(otherUser.getId());

        mockMvc.perform(
                        get("/api/workouts/{id}", workout.getId())
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectGettingWorkoutWithoutAuthentication()
            throws Exception {

        User user = createUser("manuel@test.com");

        Workout workout =
                createWorkout(user.getId(), null);

        mockMvc.perform(
                        get("/api/workouts/{id}", workout.getId())
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnWorkoutExercisesOrderedByPosition()
            throws Exception {

        User user = createUser("manuel@test.com");

        UUID firstExerciseId =
                createExercise(user.getId(), "Bench Press");

        UUID secondExerciseId =
                createExercise(user.getId(), "Squat");

        Workout workout =
                createWorkout(user.getId(), null);

        createWorkoutExercise(
                workout.getId(),
                secondExerciseId,
                1,
                "Second"
        );

        createWorkoutExercise(
                workout.getId(),
                firstExerciseId,
                0,
                "First"
        );

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                        get("/api/workouts/{id}", workout.getId())
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.exercises[0].exerciseId")
                                .value(firstExerciseId.toString())
                )
                .andExpect(
                        jsonPath("$.exercises[0].position")
                                .value(0)
                )
                .andExpect(
                        jsonPath("$.exercises[1].exerciseId")
                                .value(secondExerciseId.toString())
                )
                .andExpect(
                        jsonPath("$.exercises[1].position")
                                .value(1)
                );
    }

    // -------------------------------------------------------------------------
    // GET WORKOUTS
    // -------------------------------------------------------------------------

    @Test
    void shouldGetOnlyOwnWorkouts() throws Exception {
        User user = createUser("manuel@test.com");
        User otherUser = createUser("other@test.com");

        Workout ownWorkout =
                createWorkout(user.getId(), null);

        createWorkout(otherUser.getId(), null);

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                        get("/api/workouts")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.content[0].id")
                                .value(ownWorkout.getId().toString())
                );
    }

    @Test
    void shouldPaginateWorkouts() throws Exception {
        User user = createUser("manuel@test.com");

        for (int i = 0; i < 12; i++) {
            createWorkout(user.getId(), null);
        }

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                        get("/api/workouts")
                                .param("page", "0")
                                .param("size", "10")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(12)
                )
                .andExpect(
                        jsonPath("$.totalPages")
                                .value(2)
                );
    }

    @Test
    void shouldReturnSecondWorkoutPage() throws Exception {
        User user = createUser("manuel@test.com");

        for (int i = 0; i < 12; i++) {
            createWorkout(user.getId(), null);
        }

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                        get("/api/workouts")
                                .param("page", "1")
                                .param("size", "10")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content.length()")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.number")
                                .value(1)
                );
    }

    @Test
    void shouldRejectGettingWorkoutsWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                        get("/api/workouts")
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldIncludeWorkoutExercisesAndSetsInWorkoutList()
            throws Exception {

        User user = createUser("manuel@test.com");

        UUID exerciseId =
                createExercise(user.getId(), "Bench Press");

        Workout workout =
                createWorkout(user.getId(), null);

        WorkoutExercise workoutExercise =
                createWorkoutExercise(
                        workout.getId(),
                        exerciseId,
                        0,
                        "Bench"
                );

        createWorkoutSet(
                workoutExercise.getId(),
                1,
                "80.00",
                10,
                "8.0"
        );

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                        get("/api/workouts")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.content[0].exercises.length()"
                        ).value(1)
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].exercises[0].exerciseId"
                        ).value(exerciseId.toString())
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].exercises[0].sets.length()"
                        ).value(1)
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].exercises[0].sets[0].setNumber"
                        ).value(1)
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].exercises[0].sets[0].weight"
                        ).value(80.00)
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].exercises[0].sets[0].reps"
                        ).value(10)
                )
                .andExpect(
                        jsonPath(
                                "$.content[0].exercises[0].sets[0].rpe"
                        ).value(8.0)
                );
    }

    // -------------------------------------------------------------------------
    // WORKOUT SETS
    // -------------------------------------------------------------------------

    @Test
    void shouldCreateWorkoutSet() throws Exception {
        User user = createUser("manuel@test.com");

        UUID exerciseId =
                createExercise(user.getId(), "Bench Press");

        Workout workout =
                createWorkout(user.getId(), null);

        WorkoutExercise workoutExercise =
                createWorkoutExercise(
                        workout.getId(),
                        exerciseId,
                        0,
                        null
                );

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "setNumber": 1,
                    "weight": 80.00,
                    "reps": 10,
                    "rpe": 8.0
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                                workout.getId(),
                                workoutExercise.getId()
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.setNumber").value(1))
                .andExpect(jsonPath("$.weight").value(80.00))
                .andExpect(jsonPath("$.reps").value(10))
                .andExpect(jsonPath("$.rpe").value(8.0));
    }

    @Test
    void shouldCreateMultipleWorkoutSetsInOrder()
            throws Exception {

        User user = createUser("manuel@test.com");

        UUID exerciseId =
                createExercise(user.getId(), "Bench Press");

        Workout workout =
                createWorkout(user.getId(), null);

        WorkoutExercise workoutExercise =
                createWorkoutExercise(
                        workout.getId(),
                        exerciseId,
                        0,
                        null
                );

        String token = jwtService.generateToken(user.getId());

        String firstRequest = """
                {
                    "setNumber": 2,
                    "weight": 85.00,
                    "reps": 8,
                    "rpe": 9.0
                }
                """;

        String secondRequest = """
                {
                    "setNumber": 1,
                    "weight": 80.00,
                    "reps": 10,
                    "rpe": 8.0
                }
                """;

        mockMvc.perform(
                post(
                        "/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                        workout.getId(),
                        workoutExercise.getId()
                )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstRequest)
        ).andExpect(status().isCreated());

        mockMvc.perform(
                post(
                        "/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                        workout.getId(),
                        workoutExercise.getId()
                )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondRequest)
        ).andExpect(status().isCreated());

        mockMvc.perform(
                        get(
                                "/api/workouts/{id}",
                                workout.getId()
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath(
                                "$.exercises[0].sets.length()"
                        ).value(2)
                )
                .andExpect(
                        jsonPath(
                                "$.exercises[0].sets[0].setNumber"
                        ).value(1)
                )
                .andExpect(
                        jsonPath(
                                "$.exercises[0].sets[1].setNumber"
                        ).value(2)
                );
    }

    @Test
    void shouldRejectDuplicateWorkoutSetNumber()
            throws Exception {

        User user = createUser("manuel@test.com");

        UUID exerciseId =
                createExercise(user.getId(), "Bench Press");

        Workout workout =
                createWorkout(user.getId(), null);

        WorkoutExercise workoutExercise =
                createWorkoutExercise(
                        workout.getId(),
                        exerciseId,
                        0,
                        null
                );

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "setNumber": 1,
                    "weight": 80.00,
                    "reps": 10,
                    "rpe": 8.0
                }
                """;

        mockMvc.perform(
                post(
                        "/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                        workout.getId(),
                        workoutExercise.getId()
                )
                        .header(
                                "Authorization",
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request)
        ).andExpect(status().isCreated());

        mockMvc.perform(
                        post(
                                "/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                                workout.getId(),
                                workoutExercise.getId()
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("DUPLICATE_WORKOUT_SET_NUMBER")
                );
    }

    @Test
    void shouldRejectCreatingSetForNonExistingWorkout()
            throws Exception {

        User user = createUser("manuel@test.com");

        UUID workoutId = UUID.randomUUID();
        UUID workoutExerciseId = UUID.randomUUID();

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "setNumber": 1,
                    "weight": 80.00,
                    "reps": 10,
                    "rpe": 8.0
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                                workoutId,
                                workoutExerciseId
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectCreatingSetForAnotherUsersWorkout()
            throws Exception {

        User owner = createUser("owner@test.com");
        User otherUser = createUser("other@test.com");

        UUID exerciseId =
                createExercise(owner.getId(), "Bench Press");

        Workout workout =
                createWorkout(owner.getId(), null);

        WorkoutExercise workoutExercise =
                createWorkoutExercise(
                        workout.getId(),
                        exerciseId,
                        0,
                        null
                );

        String token = jwtService.generateToken(otherUser.getId());

        String request = """
                {
                    "setNumber": 1,
                    "weight": 80.00,
                    "reps": 10,
                    "rpe": 8.0
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                                workout.getId(),
                                workoutExercise.getId()
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectCreatingSetForExerciseFromAnotherWorkout()
            throws Exception {

        User user = createUser("manuel@test.com");

        UUID exerciseId =
                createExercise(user.getId(), "Bench Press");

        Workout firstWorkout =
                createWorkout(user.getId(), null);

        Workout secondWorkout =
                createWorkout(user.getId(), null);

        WorkoutExercise workoutExercise =
                createWorkoutExercise(
                        firstWorkout.getId(),
                        exerciseId,
                        0,
                        null
                );

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "setNumber": 1,
                    "weight": 80.00,
                    "reps": 10,
                    "rpe": 8.0
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                                secondWorkout.getId(),
                                workoutExercise.getId()
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectCreatingSetWithoutAuthentication()
            throws Exception {

        User user = createUser("manuel@test.com");

        UUID exerciseId =
                createExercise(user.getId(), "Bench Press");

        Workout workout =
                createWorkout(user.getId(), null);

        WorkoutExercise workoutExercise =
                createWorkoutExercise(
                        workout.getId(),
                        exerciseId,
                        0,
                        null
                );

        String request = """
                {
                    "setNumber": 1,
                    "weight": 80.00,
                    "reps": 10,
                    "rpe": 8.0
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                                workout.getId(),
                                workoutExercise.getId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectSetWithInvalidWeight()
            throws Exception {

        User user = createUser("manuel@test.com");

        UUID exerciseId =
                createExercise(user.getId(), "Bench Press");

        Workout workout =
                createWorkout(user.getId(), null);

        WorkoutExercise workoutExercise =
                createWorkoutExercise(
                        workout.getId(),
                        exerciseId,
                        0,
                        null
                );

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "setNumber": 1,
                    "weight": -1.00,
                    "reps": 10,
                    "rpe": 8.0
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                                workout.getId(),
                                workoutExercise.getId()
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                );
    }

    @Test
    void shouldRejectSetWithInvalidReps()
            throws Exception {

        User user = createUser("manuel@test.com");

        UUID exerciseId =
                createExercise(user.getId(), "Bench Press");

        Workout workout =
                createWorkout(user.getId(), null);

        WorkoutExercise workoutExercise =
                createWorkoutExercise(
                        workout.getId(),
                        exerciseId,
                        0,
                        null
                );

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "setNumber": 1,
                    "weight": 80.00,
                    "reps": 0,
                    "rpe": 8.0
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                                workout.getId(),
                                workoutExercise.getId()
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                );
    }

    @Test
    void shouldRejectSetWithInvalidRpe()
            throws Exception {

        User user = createUser("manuel@test.com");

        UUID exerciseId =
                createExercise(user.getId(), "Bench Press");

        Workout workout =
                createWorkout(user.getId(), null);

        WorkoutExercise workoutExercise =
                createWorkoutExercise(
                        workout.getId(),
                        exerciseId,
                        0,
                        null
                );

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "setNumber": 1,
                    "weight": 80.00,
                    "reps": 10,
                    "rpe": 11.0
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                                workout.getId(),
                                workoutExercise.getId()
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                );
    }

    // -------------------------------------------------------------------------
    // PATCH WORKOUT
    // -------------------------------------------------------------------------

    @Test
    void shouldCompleteWorkout() throws Exception {
        User user = createUser("manuel@test.com");

        Workout workout =
                createWorkout(user.getId(), null);

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "completed": true
                }
                """;

        mockMvc.perform(
                        patch("/api/workouts/{id}", workout.getId())
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.id")
                                .value(workout.getId().toString())
                )
                .andExpect(
                        jsonPath("$.completedAt")
                                .isNotEmpty()
                );
    }

    @Test
    void shouldUpdateWorkoutNotes() throws Exception {
        User user = createUser("manuel@test.com");

        Workout workout =
                createWorkout(user.getId(), null);

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "notes": "Good session. Increased bench press."
                }
                """;

        mockMvc.perform(
                        patch("/api/workouts/{id}", workout.getId())
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.notes")
                                .value(
                                        "Good session. Increased bench press."
                                )
                )
                .andExpect(
                        jsonPath("$.completedAt")
                                .value((Object) null)
                );
    }

    @Test
    void shouldCompleteWorkoutAndSaveNotes()
            throws Exception {

        User user = createUser("manuel@test.com");

        Workout workout =
                createWorkout(user.getId(), null);

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "completed": true,
                    "notes": "Excellent workout."
                }
                """;

        mockMvc.perform(
                        patch("/api/workouts/{id}", workout.getId())
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.completedAt")
                                .isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.notes")
                                .value("Excellent workout.")
                );
    }

    @Test
    void shouldUncompleteWorkout() throws Exception {
        User user = createUser("manuel@test.com");

        Workout workout =
                createWorkout(user.getId(), null);

        workout.setCompletedAt(LocalDateTime.now());
        workoutRepository.save(workout);

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "completed": false
                }
                """;

        mockMvc.perform(
                        patch("/api/workouts/{id}", workout.getId())
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.completedAt")
                                .value((Object) null)
                );
    }

    @Test
    void shouldRejectUpdatingAnotherUsersWorkout()
            throws Exception {

        User owner = createUser("owner@test.com");
        User otherUser = createUser("other@test.com");

        Workout workout =
                createWorkout(owner.getId(), null);

        String token = jwtService.generateToken(otherUser.getId());

        String request = """
                {
                    "completed": true,
                    "notes": "Should not be allowed."
                }
                """;

        mockMvc.perform(
                        patch("/api/workouts/{id}", workout.getId())
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectUpdatingNonExistingWorkout()
            throws Exception {

        User user = createUser("manuel@test.com");

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "completed": true
                }
                """;

        mockMvc.perform(
                        patch(
                                "/api/workouts/{id}",
                                UUID.randomUUID()
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectUpdatingWorkoutWithoutAuthentication()
            throws Exception {

        User user = createUser("manuel@test.com");

        Workout workout =
                createWorkout(user.getId(), null);

        String request = """
                {
                    "completed": true
                }
                """;

        mockMvc.perform(
                        patch("/api/workouts/{id}", workout.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectWorkoutNotesLongerThan500Characters()
            throws Exception {

        User user = createUser("manuel@test.com");

        Workout workout =
                createWorkout(user.getId(), null);

        String token = jwtService.generateToken(user.getId());

        String longNotes = "a".repeat(501);

        String request = """
                {
                    "notes": "%s"
                }
                """.formatted(longNotes);

        mockMvc.perform(
                        patch("/api/workouts/{id}", workout.getId())
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                );
    }

    // -------------------------------------------------------------------------
    // PERSISTENCE
    // -------------------------------------------------------------------------

    @Test
    @Transactional
    void shouldPersistWorkoutAndWorkoutExercises()
            throws Exception {

        User user = createUser("manuel@test.com");

        UUID exerciseId =
                createExercise(user.getId(), "Squat");

        Routine routine =
                createRoutine(
                        user.getId(),
                        "Legs",
                        null
                );

        createRoutineExercise(
                routine.getId(),
                exerciseId,
                0,
                4,
                8,
                150,
                "Heavy set"
        );

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "routineId": "%s"
                }
                """.formatted(routine.getId());

        mockMvc.perform(
                        post("/api/workouts")
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated());

        List<Workout> workouts =
                workoutRepository.findAll();

        assertEquals(1, workouts.size());

        Workout workout = workouts.get(0);

        assertEquals(
                user.getId(),
                workout.getUserId()
        );

        assertEquals(
                routine.getId(),
                workout.getRoutineId()
        );

        assertNotNull(workout.getStartedAt());
        assertNull(workout.getCompletedAt());

        List<WorkoutExercise> workoutExercises =
                workoutExerciseRepository
                        .findByWorkoutIdOrderByPositionAsc(
                                workout.getId()
                        );

        assertEquals(1, workoutExercises.size());

        WorkoutExercise workoutExercise =
                workoutExercises.get(0);

        assertEquals(
                exerciseId,
                workoutExercise.getExerciseId()
        );

        assertEquals(
                0,
                workoutExercise.getPosition()
        );

        assertEquals(
                "Heavy set",
                workoutExercise.getNotes()
        );
    }

    @Test
    @Transactional
    void shouldPersistWorkoutSet() throws Exception {
        User user = createUser("workout-set@test.com");

        UUID exerciseId =
                createExercise(user.getId(), "Bench Press");

        Workout workout =
                createWorkout(user.getId(), null);

        WorkoutExercise workoutExercise =
                createWorkoutExercise(
                        workout.getId(),
                        exerciseId,
                        0,
                        null
                );

        String token = jwtService.generateToken(user.getId());

        String request = """
                {
                    "setNumber": 1,
                    "weight": 100.50,
                    "reps": 8,
                    "rpe": 9.0
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                                workout.getId(),
                                workoutExercise.getId()
                        )
                                .header(
                                        "Authorization",
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(request)
                )
                .andExpect(status().isCreated());

        List<WorkoutSet> sets =
                workoutSetRepository
                        .findByWorkoutExerciseIdOrderBySetNumberAsc(
                                workoutExercise.getId()
                        );

        assertEquals(1, sets.size());

        WorkoutSet set = sets.get(0);

        assertEquals(1, set.getSetNumber());
        assertEquals(
                new BigDecimal("100.50"),
                set.getWeight()
        );
        assertEquals(8, set.getReps());
        assertEquals(
                new BigDecimal("9.0"),
                set.getRpe()
        );
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

    @Test
    void shouldAllowOnlyConfiguredCorsOriginsForPreflight() throws Exception {
        mockMvc.perform(options("/api/workouts")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("http://localhost:4200",
                        result.getResponse().getHeader("Access-Control-Allow-Origin")));

        mockMvc.perform(options("/api/workouts")
                        .header("Origin", "https://gym-tracker-eight-dun.vercel.app")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("https://gym-tracker-eight-dun.vercel.app",
                        result.getResponse().getHeader("Access-Control-Allow-Origin")));

        mockMvc.perform(options("/api/workouts")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldImportHistoricalWorkoutIdempotently() throws Exception {
        User user = createUser("historical-import@test.com");
        UUID exerciseId = createExercise(user.getId(), "Bench Press");
        Routine routine = createRoutine(user.getId(), "Historical routine", null);
        createRoutineExercise(routine.getId(), exerciseId, 0, 3, 10, 90, null);

        UUID clientId = UUID.randomUUID();
        String request = """
                {
                  "clientId": "%s",
                  "routineId": "%s",
                  "startedAt": "2024-06-01T10:00:00",
                  "completedAt": "2024-06-01T11:15:00",
                  "notes": "Imported from local storage"
                }
                """.formatted(clientId, routine.getId());
        String token = jwtService.generateToken(user.getId());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/workouts")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.clientId").value(clientId.toString()))
                    .andExpect(jsonPath("$.startedAt").value("2024-06-01T10:00:00"))
                    .andExpect(jsonPath("$.completedAt").value("2024-06-01T11:15:00"))
                    .andExpect(jsonPath("$.notes").value("Imported from local storage"));
        }

        assertEquals(1, workoutRepository.count());
    }

    @Test
    void shouldImportWorkoutSetIdempotently() throws Exception {
        User user = createUser("set-import@test.com");
        UUID exerciseId = createExercise(user.getId(), "Row");
        Routine routine = createRoutine(user.getId(), "Set import", null);
        Workout workout = createWorkout(user.getId(), routine.getId());
        WorkoutExercise workoutExercise = createWorkoutExercise(
                workout.getId(), exerciseId, 0, null);
        UUID clientId = UUID.randomUUID();
        String request = """
                { "clientId": "%s", "setNumber": 1, "weight": 60.00, "reps": 10, "rpe": 8.0 }
                """.formatted(clientId);
        String token = jwtService.generateToken(user.getId());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets",
                            workout.getId(), workoutExercise.getId())
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.clientId").value(clientId.toString()));
        }

        assertEquals(1, workoutSetRepository.count());
    }

    private User createUser(String email) {
        User user = new User();

        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(
                passwordEncoder.encode("password123")
        );

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
        exercise.setSource(ExerciseSource.USER);
        exercise.setSourceId(null);
        exercise.setCategory(null);
        exercise.setEquipment(null);
        exercise.setTargetMuscle(null);
        exercise.setMuscleGroup(null);
        exercise.setSecondaryMuscles(null);

        LocalDateTime now = LocalDateTime.now();

        exercise.setCreatedAt(now);
        exercise.setUpdatedAt(now);
        exercise.setDeletedAt(null);

        return exerciseRepository
                .save(exercise)
                .getId();
    }

    private Routine createRoutine(
            UUID userId,
            String name,
            String description) {

        Routine routine = new Routine();

        routine.setId(UUID.randomUUID());
        routine.setUserId(userId);
        routine.setName(name);
        routine.setDescription(description);

        LocalDateTime now = LocalDateTime.now();

        routine.setCreatedAt(now);
        routine.setUpdatedAt(now);
        routine.setDeletedAt(null);

        return routineRepository.save(routine);
    }

    private void createRoutineExercise(
            UUID routineId,
            UUID exerciseId,
            int position,
            int sets,
            int targetReps,
            int restSeconds,
            String notes) {

        RoutineExercise routineExercise =
                new RoutineExercise();

        routineExercise.setId(UUID.randomUUID());
        routineExercise.setRoutineId(routineId);
        routineExercise.setExerciseId(exerciseId);
        routineExercise.setPosition(position);
        routineExercise.setSets(sets);
        routineExercise.setTargetReps(targetReps);
        routineExercise.setRestSeconds(restSeconds);
        routineExercise.setNotes(notes);

        routineExerciseRepository.save(routineExercise);
    }

    private Workout createWorkout(
            UUID userId,
            UUID routineId) {

        Workout workout = new Workout();

        workout.setId(UUID.randomUUID());
        workout.setUserId(userId);
        workout.setRoutineId(routineId);
        workout.setStartedAt(LocalDateTime.now());
        workout.setCompletedAt(null);
        workout.setNotes(null);
        workout.setCreatedAt(LocalDateTime.now());

        return workoutRepository.save(workout);
    }

    private WorkoutExercise createWorkoutExercise(
            UUID workoutId,
            UUID exerciseId,
            int position,
            String notes) {

        WorkoutExercise workoutExercise =
                new WorkoutExercise();

        workoutExercise.setId(UUID.randomUUID());
        workoutExercise.setWorkoutId(workoutId);
        workoutExercise.setExerciseId(exerciseId);
        workoutExercise.setPosition(position);
        workoutExercise.setNotes(notes);

        return workoutExerciseRepository.save(
                workoutExercise
        );
    }

    private WorkoutSet createWorkoutSet(
            UUID workoutExerciseId,
            int setNumber,
            String weight,
            int reps,
            String rpe) {

        WorkoutSet workoutSet =
                new WorkoutSet();

        workoutSet.setId(UUID.randomUUID());
        workoutSet.setWorkoutExerciseId(
                workoutExerciseId
        );
        workoutSet.setSetNumber(setNumber);
        workoutSet.setWeight(
                new BigDecimal(weight)
        );
        workoutSet.setReps(reps);
        workoutSet.setRpe(
                rpe == null
                        ? null
                        : new BigDecimal(rpe)
        );

        return workoutSetRepository.save(
                workoutSet
        );
    }
}
