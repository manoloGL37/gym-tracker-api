package dev.manuel.gymtracker_api.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.data.domain.Sort;

import dev.manuel.gymtracker_api.auth.security.JwtService;
import dev.manuel.gymtracker_api.exercise.dto.ExercisePageResponse;
import dev.manuel.gymtracker_api.exercise.dto.ExerciseResponse;
import dev.manuel.gymtracker_api.exercise.model.Exercise;
import dev.manuel.gymtracker_api.exercise.model.ExerciseTranslation;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseRepository;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseTranslationRepository;
import dev.manuel.gymtracker_api.exercise.service.ExerciseService;
import dev.manuel.gymtracker_api.user.model.User;
import dev.manuel.gymtracker_api.user.repository.UserRepository;


@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ExerciseControllerIntegrationTest {

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
        private final ExerciseService exerciseService;
        private final ExerciseRepository exerciseRepository;
        private final ExerciseTranslationRepository translationRepository;
        private final UserRepository userRepository;
        private final JwtService jwtService;
        private final PasswordEncoder passwordEncoder;

        private UUID userA;
        private UUID userB;

        ExerciseControllerIntegrationTest(
                        MockMvc mockMvc,
                        ExerciseService exerciseService,
                        ExerciseRepository exerciseRepository,
                        ExerciseTranslationRepository translationRepository,
                        UserRepository userRepository,
                        JwtService jwtService,
                        PasswordEncoder passwordEncoder) {
                this.mockMvc = mockMvc;
                this.exerciseService = exerciseService;
                this.exerciseRepository = exerciseRepository;
                this.translationRepository = translationRepository;
                this.userRepository = userRepository;
                this.jwtService = jwtService;
                this.passwordEncoder = passwordEncoder;
        }

        @BeforeEach
        void setUp() {

                translationRepository.deleteAll();
                exerciseRepository.deleteAll();
                userRepository.deleteAll();

                userA = createUser();
                userB = createUser();

                createExercise(
                                null,
                                "Press Bench",
                                "chest",
                                "barbell",
                                "EXERCISES_DATASET");

                createExercise(
                                userA,
                                "My Custom Press",
                                "chest",
                                "dumbbell",
                                "USER");

                createExercise(
                                userB,
                                "Private Press",
                                "chest",
                                "barbell",
                                "USER");

                Exercise deletedExercise = createExercise(
                                null,
                                "Deleted Exercise",
                                "chest",
                                "barbell",
                                "EXERCISES_DATASET");

                deletedExercise.setDeletedAt(LocalDateTime.now());
                exerciseRepository.save(deletedExercise);

                createExercise(
                                null,
                                "Back Squat",
                                "legs",
                                "barbell",
                                "EXERCISES_DATASET");
        }

        @Test
        void shouldReturnGlobalAndOwnExercises() {

                Pageable pageable = PageRequest.of(0, 20);

                ExercisePageResponse response = exerciseService.getAvailableExercises(
                                userA,
                                null,
                                null,
                                null,
                                null,
                                null,
                                pageable);

                assertEquals(3, response.totalElements());
                assertEquals(3, response.content().size());

                assertTrue(
                                response.content()
                                                .stream()
                                                .anyMatch(exercise -> exercise.translations()
                                                                .stream()
                                                                .anyMatch(translation -> "Press Bench".equals(
                                                                                translation.name()))));

                assertTrue(
                                response.content()
                                                .stream()
                                                .anyMatch(exercise -> exercise.translations()
                                                                .stream()
                                                                .anyMatch(translation -> "My Custom Press".equals(
                                                                                translation.name()))));

                assertFalse(
                                response.content()
                                                .stream()
                                                .anyMatch(exercise -> exercise.translations()
                                                                .stream()
                                                                .anyMatch(translation -> "Private Press".equals(
                                                                                translation.name()))));

                assertFalse(
                                response.content()
                                                .stream()
                                                .anyMatch(exercise -> exercise.translations()
                                                                .stream()
                                                                .anyMatch(translation -> "Deleted Exercise".equals(
                                                                                translation.name()))));
        }

        @Test
        void shouldSearchExercisesByName() {

                Pageable pageable = PageRequest.of(0, 20);

                ExercisePageResponse response = exerciseService.getAvailableExercises(
                                userA,
                                "press",
                                null,
                                null,
                                null,
                                null,
                                pageable);

                assertEquals(2, response.totalElements());

                assertTrue(
                                response.content()
                                                .stream()
                                                .allMatch(exercise -> exercise.translations()
                                                                .stream()
                                                                .anyMatch(translation -> translation.name() != null
                                                                                && translation.name()
                                                                                                .toLowerCase()
                                                                                                .contains("press"))));
        }

        @Test
        void shouldFilterExercisesByEquipment() {

                Pageable pageable = PageRequest.of(0, 20);

                ExercisePageResponse response = exerciseService.getAvailableExercises(
                                userA,
                                null,
                                null,
                                "barbell",
                                null,
                                null,
                                pageable);

                assertEquals(2, response.totalElements());
        }

        @Test
        void shouldCombineSearchAndFilters() {

                Pageable pageable = PageRequest.of(0, 20);

                ExercisePageResponse response = exerciseService.getAvailableExercises(
                                userA,
                                "press",
                                "strength",
                                "barbell",
                                null,
                                null,
                                pageable);

                assertEquals(1, response.totalElements());

                assertEquals(
                                "Press Bench",
                                response.content()
                                                .getFirst()
                                                .translations()
                                                .stream()
                                                .filter(translation -> translation.name() != null)
                                                .findFirst()
                                                .orElseThrow()
                                                .name());
        }

        @Test
        void shouldRespectPagination() {

                Pageable pageable = PageRequest.of(0, 2);

                ExercisePageResponse response = exerciseService.getAvailableExercises(
                                userA,
                                null,
                                null,
                                null,
                                null,
                                null,
                                pageable);

                assertEquals(3, response.totalElements());
                assertEquals(2, response.content().size());
                assertEquals(2, response.size());
                assertEquals(2, response.totalPages());
        }

        @Test
        void shouldReturnExercisesOrderedByCreationDateDescending() {
        Exercise oldest = createExercise(
                null,
                "Order Test Oldest",
                "legs",
                "barbell",
                "ORDER_TEST"
        );

        Exercise middle = createExercise(
                null,
                "Order Test Middle",
                "legs",
                "barbell",
                "ORDER_TEST"
        );

        Exercise newest = createExercise(
                null,
                "Order Test Newest",
                "legs",
                "barbell",
                "ORDER_TEST"
        );

        LocalDateTime now = LocalDateTime.now();

        oldest.setCreatedAt(now.minusDays(2));
        middle.setCreatedAt(now.minusDays(1));
        newest.setCreatedAt(now);

        exerciseRepository.save(oldest);
        exerciseRepository.save(middle);
        exerciseRepository.save(newest);

        Pageable pageable = PageRequest.of(0, 20);

        ExercisePageResponse response =
                exerciseService.getAvailableExercises(
                        userA,
                        "Order Test",
                        null,
                        null,
                        null,
                        null,
                        pageable
                );

        assertEquals(3, response.totalElements());

        assertEquals(
                "Order Test Newest",
                response.content().get(0)
                        .translations()
                        .getFirst()
                        .name()
        );

        assertEquals(
                "Order Test Middle",
                response.content().get(1)
                        .translations()
                        .getFirst()
                        .name()
        );

        assertEquals(
                "Order Test Oldest",
                response.content().get(2)
                        .translations()
                        .getFirst()
                        .name()
        );
        }

        @Test
        void shouldRejectRequestWithoutJwt() throws Exception {

                mockMvc.perform(
                                get("/api/exercises"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldAllowRequestWithValidJwt() throws Exception {

                String token = jwtService.generateToken(userA);

                mockMvc.perform(
                                get("/api/exercises")
                                                .header(
                                                                "Authorization",
                                                                "Bearer " + token))
                                .andExpect(status().isOk());
        }

        @Test
        void shouldRejectRequestWithInvalidJwt() throws Exception {

                mockMvc.perform(
                                get("/api/exercises")
                                                .header(
                                                                "Authorization",
                                                                "Bearer invalid-token"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldOnlyReturnGlobalAndOwnExercises() throws Exception {
                String token = jwtService.generateToken(userA);

                String response = mockMvc.perform(
                                get("/api/exercises")
                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                assertTrue(response.contains("Press Bench"));
                assertTrue(response.contains("My Custom Press"));
                assertFalse(response.contains("Private Press"));
                assertFalse(response.contains("Deleted Exercise"));
        }

        @Test
        void shouldRejectNegativePage() throws Exception {
        String token = jwtService.generateToken(userA);

        mockMvc.perform(
                get("/api/exercises")
                        .param("page", "-1")
                        .header("Authorization", "Bearer " + token)
        )
                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectZeroSize() throws Exception {
        String token = jwtService.generateToken(userA);

        mockMvc.perform(
                get("/api/exercises")
                        .param("size", "0")
                        .header("Authorization", "Bearer " + token)
        )
                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectSizeAboveMaximum() throws Exception {
        String token = jwtService.generateToken(userA);

        mockMvc.perform(
                get("/api/exercises")
                        .param("size", "101")
                        .header("Authorization", "Bearer " + token)
        )
                .andExpect(status().isBadRequest());
        }

        private UUID createUser() {

                User user = new User();

                user.setId(UUID.randomUUID());

                user.setEmail(
                                UUID.randomUUID() + "@test.com");

                user.setPasswordHash(
                                passwordEncoder.encode("test-password"));

                LocalDateTime now = LocalDateTime.now();

                user.setCreatedAt(now);
                user.setUpdatedAt(now);

                return userRepository.save(user).getId();
        }

        private Exercise createExercise(
                        UUID ownerId,
                        String name,
                        String muscleGroup,
                        String equipment,
                        String source) {

                Exercise exercise = new Exercise();

                exercise.setId(UUID.randomUUID());
                exercise.setOwnerId(ownerId);
                exercise.setSource(source);
                exercise.setSourceId(UUID.randomUUID().toString());
                exercise.setCategory("strength");
                exercise.setEquipment(equipment);
                exercise.setTargetMuscle(muscleGroup);
                exercise.setMuscleGroup(muscleGroup);
                exercise.setSecondaryMuscles(new String[0]);

                LocalDateTime now = LocalDateTime.now();

                exercise.setCreatedAt(now);
                exercise.setUpdatedAt(now);

                Exercise savedExercise = exerciseRepository.save(exercise);

                ExerciseTranslation translation = new ExerciseTranslation();

                translation.setId(UUID.randomUUID());
                translation.setExerciseId(savedExercise.getId());
                translation.setLanguage("en");
                translation.setName(name);
                translation.setInstructions("Test instructions");

                translationRepository.save(translation);

                return savedExercise;
        }
}