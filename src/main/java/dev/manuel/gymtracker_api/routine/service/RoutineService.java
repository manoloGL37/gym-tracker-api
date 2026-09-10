package dev.manuel.gymtracker_api.routine.service;

import dev.manuel.gymtracker_api.exercise.model.Exercise;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseRepository;
import dev.manuel.gymtracker_api.routine.dto.CreateRoutineRequest;
import dev.manuel.gymtracker_api.routine.dto.RoutineExerciseRequest;
import dev.manuel.gymtracker_api.routine.dto.RoutineExerciseResponse;
import dev.manuel.gymtracker_api.routine.dto.RoutineResponse;
import dev.manuel.gymtracker_api.routine.dto.RoutineSummaryResponse;
import dev.manuel.gymtracker_api.routine.exception.DuplicateRoutineExercisePositionException;
import dev.manuel.gymtracker_api.routine.model.Routine;
import dev.manuel.gymtracker_api.routine.model.RoutineExercise;
import dev.manuel.gymtracker_api.routine.repository.RoutineExerciseRepository;
import dev.manuel.gymtracker_api.routine.repository.RoutineRepository;
import dev.manuel.gymtracker_api.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RoutineService {

    private final RoutineRepository routineRepository;
    private final RoutineExerciseRepository routineExerciseRepository;
    private final ExerciseRepository exerciseRepository;

    public RoutineService(
            RoutineRepository routineRepository,
            RoutineExerciseRepository routineExerciseRepository,
            ExerciseRepository exerciseRepository) {
        this.routineRepository = routineRepository;
        this.routineExerciseRepository = routineExerciseRepository;
        this.exerciseRepository = exerciseRepository;
    }

    @Transactional
    public RoutineResponse createRoutine(
            UUID userId,
            CreateRoutineRequest request) {
        validateExercisesBelongToUser(
                userId,
                request.exercises());
        validateExercisePositions(request.exercises());

        if (request.clientId() != null) {
            Routine existingRoutine = routineRepository
                    .findByUserIdAndClientId(userId, request.clientId())
                    .orElse(null);

            if (existingRoutine != null) {
                return toResponse(existingRoutine, routineExerciseRepository
                        .findByRoutineIdOrderByPositionAsc(existingRoutine.getId()));
            }
        }

        LocalDateTime now = LocalDateTime.now();

        Routine routine = new Routine();

        routine.setId(UUID.randomUUID());
        routine.setUserId(userId);
        routine.setClientId(request.clientId());
        routine.setName(request.name());
        routine.setDescription(request.description());
        routine.setCreatedAt(now);
        routine.setUpdatedAt(now);

        Routine savedRoutine = routineRepository.save(routine);

        List<RoutineExercise> routineExercises = request.exercises()
                .stream()
                .map(exerciseRequest -> toRoutineExercise(
                        savedRoutine.getId(),
                        exerciseRequest))
                .toList();

        List<RoutineExercise> savedExercises = routineExerciseRepository.saveAll(
                routineExercises);

        return toResponse(
                savedRoutine,
                savedExercises);
    }

    @Transactional(readOnly = true)
    public RoutineResponse getRoutineById(
            UUID userId,
            UUID routineId) {
        Routine routine = routineRepository.findByIdAndUserIdAndDeletedAtIsNull(
                routineId,
                userId).orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Routine",
                                routineId));

        List<RoutineExercise> exercises = routineExerciseRepository
                .findByRoutineIdOrderByPositionAsc(
                        routineId);

        return toResponse(routine, exercises);
    }

    @Transactional(readOnly = true)
    public Page<RoutineSummaryResponse> getRoutines(
            UUID userId,
            Pageable pageable) {
        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.asc("id")));

        return routineRepository
                .findByUserIdAndDeletedAtIsNull(userId, sortedPageable)
                .map(routine -> new RoutineSummaryResponse(
                        routine.getId(),
                        routine.getClientId(),
                        routine.getName(),
                        routine.getDescription(),
                        routine.getCreatedAt(),
                        routine.getUpdatedAt()));
    }

    @Transactional
    public RoutineResponse updateRoutine(
            UUID userId,
            UUID routineId,
            CreateRoutineRequest request) {
        Routine routine = routineRepository.findByIdAndUserIdAndDeletedAtIsNull(
                routineId,
                userId).orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Routine",
                                routineId));

        validateExercisesBelongToUser(
                userId,
                request.exercises());

        validateExercisePositions(request.exercises());

        routine.setName(request.name());
        routine.setDescription(request.description());
        routine.setUpdatedAt(LocalDateTime.now());

        Routine savedRoutine = routineRepository.save(routine);

        routineExerciseRepository.deleteByRoutineId(routineId);

        List<RoutineExercise> routineExercises = request.exercises()
                .stream()
                .map(exerciseRequest -> toRoutineExercise(
                        routineId,
                        exerciseRequest))
                .toList();

        List<RoutineExercise> savedExercises = routineExerciseRepository.saveAll(
                routineExercises);

        return toResponse(
                savedRoutine,
                savedExercises);
    }

    @Transactional
public void deleteRoutine(
        UUID userId,
        UUID routineId
) {
    Routine routine =
            routineRepository
                    .findByIdAndUserIdAndDeletedAtIsNull(
                            routineId,
                            userId
                    )
                    .orElseThrow(() ->
                            new ResourceNotFoundException(
                                    "Routine",
                                    routineId
                            )
                    );

    routine.setDeletedAt(LocalDateTime.now());
}

    private void validateExercisesBelongToUser(
            UUID userId,
            List<RoutineExerciseRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        List<UUID> exerciseIds = requests.stream()
                .map(RoutineExerciseRequest::exerciseId)
                .distinct()
                .toList();

        List<Exercise> exercises = exerciseRepository.findAllById(exerciseIds);

        Set<UUID> availableExerciseIds = exercises.stream()
                .filter(exercise -> exercise.getDeletedAt() == null
                        && (exercise.getOwnerId() == null
                                || exercise.getOwnerId()
                                        .equals(userId)))
                .map(Exercise::getId)
                .collect(Collectors.toSet());

        UUID unavailableExerciseId = exerciseIds.stream()
                .filter(id -> !availableExerciseIds.contains(id))
                .findFirst()
                .orElse(null);

        if (unavailableExerciseId != null) {
            throw new ResourceNotFoundException(
                    "Exercise",
                    unavailableExerciseId);
        }
    }

    private void validateExercisePositions(
            List<RoutineExerciseRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        Set<Integer> positions = new HashSet<>();

        for (RoutineExerciseRequest request : requests) {
            if (!positions.add(request.position())) {
                throw new DuplicateRoutineExercisePositionException(
                        request.position());
            }
        }
    }

    private RoutineExercise toRoutineExercise(
            UUID routineId,
            RoutineExerciseRequest request) {
        RoutineExercise routineExercise = new RoutineExercise();

        routineExercise.setId(UUID.randomUUID());
        routineExercise.setRoutineId(routineId);
        routineExercise.setExerciseId(request.exerciseId());
        routineExercise.setPosition(request.position());
        routineExercise.setSets(request.sets());
        routineExercise.setTargetReps(request.targetReps());
        routineExercise.setRestSeconds(request.restSeconds());
        routineExercise.setNotes(request.notes());

        return routineExercise;
    }

    private RoutineResponse toResponse(
            Routine routine,
            List<RoutineExercise> exercises) {
        List<RoutineExerciseResponse> exerciseResponses = exercises.stream()
                .map(exercise -> new RoutineExerciseResponse(
                        exercise.getId(),
                        exercise.getExerciseId(),
                        exercise.getPosition(),
                        exercise.getSets(),
                        exercise.getTargetReps(),
                        exercise.getRestSeconds(),
                        exercise.getNotes()))
                .toList();

        return new RoutineResponse(
                routine.getId(),
                routine.getClientId(),
                routine.getName(),
                routine.getDescription(),
                exerciseResponses,
                routine.getCreatedAt(),
                routine.getUpdatedAt());
    }
}
