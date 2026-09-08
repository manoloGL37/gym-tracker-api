package dev.manuel.gymtracker_api.exercise.service;

import dev.manuel.gymtracker_api.exercise.dto.ExerciseAliasResponse;
import dev.manuel.gymtracker_api.exercise.dto.ExercisePageResponse;
import dev.manuel.gymtracker_api.exercise.dto.ExerciseResponse;
import dev.manuel.gymtracker_api.exercise.dto.ExerciseTranslationResponse;
import dev.manuel.gymtracker_api.exercise.model.Exercise;
import dev.manuel.gymtracker_api.exercise.model.ExerciseAlias;
import dev.manuel.gymtracker_api.exercise.model.ExerciseTranslation;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseAliasRepository;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseRepository;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseTranslationRepository;
import dev.manuel.gymtracker_api.exercise.specification.ExerciseSpecification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final ExerciseTranslationRepository translationRepository;
    private final ExerciseAliasRepository aliasRepository;

    public ExerciseService(
            ExerciseRepository exerciseRepository,
            ExerciseTranslationRepository translationRepository,
            ExerciseAliasRepository aliasRepository
    ) {
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
            Pageable pageable
    ) {

        Specification<Exercise> specification =
                ExerciseSpecification.availableForUser(userId);

        if (search != null && !search.isBlank()) {
            specification = specification.and(
                    ExerciseSpecification.search(search)
            );
        }

        if (category != null && !category.isBlank()) {
            specification = specification.and(
                    ExerciseSpecification.hasCategory(category)
            );
        }

        if (equipment != null && !equipment.isBlank()) {
            specification = specification.and(
                    ExerciseSpecification.hasEquipment(equipment)
            );
        }

        if (muscleGroup != null && !muscleGroup.isBlank()) {
            specification = specification.and(
                    ExerciseSpecification.hasMuscleGroup(muscleGroup)
            );
        }

        if (targetMuscle != null && !targetMuscle.isBlank()) {
            specification = specification.and(
                    ExerciseSpecification.hasTargetMuscle(targetMuscle)
            );
        }

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.asc("id")
                )
        );

        Page<Exercise> exercisePage =
                exerciseRepository.findAll(
                        specification,
                        sortedPageable
                );

        if (exercisePage.isEmpty()) {
            return new ExercisePageResponse(
                    List.of(),
                    exercisePage.getNumber(),
                    exercisePage.getSize(),
                    exercisePage.getTotalElements(),
                    exercisePage.getTotalPages()
            );
        }

        List<UUID> exerciseIds = exercisePage.getContent()
                .stream()
                .map(Exercise::getId)
                .toList();

        List<ExerciseTranslation> translations =
                translationRepository.findByExerciseIdIn(exerciseIds);

        List<ExerciseAlias> aliases =
                aliasRepository.findByExerciseIdIn(exerciseIds);

        Map<UUID, List<ExerciseTranslation>> translationsByExercise =
                translations.stream()
                        .collect(Collectors.groupingBy(
                                ExerciseTranslation::getExerciseId
                        ));

        Map<UUID, List<ExerciseAlias>> aliasesByExercise =
                aliases.stream()
                        .collect(Collectors.groupingBy(
                                ExerciseAlias::getExerciseId
                        ));

        List<ExerciseResponse> content =
                exercisePage.getContent()
                        .stream()
                        .map(exercise -> toResponse(
                                exercise,
                                translationsByExercise.getOrDefault(
                                        exercise.getId(),
                                        List.of()
                                ),
                                aliasesByExercise.getOrDefault(
                                        exercise.getId(),
                                        List.of()
                                )
                        ))
                        .toList();

        return new ExercisePageResponse(
                content,
                exercisePage.getNumber(),
                exercisePage.getSize(),
                exercisePage.getTotalElements(),
                exercisePage.getTotalPages()
        );
    }

    private ExerciseResponse toResponse(
            Exercise exercise,
            List<ExerciseTranslation> translations,
            List<ExerciseAlias> aliases
    ) {

        List<ExerciseTranslationResponse> translationResponses =
                translations.stream()
                        .map(translation ->
                                new ExerciseTranslationResponse(
                                        translation.getLanguage(),
                                        translation.getName(),
                                        translation.getInstructions()
                                )
                        )
                        .toList();

        List<ExerciseAliasResponse> aliasResponses =
                aliases.stream()
                        .map(alias ->
                                new ExerciseAliasResponse(
                                        alias.getLanguage(),
                                        alias.getAlias()
                                )
                        )
                        .toList();

        return new ExerciseResponse(
                exercise.getId(),
                exercise.getCategory(),
                exercise.getEquipment(),
                exercise.getTargetMuscle(),
                exercise.getMuscleGroup(),
                exercise.getSecondaryMuscles(),
                translationResponses,
                aliasResponses
        );
    }
}