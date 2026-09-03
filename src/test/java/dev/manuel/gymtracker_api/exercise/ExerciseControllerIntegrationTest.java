package dev.manuel.gymtracker_api.exercise;

import dev.manuel.gymtracker_api.exercise.dto.ExercisePageResponse;
import dev.manuel.gymtracker_api.exercise.model.Exercise;
import dev.manuel.gymtracker_api.exercise.model.ExerciseTranslation;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseRepository;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseTranslationRepository;
import dev.manuel.gymtracker_api.exercise.service.ExerciseService;
import dev.manuel.gymtracker_api.user.model.User;
import dev.manuel.gymtracker_api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class ExerciseControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("gymtracker_test")
                    .withUsername("gymtracker")
                    .withPassword("gymtracker");

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
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
    }

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ExerciseTranslationRepository translationRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID userA;
    private UUID userB;

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
                "EXERCISES_DATASET"
        );

        createExercise(
                userA,
                "My Custom Press",
                "chest",
                "dumbbell",
                "USER"
        );

        createExercise(
                userB,
                "Private Press",
                "chest",
                "barbell",
                "USER"
        );

        Exercise deletedExercise = createExercise(
                null,
                "Deleted Exercise",
                "chest",
                "barbell",
                "EXERCISES_DATASET"
        );

        deletedExercise.setDeletedAt(LocalDateTime.now());
        exerciseRepository.save(deletedExercise);

        createExercise(
                null,
                "Back Squat",
                "legs",
                "barbell",
                "EXERCISES_DATASET"
        );
    }

    @Test
    void shouldReturnGlobalAndOwnExercises() {
        Pageable pageable = PageRequest.of(0, 20);

        ExercisePageResponse response =
                exerciseService.getAvailableExercises(
                        userA,
                        null,
                        null,
                        null,
                        null,
                        null,
                        pageable
                );

        assertEquals(3, response.totalElements());
        assertEquals(3, response.content().size());

        assertTrue(
                response.content()
                        .stream()
                        .anyMatch(exercise ->
                                exercise.translations()
                                        .stream()
                                        .anyMatch(translation ->
                                                "Press Bench".equals(
                                                        translation.name()
                                                )
                                        )
                        )
        );

        assertTrue(
                response.content()
                        .stream()
                        .anyMatch(exercise ->
                                exercise.translations()
                                        .stream()
                                        .anyMatch(translation ->
                                                "My Custom Press".equals(
                                                        translation.name()
                                                )
                                        )
                        )
        );

        assertFalse(
                response.content()
                        .stream()
                        .anyMatch(exercise ->
                                exercise.translations()
                                        .stream()
                                        .anyMatch(translation ->
                                                "Private Press".equals(
                                                        translation.name()
                                                )
                                        )
                        )
        );

        assertFalse(
                response.content()
                        .stream()
                        .anyMatch(exercise ->
                                exercise.translations()
                                        .stream()
                                        .anyMatch(translation ->
                                                "Deleted Exercise".equals(
                                                        translation.name()
                                                )
                                        )
                        )
        );
    }

    @Test
    void shouldSearchExercisesByName() {
        Pageable pageable = PageRequest.of(0, 20);

        ExercisePageResponse response =
                exerciseService.getAvailableExercises(
                        userA,
                        "press",
                        null,
                        null,
                        null,
                        null,
                        pageable
                );

        assertEquals(2, response.totalElements());

        assertTrue(
                response.content()
                        .stream()
                        .allMatch(exercise ->
                                exercise.translations()
                                        .stream()
                                        .anyMatch(translation ->
                                                translation.name() != null
                                                        && translation.name()
                                                        .toLowerCase()
                                                        .contains("press")
                                        )
                        )
        );
    }

    @Test
    void shouldFilterExercisesByEquipment() {
        Pageable pageable = PageRequest.of(0, 20);

        ExercisePageResponse response =
                exerciseService.getAvailableExercises(
                        userA,
                        null,
                        null,
                        "barbell",
                        null,
                        null,
                        pageable
                );

        assertEquals(2, response.totalElements());
    }

    @Test
    void shouldCombineSearchAndFilters() {
        Pageable pageable = PageRequest.of(0, 20);

        ExercisePageResponse response =
                exerciseService.getAvailableExercises(
                        userA,
                        "press",
                        "strength",
                        "barbell",
                        null,
                        null,
                        pageable
                );

        assertEquals(1, response.totalElements());

        assertEquals(
                "Press Bench",
                response.content()
                        .getFirst()
                        .translations()
                        .stream()
                        .filter(translation ->
                                translation.name() != null
                        )
                        .findFirst()
                        .orElseThrow()
                        .name()
        );
    }

    @Test
    void shouldRespectPagination() {
        Pageable pageable = PageRequest.of(0, 2);

        ExercisePageResponse response =
                exerciseService.getAvailableExercises(
                        userA,
                        null,
                        null,
                        null,
                        null,
                        null,
                        pageable
                );

        assertEquals(3, response.totalElements());
        assertEquals(2, response.content().size());
        assertEquals(2, response.size());
        assertEquals(2, response.totalPages());
    }

    private UUID createUser() {
        User user = new User();

        user.setId(UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@test.com");
        user.setPasswordHash("test-password");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user).getId();
    }

    private Exercise createExercise(
            UUID ownerId,
            String name,
            String muscleGroup,
            String equipment,
            String source
    ) {
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
        exercise.setCreatedAt(LocalDateTime.now());
        exercise.setUpdatedAt(LocalDateTime.now());

        Exercise savedExercise = exerciseRepository.save(exercise);

        ExerciseTranslation translation =
                new ExerciseTranslation();

        translation.setId(UUID.randomUUID());
        translation.setExerciseId(savedExercise.getId());
        translation.setLanguage("en");
        translation.setName(name);
        translation.setInstructions("Test instructions");

        translationRepository.save(translation);

        return savedExercise;
    }
}