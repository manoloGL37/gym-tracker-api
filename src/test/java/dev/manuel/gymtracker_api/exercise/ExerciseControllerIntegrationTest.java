package dev.manuel.gymtracker_api.exercise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import static org.hamcrest.Matchers.hasSize;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import dev.manuel.gymtracker_api.exercise.model.ExerciseSource;

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
                                ExerciseSource.EXERCISES_DATASET);

                createExercise(
                                userA,
                                "My Custom Press",
                                "chest",
                                "dumbbell",
                                ExerciseSource.USER);

                createExercise(
                                userB,
                                "Private Press",
                                "chest",
                                "barbell",
                                ExerciseSource.USER);

                Exercise deletedExercise = createExercise(
                                null,
                                "Deleted Exercise",
                                "chest",
                                "barbell",
                                ExerciseSource.EXERCISES_DATASET);

                deletedExercise.setDeletedAt(LocalDateTime.now());
                exerciseRepository.save(deletedExercise);

                createExercise(
                                null,
                                "Back Squat",
                                "legs",
                                "barbell",
                                ExerciseSource.EXERCISES_DATASET);
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
                                ExerciseSource.EXERCISES_DATASET);

                Exercise middle = createExercise(
                                null,
                                "Order Test Middle",
                                "legs",
                                "barbell",
                                ExerciseSource.EXERCISES_DATASET);

                Exercise newest = createExercise(
                                null,
                                "Order Test Newest",
                                "legs",
                                "barbell",
                                ExerciseSource.EXERCISES_DATASET);

                LocalDateTime now = LocalDateTime.now();

                oldest.setCreatedAt(now.minusDays(2));
                middle.setCreatedAt(now.minusDays(1));
                newest.setCreatedAt(now);

                exerciseRepository.save(oldest);
                exerciseRepository.save(middle);
                exerciseRepository.save(newest);

                Pageable pageable = PageRequest.of(0, 20);

                ExercisePageResponse response = exerciseService.getAvailableExercises(
                                userA,
                                "Order Test",
                                null,
                                null,
                                null,
                                null,
                                pageable);

                assertEquals(3, response.totalElements());

                assertEquals(
                                "Order Test Newest",
                                response.content().get(0)
                                                .translations()
                                                .getFirst()
                                                .name());

                assertEquals(
                                "Order Test Middle",
                                response.content().get(1)
                                                .translations()
                                                .getFirst()
                                                .name());

                assertEquals(
                                "Order Test Oldest",
                                response.content().get(2)
                                                .translations()
                                                .getFirst()
                                                .name());
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
                                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectZeroSize() throws Exception {
                String token = jwtService.generateToken(userA);

                mockMvc.perform(
                                get("/api/exercises")
                                                .param("size", "0")
                                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldRejectSizeAboveMaximum() throws Exception {
                String token = jwtService.generateToken(userA);

                mockMvc.perform(
                                get("/api/exercises")
                                                .param("size", "101")
                                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void shouldCreateCustomExerciseIdempotentlyAndExposeItsCapabilities() throws Exception {
                UUID clientId = UUID.randomUUID();
                long exercisesBefore = exerciseRepository.count();
                String request = """
                                {
                                  "clientId": "%s",
                                  "category": "strength",
                                  "translations": [{ "language": "en", "name": "Imported exercise" }]
                                }
                                """.formatted(clientId);
                String token = jwtService.generateToken(userA);

                for (int attempt = 0; attempt < 2; attempt++) {
                        mockMvc.perform(post("/api/exercises")
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(request))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.clientId").value(clientId.toString()))
                                        .andExpect(jsonPath("$.source").value("USER"))
                                        .andExpect(jsonPath("$.editable").value(true))
                                        .andExpect(jsonPath("$.deletable").value(true));
                }

                assertEquals(exercisesBefore + 1, exerciseRepository.count());
                assertTrue(exerciseRepository.findByOwnerIdAndClientId(userA, clientId).isPresent());
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
                        ExerciseSource source) {

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

        @Test
        void shouldCreatePrivateExercise() throws Exception {

                String token = jwtService.generateToken(userA);

                String request = """
                                {
                                        "category": "strength",
                                        "equipment": "barbell",
                                        "targetMuscle": "chest",
                                        "muscleGroup": "chest",
                                        "secondaryMuscles": ["triceps", "shoulders"],
                                        "translations": [
                                        {
                                                "language": "en",
                                                "name": "My Bench Press",
                                                "instructions": "Press the bar upward."
                                        },
                                        {
                                                "language": "es",
                                                "name": "Mi Press de Banca",
                                                "instructions": "Empuja la barra hacia arriba."
                                        }
                                        ]
                                }
                                """;

                mockMvc.perform(
                                post("/api/exercises")
                                                .header(
                                                                HttpHeaders.AUTHORIZATION,
                                                                "Bearer " + token)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(request))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.category").value("strength"))
                                .andExpect(jsonPath("$.equipment").value("barbell"))
                                .andExpect(jsonPath("$.targetMuscle").value("chest"))
                                .andExpect(jsonPath("$.muscleGroup").value("chest"))
                                .andExpect(jsonPath("$.translations", hasSize(2)))
                                .andExpect(jsonPath("$.translations[0].name").value("My Bench Press"))
                                .andExpect(jsonPath("$.translations[1].name").value("Mi Press de Banca"));
        }

        @Test
        void shouldUpdateOwnPrivateExercise() throws Exception {

                Exercise exercise = createExercise(
                                userA,
                                "My Bench Press",
                                "chest",
                                "barbell",
                                ExerciseSource.USER);

                String token = jwtService.generateToken(userA);

                String request = """
                                {
                                    "category": "strength",
                                    "equipment": "dumbbell",
                                    "targetMuscle": "chest",
                                    "muscleGroup": "chest",
                                    "secondaryMuscles": ["triceps"],
                                    "translations": [
                                        {
                                            "language": "en",
                                            "name": "Updated Bench Press",
                                            "instructions": "New instructions"
                                        }
                                    ]
                                }
                                """;

                mockMvc.perform(
                                put("/api/exercises/{id}", exercise.getId())
                                                .header(
                                                                HttpHeaders.AUTHORIZATION,
                                                                "Bearer " + token)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(request))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id")
                                                .value(exercise.getId().toString()))
                                .andExpect(jsonPath("$.equipment")
                                                .value("dumbbell"))
                                .andExpect(jsonPath("$.translations[0].name")
                                                .value("Updated Bench Press"))
                                .andExpect(jsonPath("$.translations[0].instructions")
                                                .value("New instructions"));
        }

        @Test
        void shouldNotUpdateAnotherUsersExercise() throws Exception {

                Exercise exercise = createExercise(
                                userB,
                                "Private Exercise",
                                "back",
                                "barbell",
                                ExerciseSource.USER);

                String token = jwtService.generateToken(userA);

                String request = """
                                {
                                    "category": "strength",
                                    "equipment": "dumbbell",
                                    "targetMuscle": "back",
                                    "muscleGroup": "back",
                                    "translations": [
                                        {
                                            "language": "en",
                                            "name": "Hacked Exercise",
                                            "instructions": "Should not update"
                                        }
                                    ]
                                }
                                """;

                mockMvc.perform(
                                put("/api/exercises/{id}", exercise.getId())
                                                .header(
                                                                HttpHeaders.AUTHORIZATION,
                                                                "Bearer " + token)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(request))
                                .andExpect(status().isNotFound());
        }

        @Test
        void shouldNotUpdateGlobalExercise() throws Exception {

                Exercise exercise = createExercise(
                                null,
                                "Global Bench Press",
                                "chest",
                                "barbell",
                                ExerciseSource.EXERCISES_DATASET);

                String token = jwtService.generateToken(userA);

                String request = """
                                {
                                    "category": "strength",
                                    "equipment": "dumbbell",
                                    "targetMuscle": "chest",
                                    "muscleGroup": "chest",
                                    "translations": [
                                        {
                                            "language": "en",
                                            "name": "Modified Global Exercise",
                                            "instructions": "Should not update"
                                        }
                                    ]
                                }
                                """;

                mockMvc.perform(
                                put("/api/exercises/{id}", exercise.getId())
                                                .header(
                                                                HttpHeaders.AUTHORIZATION,
                                                                "Bearer " + token)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(request))
                                .andExpect(status().isNotFound());
        }

        @Test
        void shouldRejectExerciseUpdateWithoutJwt() throws Exception {

                Exercise exercise = createExercise(
                                userA,
                                "My Exercise",
                                "chest",
                                "barbell",
                                ExerciseSource.USER);

                String request = """
                                {
                                    "category": "strength",
                                    "equipment": "dumbbell",
                                    "targetMuscle": "chest",
                                    "muscleGroup": "chest",
                                    "translations": [
                                        {
                                            "language": "en",
                                            "name": "Updated Exercise",
                                            "instructions": "Updated"
                                        }
                                    ]
                                }
                                """;

                mockMvc.perform(
                                put("/api/exercises/{id}", exercise.getId())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(request))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldDeleteOwnPrivateExercise() throws Exception {

                Exercise exercise = createExercise(
                                userA,
                                "My Exercise",
                                "chest",
                                "barbell",
                                ExerciseSource.USER);

                String token = jwtService.generateToken(userA);

                mockMvc.perform(
                                delete("/api/exercises/{id}", exercise.getId())
                                                .header(
                                                                HttpHeaders.AUTHORIZATION,
                                                                "Bearer " + token))
                                .andExpect(status().isNoContent());

                Exercise deletedExercise = exerciseRepository.findById(exercise.getId())
                                .orElseThrow();

                assertNotNull(deletedExercise.getDeletedAt());
        }

        @Test
        void shouldNotDeleteAnotherUsersExercise() throws Exception {

                Exercise exercise = createExercise(
                                userB,
                                "Private Exercise",
                                "back",
                                "barbell",
                                ExerciseSource.USER);

                String token = jwtService.generateToken(userA);

                mockMvc.perform(
                                delete("/api/exercises/{id}", exercise.getId())
                                                .header(
                                                                HttpHeaders.AUTHORIZATION,
                                                                "Bearer " + token))
                                .andExpect(status().isNotFound());
        }

        @Test
        void shouldNotDeleteGlobalExercise() throws Exception {

                Exercise exercise = createExercise(
                                null,
                                "Global Exercise",
                                "chest",
                                "barbell",
                                ExerciseSource.EXERCISES_DATASET);

                String token = jwtService.generateToken(userA);

                mockMvc.perform(
                                delete("/api/exercises/{id}", exercise.getId())
                                                .header(
                                                                HttpHeaders.AUTHORIZATION,
                                                                "Bearer " + token))
                                .andExpect(status().isNotFound());
        }

        @Test
        void shouldRejectExerciseDeletionWithoutJwt() throws Exception {

                Exercise exercise = createExercise(
                                userA,
                                "My Exercise",
                                "chest",
                                "barbell",
                                ExerciseSource.USER);

                mockMvc.perform(
                                delete("/api/exercises/{id}", exercise.getId()))
                                .andExpect(status().isUnauthorized());
        }
}
