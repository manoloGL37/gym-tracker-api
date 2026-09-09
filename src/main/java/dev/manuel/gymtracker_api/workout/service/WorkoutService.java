package dev.manuel.gymtracker_api.workout.service;

import dev.manuel.gymtracker_api.common.exception.ResourceNotFoundException;
import dev.manuel.gymtracker_api.routine.model.Routine;
import dev.manuel.gymtracker_api.routine.model.RoutineExercise;
import dev.manuel.gymtracker_api.routine.repository.RoutineExerciseRepository;
import dev.manuel.gymtracker_api.routine.repository.RoutineRepository;
import dev.manuel.gymtracker_api.workout.dto.CreateWorkoutRequest;
import dev.manuel.gymtracker_api.workout.dto.UpdateWorkoutRequest;
import dev.manuel.gymtracker_api.workout.dto.WorkoutExerciseResponse;
import dev.manuel.gymtracker_api.workout.dto.WorkoutResponse;
import dev.manuel.gymtracker_api.workout.dto.WorkoutSetRequest;
import dev.manuel.gymtracker_api.workout.dto.WorkoutSetResponse;
import dev.manuel.gymtracker_api.workout.exception.DuplicateWorkoutSetNumberException;
import dev.manuel.gymtracker_api.workout.model.Workout;
import dev.manuel.gymtracker_api.workout.model.WorkoutExercise;
import dev.manuel.gymtracker_api.workout.model.WorkoutSet;
import dev.manuel.gymtracker_api.workout.repository.WorkoutExerciseRepository;
import dev.manuel.gymtracker_api.workout.repository.WorkoutRepository;
import dev.manuel.gymtracker_api.workout.repository.WorkoutSetRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkoutService {

        private final WorkoutRepository workoutRepository;
        private final WorkoutExerciseRepository workoutExerciseRepository;
        private final RoutineRepository routineRepository;
        private final RoutineExerciseRepository routineExerciseRepository;
        private final WorkoutSetRepository workoutSetRepository;

        public WorkoutService(
                        WorkoutRepository workoutRepository,
                        WorkoutExerciseRepository workoutExerciseRepository,
                        WorkoutSetRepository workoutSetRepository,
                        RoutineRepository routineRepository,
                        RoutineExerciseRepository routineExerciseRepository) {

                this.workoutRepository = workoutRepository;
                this.workoutExerciseRepository = workoutExerciseRepository;
                this.workoutSetRepository = workoutSetRepository;
                this.routineRepository = routineRepository;
                this.routineExerciseRepository = routineExerciseRepository;
        }

        @Transactional
        public WorkoutResponse createWorkout(
                        UUID userId,
                        CreateWorkoutRequest request) {

                Routine routine = routineRepository
                                .findByIdAndUserIdAndDeletedAtIsNull(
                                                request.routineId(),
                                                userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Routine",
                                                request.routineId()));

                LocalDateTime now = LocalDateTime.now();

                Workout workout = new Workout();

                workout.setId(UUID.randomUUID());
                workout.setUserId(userId);
                workout.setRoutineId(routine.getId());
                workout.setStartedAt(now);
                workout.setCreatedAt(now);

                Workout savedWorkout = workoutRepository.save(workout);

                List<RoutineExercise> routineExercises = routineExerciseRepository
                                .findByRoutineIdOrderByPositionAsc(
                                                routine.getId());

                List<WorkoutExercise> workoutExercises = routineExercises
                                .stream()
                                .map(routineExercise -> toWorkoutExercise(
                                                savedWorkout.getId(),
                                                routineExercise))
                                .toList();

                List<WorkoutExercise> savedExercises = workoutExerciseRepository
                                .saveAll(workoutExercises);

                return toResponse(
                                savedWorkout,
                                savedExercises);
        }

        @Transactional
        public WorkoutSetResponse createWorkoutSet(
                        UUID userId,
                        UUID workoutId,
                        UUID workoutExerciseId,
                        WorkoutSetRequest request) {

                Workout workout = workoutRepository
                                .findByIdAndUserId(
                                                workoutId,
                                                userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Workout",
                                                workoutId));

                WorkoutExercise workoutExercise = workoutExerciseRepository
                                .findById(workoutExerciseId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "WorkoutExercise",
                                                workoutExerciseId));

                if (!workoutExercise.getWorkoutId().equals(workout.getId())) {
                        throw new ResourceNotFoundException(
                                        "WorkoutExercise",
                                        workoutExerciseId);
                }

                boolean setAlreadyExists = workoutSetRepository
                                .existsByWorkoutExerciseIdAndSetNumber(
                                                workoutExerciseId,
                                                request.setNumber());

                if (setAlreadyExists) {
                        throw new DuplicateWorkoutSetNumberException(
                                        request.setNumber());
                }

                WorkoutSet workoutSet = new WorkoutSet();

                workoutSet.setId(UUID.randomUUID());
                workoutSet.setWorkoutExerciseId(workoutExerciseId);
                workoutSet.setSetNumber(request.setNumber());
                workoutSet.setWeight(request.weight());
                workoutSet.setReps(request.reps());
                workoutSet.setRpe(request.rpe());

                WorkoutSet savedSet = workoutSetRepository.save(workoutSet);

                return toSetResponse(savedSet);
        }

        @Transactional(readOnly = true)
        public WorkoutResponse getWorkoutById(
                        UUID userId,
                        UUID workoutId) {

                Workout workout = workoutRepository
                                .findByIdAndUserId(workoutId, userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Workout",
                                                workoutId));

                List<WorkoutExercise> exercises = workoutExerciseRepository
                                .findByWorkoutIdOrderByPositionAsc(workoutId);

                return toResponse(workout, exercises);
        }

        @Transactional(readOnly = true)
        public Page<WorkoutResponse> getWorkouts(
                        UUID userId,
                        Pageable pageable) {

                Pageable sortedPageable = PageRequest.of(
                                pageable.getPageNumber(),
                                pageable.getPageSize(),
                                Sort.by(
                                                Sort.Order.desc("createdAt"),
                                                Sort.Order.asc("id")));

                return workoutRepository
                                .findByUserId(userId, sortedPageable)
                                .map(workout -> {

                                        List<WorkoutExercise> exercises = workoutExerciseRepository
                                                        .findByWorkoutIdOrderByPositionAsc(
                                                                        workout.getId());

                                        return toResponse(
                                                        workout,
                                                        exercises);
                                });
        }

        @Transactional
        public WorkoutResponse updateWorkout(
                        UUID userId,
                        UUID workoutId,
                        UpdateWorkoutRequest request) {

                Workout workout = workoutRepository
                                .findByIdAndUserId(
                                                workoutId,
                                                userId)
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Workout",
                                                workoutId));

                if (request.completed() != null) {
                        workout.setCompletedAt(
                                        request.completed()
                                                        ? LocalDateTime.now()
                                                        : null);
                }

                if (request.notes() != null) {
                        workout.setNotes(request.notes());
                }

                Workout savedWorkout = workoutRepository.save(workout);

                List<WorkoutExercise> exercises = workoutExerciseRepository
                                .findByWorkoutIdOrderByPositionAsc(
                                                workoutId);

                return toResponse(
                                savedWorkout,
                                exercises);
        }

        private WorkoutExercise toWorkoutExercise(
                        UUID workoutId,
                        RoutineExercise routineExercise) {

                WorkoutExercise workoutExercise = new WorkoutExercise();

                workoutExercise.setId(UUID.randomUUID());
                workoutExercise.setWorkoutId(workoutId);
                workoutExercise.setExerciseId(
                                routineExercise.getExerciseId());
                workoutExercise.setPosition(
                                routineExercise.getPosition());
                workoutExercise.setNotes(
                                routineExercise.getNotes());

                return workoutExercise;
        }

        private WorkoutSetResponse toSetResponse(
                        WorkoutSet workoutSet) {

                return new WorkoutSetResponse(
                                workoutSet.getId(),
                                workoutSet.getSetNumber(),
                                workoutSet.getWeight(),
                                workoutSet.getReps(),
                                workoutSet.getRpe());
        }

        private WorkoutResponse toResponse(
                        Workout workout,
                        List<WorkoutExercise> exercises) {

                List<UUID> workoutExerciseIds = exercises.stream()
                                .map(WorkoutExercise::getId)
                                .toList();

                List<WorkoutSet> workoutSets = workoutExerciseIds.isEmpty()
                                ? List.of()
                                : workoutSetRepository
                                                .findByWorkoutExerciseIdInOrderByWorkoutExerciseIdAscSetNumberAsc(
                                                                workoutExerciseIds);

                Map<UUID, List<WorkoutSet>> setsByWorkoutExercise = workoutSets.stream()
                                .collect(Collectors.groupingBy(
                                                WorkoutSet::getWorkoutExerciseId));

                List<WorkoutExerciseResponse> exerciseResponses = exercises.stream()
                                .map(exercise -> {

                                        List<WorkoutSetResponse> sets = setsByWorkoutExercise
                                                        .getOrDefault(
                                                                        exercise.getId(),
                                                                        List.of())
                                                        .stream()
                                                        .map(this::toSetResponse)
                                                        .toList();

                                        return new WorkoutExerciseResponse(
                                                        exercise.getId(),
                                                        exercise.getExerciseId(),
                                                        exercise.getPosition(),
                                                        exercise.getNotes(),
                                                        sets);
                                })
                                .toList();

                return new WorkoutResponse(
                                workout.getId(),
                                workout.getRoutineId(),
                                workout.getStartedAt(),
                                workout.getCompletedAt(),
                                workout.getNotes(),
                                exerciseResponses,
                                workout.getCreatedAt());
        }
}