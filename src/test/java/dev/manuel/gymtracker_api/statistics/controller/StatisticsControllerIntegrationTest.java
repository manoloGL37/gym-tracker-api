package dev.manuel.gymtracker_api.statistics.controller;

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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StatisticsControllerIntegrationTest {

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

    @Test
    void shouldReturnStatisticsSummaryForUser() throws Exception {

        User user = createUser("statistics@test.com");

        Exercise exercise = createExercise();

        Routine routine = createRoutine(user);

        RoutineExercise routineExercise = createRoutineExercise(routine, exercise);

        Workout workout = createWorkout(
                user,
                routine,
                LocalDateTime.of(2026, 9, 5, 10, 0));

        WorkoutExercise workoutExercise = createWorkoutExercise(workout, exercise);

        createWorkoutSet(
                workoutExercise,
                1,
                new BigDecimal("50.00"),
                10);

        createWorkoutSet(
                workoutExercise,
                2,
                new BigDecimal("60.00"),
                8);

        createWorkoutSet(
                workoutExercise,
                3,
                new BigDecimal("60.00"),
                6);

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                get("/api/statistics/summary")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-09")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from")
                        .value("2026-09-01"))
                .andExpect(jsonPath("$.to")
                        .value("2026-09-09"))
                .andExpect(jsonPath("$.workouts")
                        .value(1))
                .andExpect(jsonPath("$.sets")
                        .value(3))
                .andExpect(jsonPath("$.reps")
                        .value(24))
                .andExpect(jsonPath("$.volume")
                        .value(1340.00))
                .andExpect(jsonPath("$.maxWeight")
                        .value(60.00));
    }

    @Test
    void shouldNotIncludeOtherUsersWorkouts() throws Exception {

        User userA = createUser("statistics-a@test.com");
        User userB = createUser("statistics-b@test.com");

        Exercise exercise = createExercise();

        Routine routineB = createRoutine(userB);

        createRoutineExercise(routineB, exercise);

        Workout workoutB = createWorkout(
                userB,
                routineB,
                LocalDateTime.of(2026, 9, 5, 10, 0));

        WorkoutExercise workoutExerciseB = createWorkoutExercise(workoutB, exercise);

        createWorkoutSet(
                workoutExerciseB,
                1,
                new BigDecimal("100.00"),
                10);

        String token = jwtService.generateToken(userA.getId());

        mockMvc.perform(
                get("/api/statistics/summary")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-09")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workouts")
                        .value(0))
                .andExpect(jsonPath("$.sets")
                        .value(0))
                .andExpect(jsonPath("$.reps")
                        .value(0))
                .andExpect(jsonPath("$.volume")
                        .value(0))
                .andExpect(jsonPath("$.maxWeight")
                        .value(0));
    }

    @Test
    void shouldRequireAuthentication() throws Exception {

        mockMvc.perform(
                get("/api/statistics/summary")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-09"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidDateRange() throws Exception {

        User user = createUser("statistics-invalid-range@test.com");

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                get("/api/statistics/summary")
                        .param("from", "2026-09-10")
                        .param("to", "2026-09-01")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCompareTwoPeriods() throws Exception {

        User user = createUser("statistics-comparison@test.com");

        Exercise exercise = createExercise();

        Routine routine = createRoutine(user);
        createRoutineExercise(routine, exercise);

        // Periodo anterior
        Workout previousWorkout = createWorkout(
                user,
                routine,
                LocalDateTime.of(2026, 8, 30, 10, 0));

        WorkoutExercise previousWorkoutExercise = createWorkoutExercise(previousWorkout, exercise);

        createWorkoutSet(
                previousWorkoutExercise,
                1,
                new BigDecimal("50.00"),
                10);

        // 500 kg de volumen anterior

        // Periodo actual
        Workout currentWorkout = createWorkout(
                user,
                routine,
                LocalDateTime.of(2026, 9, 6, 10, 0));

        WorkoutExercise currentWorkoutExercise = createWorkoutExercise(currentWorkout, exercise);

        createWorkoutSet(
                currentWorkoutExercise,
                1,
                new BigDecimal("60.00"),
                10);

        // 600 kg de volumen actual

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                get("/api/statistics/comparison")
                        .param("currentFrom", "2026-09-01")
                        .param("currentTo", "2026-09-07")
                        .param("previousFrom", "2026-08-24")
                        .param("previousTo", "2026-08-31")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())

                .andExpect(jsonPath("$.current.workouts")
                        .value(1))
                .andExpect(jsonPath("$.current.sets")
                        .value(1))
                .andExpect(jsonPath("$.current.reps")
                        .value(10))
                .andExpect(jsonPath("$.current.volume")
                        .value(600.00))
                .andExpect(jsonPath("$.current.maxWeight")
                        .value(60.00))

                .andExpect(jsonPath("$.previous.workouts")
                        .value(1))
                .andExpect(jsonPath("$.previous.sets")
                        .value(1))
                .andExpect(jsonPath("$.previous.reps")
                        .value(10))
                .andExpect(jsonPath("$.previous.volume")
                        .value(500.00))
                .andExpect(jsonPath("$.previous.maxWeight")
                        .value(50.00))

                // (600 - 500) / 500 × 100 = 20%
                .andExpect(jsonPath("$.changes.workouts")
                        .value(0.0))
                .andExpect(jsonPath("$.changes.sets")
                        .value(0.0))
                .andExpect(jsonPath("$.changes.reps")
                        .value(0.0))
                .andExpect(jsonPath("$.changes.volume")
                        .value(20.0))
                .andExpect(jsonPath("$.changes.maxWeight")
                        .value(20.0));
    }

    @Test
    void shouldNotIncludeOtherUsersInComparison() throws Exception {

        User userA = createUser("statistics-comparison-a@test.com");
        User userB = createUser("statistics-comparison-b@test.com");

        Exercise exercise = createExercise();

        Routine routineB = createRoutine(userB);
        createRoutineExercise(routineB, exercise);

        Workout workoutB = createWorkout(
                userB,
                routineB,
                LocalDateTime.of(2026, 9, 6, 10, 0));

        WorkoutExercise workoutExerciseB = createWorkoutExercise(workoutB, exercise);

        createWorkoutSet(
                workoutExerciseB,
                1,
                new BigDecimal("100.00"),
                10);

        String token = jwtService.generateToken(userA.getId());

        mockMvc.perform(
                get("/api/statistics/comparison")
                        .param("currentFrom", "2026-09-01")
                        .param("currentTo", "2026-09-07")
                        .param("previousFrom", "2026-08-24")
                        .param("previousTo", "2026-08-31")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.workouts")
                        .value(0))
                .andExpect(jsonPath("$.current.volume")
                        .value(0))
                .andExpect(jsonPath("$.previous.workouts")
                        .value(0))
                .andExpect(jsonPath("$.previous.volume")
                        .value(0));
    }

    @Test
    void shouldRequireAuthenticationForComparison() throws Exception {

        mockMvc.perform(
                get("/api/statistics/comparison")
                        .param("currentFrom", "2026-09-01")
                        .param("currentTo", "2026-09-07")
                        .param("previousFrom", "2026-08-24")
                        .param("previousTo", "2026-08-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidComparisonDateRange() throws Exception {

        User user = createUser(
                "statistics-comparison-invalid@test.com");

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                get("/api/statistics/comparison")
                        .param("currentFrom", "2026-09-10")
                        .param("currentTo", "2026-09-01")
                        .param("previousFrom", "2026-08-24")
                        .param("previousTo", "2026-08-31")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnStatisticsEvolutionGroupedByDay() throws Exception {
        User user = createUser("evolution@test.com");
        Exercise exercise = createExercise();
        Routine routine = createRoutine(user);
        createRoutineExercise(routine, exercise);

        Workout workout1 = createWorkout(
                user,
                routine,
                LocalDateTime.of(2026, 9, 1, 10, 0));

        WorkoutExercise workoutExercise1 = createWorkoutExercise(workout1, exercise);

        createWorkoutSet(
                workoutExercise1,
                1,
                new BigDecimal("50.00"),
                10);

        Workout workout2 = createWorkout(
                user,
                routine,
                LocalDateTime.of(2026, 9, 1, 18, 0));

        WorkoutExercise workoutExercise2 = createWorkoutExercise(workout2, exercise);

        createWorkoutSet(
                workoutExercise2,
                1,
                new BigDecimal("60.00"),
                10);

        Workout workout3 = createWorkout(
                user,
                routine,
                LocalDateTime.of(2026, 9, 3, 10, 0));

        WorkoutExercise workoutExercise3 = createWorkoutExercise(workout3, exercise);

        createWorkoutSet(
                workoutExercise3,
                1,
                new BigDecimal("70.00"),
                10);

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                get("/api/statistics/evolution")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-03")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-01"))
                .andExpect(jsonPath("$.to").value("2026-09-03"))
                .andExpect(jsonPath("$.data.length()").value(2))

                .andExpect(jsonPath("$.data[0].date").value("2026-09-01"))
                .andExpect(jsonPath("$.data[0].workouts").value(2))
                .andExpect(jsonPath("$.data[0].sets").value(2))
                .andExpect(jsonPath("$.data[0].reps").value(20))
                .andExpect(jsonPath("$.data[0].volume").value(1100.00))

                .andExpect(jsonPath("$.data[1].date").value("2026-09-03"))
                .andExpect(jsonPath("$.data[1].workouts").value(1))
                .andExpect(jsonPath("$.data[1].sets").value(1))
                .andExpect(jsonPath("$.data[1].reps").value(10))
                .andExpect(jsonPath("$.data[1].volume").value(700.00));
    }

    @Test
    void shouldNotIncludeOtherUsersInEvolution() throws Exception {
        User userA = createUser("evolution-a@test.com");
        User userB = createUser("evolution-b@test.com");

        Exercise exercise = createExercise();

        Routine routineA = createRoutine(userA);
        createRoutineExercise(routineA, exercise);

        Routine routineB = createRoutine(userB);
        createRoutineExercise(routineB, exercise);

        Workout workoutA = createWorkout(
                userA,
                routineA,
                LocalDateTime.of(2026, 9, 1, 10, 0));

        WorkoutExercise workoutExerciseA = createWorkoutExercise(workoutA, exercise);

        createWorkoutSet(
                workoutExerciseA,
                1,
                new BigDecimal("50.00"),
                10);

        Workout workoutB = createWorkout(
                userB,
                routineB,
                LocalDateTime.of(2026, 9, 1, 12, 0));

        WorkoutExercise workoutExerciseB = createWorkoutExercise(workoutB, exercise);

        createWorkoutSet(
                workoutExerciseB,
                1,
                new BigDecimal("999.00"),
                10);

        String token = jwtService.generateToken(userA.getId());

        mockMvc.perform(
                get("/api/statistics/evolution")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-01")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].workouts").value(1))
                .andExpect(jsonPath("$.data[0].volume").value(500.00));
    }

    @Test
    void shouldRequireAuthenticationForEvolution() throws Exception {
        mockMvc.perform(
                get("/api/statistics/evolution")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-07"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidEvolutionDateRange() throws Exception {
        User user = createUser(
                "evolution-invalid@test.com");

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                get("/api/statistics/evolution")
                        .param("from", "2026-09-10")
                        .param("to", "2026-09-01")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnStatisticsForExercise() throws Exception {
        User user = createUser("exercise-stats@test.com");
        Exercise exercise = createExercise();

        Routine routine = createRoutine(user);
        createRoutineExercise(routine, exercise);

        Workout workout1 = createWorkout(
                user,
                routine,
                LocalDateTime.of(2026, 9, 1, 10, 0));

        WorkoutExercise workoutExercise1 = createWorkoutExercise(workout1, exercise);

        createWorkoutSet(
                workoutExercise1,
                1,
                new BigDecimal("50.00"),
                10);

        Workout workout2 = createWorkout(
                user,
                routine,
                LocalDateTime.of(2026, 9, 3, 10, 0));

        WorkoutExercise workoutExercise2 = createWorkoutExercise(workout2, exercise);

        createWorkoutSet(
                workoutExercise2,
                1,
                new BigDecimal("60.00"),
                8);

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                get("/api/statistics/exercises/" + exercise.getId())
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-03")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseId").value(exercise.getId().toString()))
                .andExpect(jsonPath("$.from").value("2026-09-01"))
                .andExpect(jsonPath("$.to").value("2026-09-03"))
                .andExpect(jsonPath("$.totalSets").value(2))
                .andExpect(jsonPath("$.totalReps").value(18))
                .andExpect(jsonPath("$.totalVolume").value(980.00))
                .andExpect(jsonPath("$.maxWeight").value(60.00))
                .andExpect(jsonPath("$.evolution.length()").value(2))
                .andExpect(jsonPath("$.evolution[0].date").value("2026-09-01"))
                .andExpect(jsonPath("$.evolution[0].volume").value(500.00))
                .andExpect(jsonPath("$.evolution[0].maxWeight").value(50.00))
                .andExpect(jsonPath("$.evolution[1].date").value("2026-09-03"))
                .andExpect(jsonPath("$.evolution[1].volume").value(480.00))
                .andExpect(jsonPath("$.evolution[1].maxWeight").value(60.00));
    }

    @Test
    void shouldOnlyIncludeSelectedExercise() throws Exception {

        User user = createUser("exercise-filter@test.com");

        Exercise exerciseA = createExercise("statistics-exercise-a");
        Exercise exerciseB = createExercise("statistics-exercise-b");

        Routine routine = createRoutine(user);

        createRoutineExercise(routine, exerciseA, 0);
        createRoutineExercise(routine, exerciseB, 1);

        Workout workout = createWorkout(
                user,
                routine,
                LocalDateTime.of(2026, 9, 1, 10, 0));

        WorkoutExercise workoutExerciseA = createWorkoutExercise(workout, exerciseA, 0);

        createWorkoutSet(
                workoutExerciseA,
                1,
                new BigDecimal("50.00"),
                10);

        WorkoutExercise workoutExerciseB = createWorkoutExercise(workout, exerciseB, 1);

        createWorkoutSet(
                workoutExerciseB,
                1,
                new BigDecimal("999.00"),
                10);

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                get("/api/statistics/exercises/" + exerciseA.getId())
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-01")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSets").value(1))
                .andExpect(jsonPath("$.totalReps").value(10))
                .andExpect(jsonPath("$.totalVolume").value(500.00))
                .andExpect(jsonPath("$.maxWeight").value(50.00));
    }

    @Test
    void shouldRequireAuthenticationForExerciseStatistics()
            throws Exception {

        Exercise exercise = createExercise();

        mockMvc.perform(
                get("/api/statistics/exercises/" + exercise.getId())
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-07"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidExerciseStatisticsDateRange()
            throws Exception {

        User user = createUser("exercise-invalid@test.com");
        Exercise exercise = createExercise();

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                get("/api/statistics/exercises/" + exercise.getId())
                        .param("from", "2026-09-10")
                        .param("to", "2026-09-01")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnEmptyExerciseStatisticsWhenNoDataExists() throws Exception {

        User user = createUser("exercise-empty@test.com");
        Exercise exercise = createExercise("statistics-empty-exercise");

        String token = jwtService.generateToken(user.getId());

        mockMvc.perform(
                get("/api/statistics/exercises/" + exercise.getId())
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-07")
                        .header(
                                "Authorization",
                                "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSets").value(0))
                .andExpect(jsonPath("$.totalReps").value(0))
                .andExpect(jsonPath("$.totalVolume").value(0.00))
                .andExpect(jsonPath("$.maxWeight").value(0.00))
                .andExpect(jsonPath("$.evolution").isEmpty());
    }

    private User createUser(String email) {

        User user = new User();

        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(
                passwordEncoder.encode("Password123!"));

        LocalDateTime now = LocalDateTime.now();

        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        return userRepository.save(user);
    }

    private Exercise createExercise() {
        return createExercise("statistics-test-exercise");
    }

    private Exercise createExercise(String sourceId) {
        Exercise exercise = new Exercise();

        exercise.setId(UUID.randomUUID());
        exercise.setSource(ExerciseSource.EXERCISES_DATASET);
        exercise.setSourceId(sourceId);
        exercise.setCategory("strength");
        exercise.setEquipment("barbell");
        exercise.setTargetMuscle("chest");
        exercise.setMuscleGroup("chest");

        LocalDateTime now = LocalDateTime.now();

        exercise.setCreatedAt(now);
        exercise.setUpdatedAt(now);

        return exerciseRepository.save(exercise);
    }

    private Routine createRoutine(User user) {

        Routine routine = new Routine();

        routine.setId(UUID.randomUUID());
        routine.setUserId(user.getId());
        routine.setName("Statistics Routine");
        routine.setDescription("Statistics test routine");

        LocalDateTime now = LocalDateTime.now();

        routine.setCreatedAt(now);
        routine.setUpdatedAt(now);

        return routineRepository.save(routine);
    }

    private RoutineExercise createRoutineExercise(
            Routine routine,
            Exercise exercise) {
        return createRoutineExercise(routine, exercise, 0);
    }

    private RoutineExercise createRoutineExercise(
            Routine routine,
            Exercise exercise,
            int position) {

        RoutineExercise routineExercise = new RoutineExercise();

        routineExercise.setId(UUID.randomUUID());
        routineExercise.setRoutineId(routine.getId());
        routineExercise.setExerciseId(exercise.getId());
        routineExercise.setPosition(position);
        routineExercise.setSets(3);
        routineExercise.setTargetReps(10);
        routineExercise.setRestSeconds(90);

        return routineExerciseRepository.save(routineExercise);
    }

    private Workout createWorkout(
            User user,
            Routine routine,
            LocalDateTime startedAt) {

        Workout workout = new Workout();

        workout.setId(UUID.randomUUID());
        workout.setUserId(user.getId());
        workout.setRoutineId(routine.getId());
        workout.setStartedAt(startedAt);
        workout.setCreatedAt(startedAt);

        return workoutRepository.save(workout);
    }

    private WorkoutExercise createWorkoutExercise(
            Workout workout,
            Exercise exercise) {

        return createWorkoutExercise(workout, exercise, 0);
    }

    private WorkoutExercise createWorkoutExercise(
            Workout workout,
            Exercise exercise,
            int position) {

        WorkoutExercise workoutExercise = new WorkoutExercise();

        workoutExercise.setId(UUID.randomUUID());
        workoutExercise.setWorkoutId(workout.getId());
        workoutExercise.setExerciseId(exercise.getId());
        workoutExercise.setPosition(position);

        return workoutExerciseRepository.save(workoutExercise);
    }

    private WorkoutSet createWorkoutSet(
            WorkoutExercise workoutExercise,
            int setNumber,
            BigDecimal weight,
            int reps) {

        WorkoutSet workoutSet = new WorkoutSet();

        workoutSet.setId(UUID.randomUUID());
        workoutSet.setWorkoutExerciseId(
                workoutExercise.getId());
        workoutSet.setSetNumber(setNumber);
        workoutSet.setWeight(weight);
        workoutSet.setReps(reps);

        return workoutSetRepository.save(workoutSet);
    }
}