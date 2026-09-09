package dev.manuel.gymtracker_api.exercise.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import dev.manuel.gymtracker_api.common.exception.ResourceNotFoundException;
import dev.manuel.gymtracker_api.exercise.dto.CreateExerciseRequest;
import dev.manuel.gymtracker_api.exercise.dto.ExerciseAliasResponse;
import dev.manuel.gymtracker_api.exercise.dto.ExercisePageResponse;
import dev.manuel.gymtracker_api.exercise.dto.ExerciseResponse;
import dev.manuel.gymtracker_api.exercise.dto.ExerciseTranslationResponse;
import dev.manuel.gymtracker_api.exercise.model.Exercise;
import dev.manuel.gymtracker_api.exercise.model.ExerciseAlias;
import dev.manuel.gymtracker_api.exercise.model.ExerciseSource;
import dev.manuel.gymtracker_api.exercise.model.ExerciseTranslation;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseAliasRepository;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseRepository;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseTranslationRepository;
import dev.manuel.gymtracker_api.exercise.specification.ExerciseSpecification;
import jakarta.transaction.Transactional;

@Service
public class ExerciseService {

        private final ExerciseRepository exerciseRepository;
        private final ExerciseTranslationRepository translationRepository;
        private final ExerciseAliasRepository aliasRepository;

        public ExerciseService(
                        ExerciseRepository exerciseRepository,
                        ExerciseTranslationRepository translationRepository,
                        ExerciseAliasRepository aliasRepository) {
                this.exerciseRepository = exerciseRepository;
                this.translationRepository = translationRepository;
                this.aliasRepository = aliasRepository;
        }

        public ExercisePageResponse getAvailableExercises(
                        UUID userId,
                        String search,
                        String category,
                        String equipment,
                        String muscleGroup,
                        String targetMuscle,
                        Pageable pageable) {

                Specification<Exercise> specification = ExerciseSpecification.availableForUser(userId);

                if (search != null && !search.isBlank()) {
                        specification = specification.and(
                                        ExerciseSpecification.search(search));
                }

                if (category != null && !category.isBlank()) {
                        specification = specification.and(
                                        ExerciseSpecification.hasCategory(category));
                }

                if (equipment != null && !equipment.isBlank()) {
                        specification = specification.and(
                                        ExerciseSpecification.hasEquipment(equipment));
                }

                if (muscleGroup != null && !muscleGroup.isBlank()) {
                        specification = specification.and(
                                        ExerciseSpecification.hasMuscleGroup(muscleGroup));
                }

                if (targetMuscle != null && !targetMuscle.isBlank()) {
                        specification = specification.and(
                                        ExerciseSpecification.hasTargetMuscle(targetMuscle));
                }

                Pageable sortedPageable = PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                Sort.by(
                                                Sort.Order.desc("createdAt"),
                                                Sort.Order.asc("id")));

                Page<Exercise> exercisePage = exerciseRepository.findAll(
                                specification,
                                sortedPageable);

                if (exercisePage.isEmpty()) {
                        return new ExercisePageResponse(
                                        List.of(),
                                        exercisePage.getNumber(),
                                        exercisePage.getSize(),
                                        exercisePage.getTotalElements(),
                                        exercisePage.getTotalPages());
                }

                List<UUID> exerciseIds = exercisePage.getContent()
                                .stream()
                                .map(Exercise::getId)
                                .toList();

                List<ExerciseTranslation> translations = translationRepository.findByExerciseIdIn(exerciseIds);

                List<ExerciseAlias> aliases = aliasRepository.findByExerciseIdIn(exerciseIds);

                Map<UUID, List<ExerciseTranslation>> translationsByExercise = translations.stream()
                                .collect(Collectors.groupingBy(
                                                ExerciseTranslation::getExerciseId));

                Map<UUID, List<ExerciseAlias>> aliasesByExercise = aliases.stream()
                                .collect(Collectors.groupingBy(
                                                ExerciseAlias::getExerciseId));

                List<ExerciseResponse> content = exercisePage.getContent()
                                .stream()
                                .map(exercise -> toResponse(
                                                exercise,
                                                translationsByExercise.getOrDefault(
                                                                exercise.getId(),
                                                                List.of()),
                                                aliasesByExercise.getOrDefault(
                                                                exercise.getId(),
                                                                List.of())))
                                .toList();

                return new ExercisePageResponse(
                                content,
                                exercisePage.getNumber(),
                                exercisePage.getSize(),
                                exercisePage.getTotalElements(),
                                exercisePage.getTotalPages());
        }

        public ExerciseResponse getExerciseById(
                        UUID exerciseId,
                        UUID userId) {
                Exercise exercise = exerciseRepository.findById(exerciseId)
                                .filter(e -> e.getDeletedAt() == null)
                                .filter(e -> e.getOwnerId() == null ||
                                                e.getOwnerId().equals(userId))
                                .orElseThrow(() -> new ResourceNotFoundException("Exercise", exerciseId));

                List<ExerciseTranslation> translations = translationRepository.findByExerciseId(exercise.getId());

                List<ExerciseAlias> aliases = aliasRepository.findByExerciseId(exercise.getId());

                return toResponse(
                                exercise,
                                translations,
                                aliases);
        }

        @Transactional
        public ExerciseResponse createExercise(
                        UUID userId,
                        CreateExerciseRequest request) {
                Exercise exercise = new Exercise();

                exercise.setId(UUID.randomUUID());
                exercise.setOwnerId(userId);
                exercise.setSource(ExerciseSource.USER);
                exercise.setSourceId(null);
                exercise.setCategory(request.category());
                exercise.setEquipment(request.equipment());
                exercise.setTargetMuscle(request.targetMuscle());
                exercise.setMuscleGroup(request.muscleGroup());
                exercise.setSecondaryMuscles(
                                request.secondaryMuscles() != null
                                                ? request.secondaryMuscles().toArray(String[]::new)
                                                : null);

                LocalDateTime now = LocalDateTime.now();
                exercise.setCreatedAt(now);
                exercise.setUpdatedAt(now);
                exercise.setDeletedAt(null);

                Exercise savedExercise = exerciseRepository.save(exercise);

                List<ExerciseTranslation> translations = request.translations()
                                .stream()
                                .map(translationRequest -> {
                                        ExerciseTranslation translation = new ExerciseTranslation();

                                        translation.setId(UUID.randomUUID());
                                        translation.setExerciseId(savedExercise.getId());
                                        translation.setLanguage(
                                                        translationRequest.language());
                                        translation.setName(
                                                        translationRequest.name());
                                        translation.setInstructions(
                                                        translationRequest.instructions());

                                        return translation;
                                })
                                .toList();

                List<ExerciseTranslation> savedTranslations = translationRepository.saveAll(translations);

                return toResponse(
                                savedExercise,
                                savedTranslations,
                                List.of());
        }

        @Transactional
        public ExerciseResponse updateExercise(
                        UUID exerciseId,
                        UUID userId,
                        CreateExerciseRequest request) {
                Exercise exercise = getOwnedExercise(exerciseId, userId);

                exercise.setCategory(request.category());
                exercise.setEquipment(request.equipment());
                exercise.setTargetMuscle(request.targetMuscle());
                exercise.setMuscleGroup(request.muscleGroup());
                exercise.setSecondaryMuscles(
                                request.secondaryMuscles() != null
                                                ? request.secondaryMuscles().toArray(String[]::new)
                                                : null);

                exercise.setUpdatedAt(LocalDateTime.now());

                Exercise savedExercise = exerciseRepository.save(exercise);

                List<ExerciseTranslation> existingTranslations = translationRepository.findByExerciseId(exerciseId);

                Map<String, ExerciseTranslation> translationsByLanguage = existingTranslations.stream()
                                .collect(Collectors.toMap(
                                                ExerciseTranslation::getLanguage,
                                                translation -> translation));

                List<ExerciseTranslation> translations = request.translations()
                                .stream()
                                .map(translationRequest -> {

                                        ExerciseTranslation translation = translationsByLanguage.get(
                                                        translationRequest.language());

                                        if (translation == null) {
                                                translation = new ExerciseTranslation();
                                                translation.setId(UUID.randomUUID());
                                                translation.setExerciseId(exerciseId);
                                                translation.setLanguage(
                                                                translationRequest.language());
                                        }

                                        translation.setName(
                                                        translationRequest.name());
                                        translation.setInstructions(
                                                        translationRequest.instructions());

                                        return translation;
                                })
                                .toList();

                List<ExerciseTranslation> savedTranslations = translationRepository.saveAll(translations);

                return toResponse(
                                savedExercise,
                                savedTranslations,
                                aliasRepository.findByExerciseId(exerciseId));
        }

        @Transactional
        public void deleteExercise(
                        UUID exerciseId,
                        UUID userId) {
                Exercise exercise = getOwnedExercise(exerciseId, userId);

                exercise.setDeletedAt(LocalDateTime.now());
                exercise.setUpdatedAt(LocalDateTime.now());

                exerciseRepository.save(exercise);
        }

        private Exercise getOwnedExercise(
                        UUID exerciseId,
                        UUID userId) {
                return exerciseRepository.findById(exerciseId)
                                .filter(e -> e.getDeletedAt() == null)
                                .filter(e -> e.getOwnerId() != null &&
                                                e.getOwnerId().equals(userId))
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Exercise",
                                                exerciseId));
        }

        private ExerciseResponse toResponse(
                        Exercise exercise,
                        List<ExerciseTranslation> translations,
                        List<ExerciseAlias> aliases) {

                List<ExerciseTranslationResponse> translationResponses = translations.stream()
                                .map(translation -> new ExerciseTranslationResponse(
                                                translation.getLanguage(),
                                                translation.getName(),
                                                translation.getInstructions()))
                                .toList();

                List<ExerciseAliasResponse> aliasResponses = aliases.stream()
                                .map(alias -> new ExerciseAliasResponse(
                                                alias.getLanguage(),
                                                alias.getAlias()))
                                .toList();

                return new ExerciseResponse(
                                exercise.getId(),
                                exercise.getCategory(),
                                exercise.getEquipment(),
                                exercise.getTargetMuscle(),
                                exercise.getMuscleGroup(),
                                exercise.getSecondaryMuscles(),
                                translationResponses,
                                aliasResponses);
        }
}