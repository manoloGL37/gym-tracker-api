package dev.manuel.gymtracker_api.workout.service;

import dev.manuel.gymtracker_api.workout.model.Side;
import dev.manuel.gymtracker_api.exercise.model.Laterality;
import dev.manuel.gymtracker_api.workout.model.SideResult;
import dev.manuel.gymtracker_api.workout.exception.StaleSetVersionException;
import dev.manuel.gymtracker_api.workout.dto.UpdateSetResultRequest;
import dev.manuel.gymtracker_api.user.repository.UserRepository;
import dev.manuel.gymtracker_api.exercise.model.Measurement;
import dev.manuel.gymtracker_api.common.exception.ResourceNotFoundException;
import dev.manuel.gymtracker_api.exercise.model.Exercise;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseRepository;
import dev.manuel.gymtracker_api.exercise.repository.ExerciseTranslationRepository;
import dev.manuel.gymtracker_api.routine.model.Routine;
import dev.manuel.gymtracker_api.routine.model.RoutineExercise;
import dev.manuel.gymtracker_api.routine.repository.RoutineExerciseRepository;
import dev.manuel.gymtracker_api.routine.repository.RoutineRepository;
import dev.manuel.gymtracker_api.workout.dto.CreateWorkoutRequest;
import dev.manuel.gymtracker_api.workout.dto.CreateMobileWorkoutRequest;
import dev.manuel.gymtracker_api.workout.dto.MobileWorkoutExerciseRequest;
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
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WorkoutService {

        private final UserRepository userRepository;
        private final WorkoutRepository workoutRepository;
        private final WorkoutExerciseRepository workoutExerciseRepository;
        private final RoutineRepository routineRepository;
        private final RoutineExerciseRepository routineExerciseRepository;
        private final WorkoutSetRepository workoutSetRepository;
        private final ExerciseRepository exerciseRepository;
        private final ExerciseTranslationRepository exerciseTranslationRepository;

        public WorkoutService(
                        UserRepository userRepository,
                        WorkoutRepository workoutRepository,
                        WorkoutExerciseRepository workoutExerciseRepository,
                        WorkoutSetRepository workoutSetRepository,
                        RoutineRepository routineRepository,
                        RoutineExerciseRepository routineExerciseRepository,
                        ExerciseRepository exerciseRepository,
                        ExerciseTranslationRepository exerciseTranslationRepository) {

                this.userRepository = userRepository;
                this.workoutRepository = workoutRepository;
                this.workoutExerciseRepository = workoutExerciseRepository;
                this.workoutSetRepository = workoutSetRepository;
                this.routineRepository = routineRepository;
                this.routineExerciseRepository = routineExerciseRepository;
                this.exerciseRepository = exerciseRepository;
                this.exerciseTranslationRepository = exerciseTranslationRepository;
        }

        @Transactional
        public WorkoutResponse createWorkout(
                        UUID userId,
                        CreateWorkoutRequest request) {
                lockAccount(userId);
                if (request.clientId() != null) {
                        Workout existingWorkout = workoutRepository
                                        .findByUserIdAndClientId(userId, request.clientId())
                                        .orElse(null);
                        if (existingWorkout != null) {
                                return toResponse(existingWorkout, workoutExerciseRepository
                                                .findByWorkoutIdOrderByPositionAsc(existingWorkout.getId()));
                        }
                }

                Routine routine = routineRepository
                                .findByIdAndUserIdAndDeletedAtIsNull(request.routineId(), userId)
                                .orElseThrow(() -> new ResourceNotFoundException("Routine", request.routineId()));

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startedAt = request.startedAt() != null ? request.startedAt() : now;
                if (request.completedAt() != null && request.completedAt().isBefore(startedAt)) {
                        throw new IllegalArgumentException("completedAt must not be before startedAt");
                }
                Workout workout = new Workout();
                workout.setId(UUID.randomUUID());
                workout.setUserId(userId);
                workout.setClientId(request.clientId());
                workout.setRoutineId(routine.getId());
                workout.setNameSnapshot(routine.getName());
                workout.setStartedAt(startedAt);
                workout.setCompletedAt(request.completedAt());
                workout.setNotes(request.notes());
                workout.setCreatedAt(now);
                Workout savedWorkout = workoutRepository.save(workout);
                List<WorkoutExercise> savedExercises = workoutExerciseRepository.saveAll(routineExerciseRepository
                                .findByRoutineIdOrderByPositionAsc(routine.getId()).stream()
                                .map(row -> toWorkoutExercise(savedWorkout.getId(), row)).toList());
                return toResponse(savedWorkout, savedExercises);
        }

        @Transactional
        public WorkoutResponse createMobileWorkout(UUID userId, CreateMobileWorkoutRequest request) {
                lockAccount(userId);
                if (!ZoneId.getAvailableZoneIds().contains(request.calendarZone())) {
                        throw new IllegalArgumentException("calendarZone must be a valid IANA zone ID");
                }
                ZoneId zone = ZoneId.of(request.calendarZone());
                if (request.completedAt().isBefore(request.startedAt())) {
                        throw new IllegalArgumentException("completedAt must not be before startedAt");
                }
                Workout existing = workoutRepository.findByUserIdAndClientId(userId, request.clientId()).orElse(null);
                if (existing != null) {
                        return toResponse(existing, workoutExerciseRepository.findByWorkoutIdOrderByPositionAsc(existing.getId()));
                }
                if (request.routineId() != null && routineRepository.findByIdAndUserId(request.routineId(), userId).isEmpty()) {
                        throw new ResourceNotFoundException("Routine", request.routineId());
                }
                Set<Integer> positions = new HashSet<>();
                Set<UUID> clientIds = new HashSet<>();
                for (MobileWorkoutExerciseRequest exercise : request.exercises()) {
                        if (!positions.add(exercise.position())) throw new IllegalArgumentException("Duplicate exercise position");
                        if (exercise.clientId() != null && !clientIds.add(exercise.clientId())) throw new IllegalArgumentException("Duplicate exercise clientId");
                        if (exercise.exerciseId() != null) {
                                Exercise source = exerciseRepository.findById(exercise.exerciseId())
                                                .orElseThrow(() -> new ResourceNotFoundException("Exercise", exercise.exerciseId()));
                                if (source.getOwnerId() != null && !source.getOwnerId().equals(userId))
                                        throw new ResourceNotFoundException("Exercise", exercise.exerciseId());
                        }
                        Set<Integer> setNumbers = new HashSet<>();
                        for (WorkoutSetRequest set : exercise.sets()) {
                                if (!setNumbers.add(set.setNumber())) throw new IllegalArgumentException("Duplicate set number");
                                if (set.clientId() != null && !clientIds.add(set.clientId())) throw new IllegalArgumentException("Duplicate clientId");
                        }
                }
                Workout workout = new Workout();
                workout.setId(UUID.randomUUID());
                workout.setUserId(userId);
                workout.setClientId(request.clientId());
                workout.setRoutineId(request.routineId());
                workout.setNameSnapshot(request.nameSnapshot());
                workout.setStartedAt(LocalDateTime.ofInstant(request.startedAt(), zone));
                workout.setCompletedAt(LocalDateTime.ofInstant(request.completedAt(), zone));
                workout.setStartedAtInstant(request.startedAt());
                workout.setCompletedAtInstant(request.completedAt());
                workout.setCalendarZone(zone.getId());
                workout.setNotes(request.notes());
                workout.setCreatedAt(LocalDateTime.now());
                workoutRepository.save(workout);
                List<MobileWorkoutExerciseRequest> orderedExercises = request.exercises().stream()
                                .sorted(Comparator.comparingInt(MobileWorkoutExerciseRequest::position)).toList();
                List<WorkoutExercise> savedExercises = orderedExercises.stream()
                                .map(exercise -> {
                                        WorkoutExercise row = new WorkoutExercise();
                                        row.setId(UUID.randomUUID());
                                        row.setWorkoutId(workout.getId());
                                        row.setClientId(exercise.clientId());
                                        row.setExerciseId(exercise.exerciseId());
                                        row.setExerciseNameSnapshot(exercise.exerciseNameSnapshot());
                                        row.setPosition(exercise.position());
                                        row.setNotes(exercise.notes());
                                        row.setLaterality(exercise.laterality());
                                        row.setMeasurement(exercise.measurement());
                                        return workoutExerciseRepository.save(row);
                                }).toList();
                for (int i = 0; i < savedExercises.size(); i++) {
                        WorkoutExercise savedExercise = savedExercises.get(i);
                        MobileWorkoutExerciseRequest exercise = orderedExercises.get(i);
                        for (WorkoutSetRequest set : exercise.sets()) {
                                WorkoutSet row = new WorkoutSet();
                                row.setId(UUID.randomUUID());
                                row.setWorkoutExerciseId(savedExercise.getId());
                                row.setClientId(set.clientId());
                                row.setSetNumber(set.setNumber());
                                row.setWeight(set.weight());
                                row.setReps(set.reps());
                                row.setRpe(set.rpe());
                                applySet(row, savedExercise, set);
                                workoutSetRepository.save(row);
                        }
                }
                return toResponse(workout, savedExercises);
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

                lockAccount(userId);
                if (request.clientId() != null) {
                        WorkoutSet existingSet = workoutSetRepository
                                        .findByWorkoutExerciseIdAndClientId(workoutExerciseId, request.clientId())
                                        .orElse(null);

                        if (existingSet != null) {
                                return toSetResponse(existingSet);
                        }
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
                workoutSet.setClientId(request.clientId());
                workoutSet.setSetNumber(request.setNumber());
                workoutSet.setWeight(request.weight());
                workoutSet.setReps(request.reps());
                workoutSet.setRpe(request.rpe());
                applySet(workoutSet, workoutExercise, request);

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
                        if (workout.getCalendarZone() != null) {
                                Instant completed = request.completed() ? Instant.now() : null;
                                workout.setCompletedAtInstant(completed);
                                workout.setCompletedAt(completed == null ? null :
                                                LocalDateTime.ofInstant(completed, ZoneId.of(workout.getCalendarZone())));
                        } else {
                                workout.setCompletedAt(request.completed() ? LocalDateTime.now() : null);
                        }
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

        private void lockAccount(UUID userId) {
                // ponytail: serializes workout writes per account; use narrower row locks if contention grows.
                userRepository.findByIdForUpdate(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        }

        private void applySet(WorkoutSet row, WorkoutExercise exercise, WorkoutSetRequest request) {
                row.setDurationSeconds(request.durationSeconds());
                row.setLeft(request.left());
                row.setRight(request.right());
                row.setCompleted(request.completed() == null || request.completed());
                validateSet(row, exercise);
        }

        private void validateMetric(Integer reps, Integer seconds, boolean completed,
                        Measurement measurement) {
                boolean timed = measurement == Measurement.DURATION;
                if ((timed && reps != null) || (!timed && seconds != null))
                        throw new IllegalArgumentException("Metric does not match exercise measurement");
                if (completed && (timed ? seconds == null : reps == null))
                        throw new IllegalArgumentException("Completed result requires its primary metric");
        }

        private void validateSet(WorkoutSet row, WorkoutExercise exercise) {
                if (exercise.getLaterality() == Laterality.UNILATERAL) {
                        if (row.getWeight() != null || row.getReps() != null || row.getDurationSeconds() != null)
                                throw new IllegalArgumentException("Unilateral sets use left/right results only");
                        for (var result : new SideResult[]{row.getLeft(), row.getRight()}) {
                                if (result != null) validateMetric(result.getRepetitions(), result.getDurationSeconds(),
                                                Boolean.TRUE.equals(result.getCompleted()), exercise.getMeasurement());
                        }
                        row.setCompleted(row.getLeft() != null && Boolean.TRUE.equals(row.getLeft().getCompleted())
                                        && row.getRight() != null && Boolean.TRUE.equals(row.getRight().getCompleted()));
                } else {
                        if (row.getLeft() != null || row.getRight() != null)
                                throw new IllegalArgumentException("Bilateral or unknown laterality cannot contain side results");
                        validateMetric(row.getReps(), row.getDurationSeconds(), row.isCompleted(), exercise.getMeasurement());
                }
        }

        @Transactional
        public WorkoutSetResponse updateSetResult(UUID userId, UUID workoutId, UUID exerciseId, UUID setId,
                        UpdateSetResultRequest request) {
                lockAccount(userId);
                workoutRepository.findByIdAndUserId(workoutId, userId)
                        .orElseThrow(() -> new ResourceNotFoundException("Workout", workoutId));
                WorkoutExercise exercise = workoutExerciseRepository.findById(exerciseId)
                        .filter(e -> e.getWorkoutId().equals(workoutId))
                        .orElseThrow(() -> new ResourceNotFoundException("WorkoutExercise", exerciseId));
                WorkoutSet row = workoutSetRepository.findByIdAndWorkoutExerciseId(setId, exerciseId)
                        .orElseThrow(() -> new ResourceNotFoundException("WorkoutSet", setId));
                if (request.clientMutationId().equals(row.getLastMutationId())) return toSetResponse(row);
                if (row.getVersion() != request.expectedVersion())
                        throw new StaleSetVersionException();
                if (Boolean.TRUE.equals(request.clearAdditionalWeight()) && request.additionalWeightKg() != null)
                        throw new IllegalArgumentException("Cannot set and clear weight together");
                boolean unilateral = exercise.getLaterality() == Laterality.UNILATERAL;
                if (unilateral != (request.side() != null))
                        throw new IllegalArgumentException("side is required only for unilateral results");
                if (unilateral) {
                        var result = request.side() == Side.LEFT ? row.getLeft() : row.getRight();
                        if (result == null) result = new SideResult(null, null, null, false);
                        if (request.repetitions() != null) result.setRepetitions(request.repetitions());
                        if (request.durationSeconds() != null) result.setDurationSeconds(request.durationSeconds());
                        if (request.additionalWeightKg() != null) result.setAdditionalWeightKg(request.additionalWeightKg());
                        if (Boolean.TRUE.equals(request.clearAdditionalWeight())) result.setAdditionalWeightKg(null);
                        if (request.completed() != null) result.setCompleted(request.completed());
                        if (request.side() == Side.LEFT) row.setLeft(result);
                        else row.setRight(result);
                } else {
                        if (request.repetitions() != null) row.setReps(request.repetitions());
                        if (request.durationSeconds() != null) row.setDurationSeconds(request.durationSeconds());
                        if (request.additionalWeightKg() != null) row.setWeight(request.additionalWeightKg());
                        if (Boolean.TRUE.equals(request.clearAdditionalWeight())) row.setWeight(null);
                        if (request.completed() != null) row.setCompleted(request.completed());
                }
                validateSet(row, exercise);
                row.setLastMutationId(request.clientMutationId());
                workoutSetRepository.flush();
                return toSetResponse(row);
        }

        private WorkoutExercise toWorkoutExercise(
                        UUID workoutId,
                        RoutineExercise routineExercise) {

                WorkoutExercise workoutExercise = new WorkoutExercise();

                workoutExercise.setId(UUID.randomUUID());
                workoutExercise.setWorkoutId(workoutId);
                workoutExercise.setExerciseId(
                                routineExercise.getExerciseId());
                exerciseRepository.findById(routineExercise.getExerciseId()).ifPresent(source -> {
                        workoutExercise.setLaterality(routineExercise.getLateralityOverride() != null
                                        ? routineExercise.getLateralityOverride() : source.getLaterality());
                        workoutExercise.setMeasurement(source.getMeasurement());
                });
                if (routineExercise.getTargetDurationSeconds() != null)
                        workoutExercise.setMeasurement(Measurement.DURATION);
                workoutExercise.setPosition(
                                routineExercise.getPosition());
                workoutExercise.setNotes(
                                routineExercise.getNotes());
                // ponytail: one translation lookup per routine exercise; capped at 50 by routine validation.
                exerciseTranslationRepository.findByExerciseId(routineExercise.getExerciseId()).stream()
                                .sorted(Comparator.comparingInt((dev.manuel.gymtracker_api.exercise.model.ExerciseTranslation t) ->
                                                t.getLanguage().equals("en") ? 0 : 1).thenComparing(t -> t.getLanguage()))
                                .findFirst().ifPresent(t -> workoutExercise.setExerciseNameSnapshot(t.getName()));

                return workoutExercise;
        }

        private WorkoutSetResponse toSetResponse(
                        WorkoutSet workoutSet) {

                return new WorkoutSetResponse(
                                workoutSet.getId(),
                                workoutSet.getClientId(),
                                workoutSet.getSetNumber(),
                                workoutSet.getWeight(),
                                workoutSet.getReps(),
                                workoutSet.getRpe(), workoutSet.getDurationSeconds(), workoutSet.isCompleted(),
                                workoutSet.getLeft(), workoutSet.getRight(), workoutSet.getVersion());
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
                                                        sets,
                                                        exercise.getClientId(),
                                                        exercise.getExerciseNameSnapshot(), exercise.getLaterality(), exercise.getMeasurement());
                                })
                                .toList();

                return new WorkoutResponse(
                                workout.getId(),
                                workout.getClientId(),
                                workout.getRoutineId(),
                                workout.getStartedAt(),
                                workout.getCompletedAt(),
                                workout.getNotes(),
                                exerciseResponses,
                                workout.getCreatedAt(),
                                workout.getStartedAtInstant(),
                                workout.getCompletedAtInstant(),
                                workout.getCalendarZone(),
                                workout.getNameSnapshot());
        }
}
