package dev.manuel.gymtracker_api.statistics.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class StatisticsRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public StatisticsSummaryProjection getSummary(
            UUID userId,
            LocalDateTime from,
            LocalDateTime to) {

        Object[] result = (Object[]) entityManager
                .createNativeQuery("""
                        SELECT
                            COUNT(DISTINCT w.id),
                            COUNT(ws.id),
                            COALESCE(SUM(ws.reps), 0),
                            COALESCE(SUM(ws.weight * ws.reps), 0),
                            COALESCE(MAX(ws.weight), 0)
                        FROM workouts w
                        LEFT JOIN workout_exercises we
                            ON we.workout_id = w.id
                        LEFT JOIN workout_sets ws
                            ON ws.workout_exercise_id = we.id
                        WHERE w.user_id = :userId
                          AND w.started_at >= :from
                          AND w.started_at < :to
                        """)
                .setParameter("userId", userId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();

        return new StatisticsSummaryProjection(
                ((Number) result[0]).longValue(),
                ((Number) result[1]).longValue(),
                ((Number) result[2]).longValue(),
                (BigDecimal) result[3],
                (BigDecimal) result[4]);
    }

    public record StatisticsSummaryProjection(
            long workouts,
            long sets,
            long reps,
            BigDecimal volume,
            BigDecimal maxWeight) {
    }

    public List<StatisticsEvolutionProjection> getEvolution(
            UUID userId,
            LocalDateTime from,
            LocalDateTime to) {
        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager
                .createNativeQuery("""
                        SELECT
                            DATE(w.started_at) AS date,
                            COUNT(DISTINCT w.id) AS workouts,
                            COUNT(ws.id) AS sets,
                            COALESCE(SUM(ws.reps), 0) AS reps,
                            COALESCE(SUM(ws.weight * ws.reps), 0) AS volume,
                            COALESCE(MAX(ws.weight), 0) AS max_weight
                        FROM workouts w
                        LEFT JOIN workout_exercises we
                            ON we.workout_id = w.id
                        LEFT JOIN workout_sets ws
                            ON ws.workout_exercise_id = we.id
                        WHERE w.user_id = :userId
                          AND w.started_at >= :from
                          AND w.started_at < :to
                        GROUP BY DATE(w.started_at)
                        ORDER BY DATE(w.started_at)
                        """)
                .setParameter("userId", userId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        return results.stream()
                .map(result -> new StatisticsEvolutionProjection(
                        (LocalDate) result[0],
                        ((Number) result[1]).longValue(),
                        ((Number) result[2]).longValue(),
                        ((Number) result[3]).longValue(),
                        (BigDecimal) result[4],
                        (BigDecimal) result[5]))
                .toList();
    }

    public ExerciseStatisticsSummaryProjection getExerciseSummary(
            UUID userId,
            UUID exerciseId,
            LocalDateTime from,
            LocalDateTime to) {
        Object[] result = (Object[]) entityManager
                .createNativeQuery("""
                        SELECT
                            COUNT(ws.id),
                            COALESCE(SUM(ws.reps), 0),
                            COALESCE(SUM(ws.weight * ws.reps), 0),
                            COALESCE(MAX(ws.weight), 0)
                        FROM workouts w
                        JOIN workout_exercises we
                            ON we.workout_id = w.id
                        LEFT JOIN workout_sets ws
                            ON ws.workout_exercise_id = we.id
                        WHERE w.user_id = :userId
                          AND we.exercise_id = :exerciseId
                          AND w.started_at >= :from
                          AND w.started_at < :to
                        """)
                .setParameter("userId", userId)
                .setParameter("exerciseId", exerciseId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();

        return new ExerciseStatisticsSummaryProjection(
                ((Number) result[0]).longValue(),
                ((Number) result[1]).longValue(),
                (BigDecimal) result[2],
                (BigDecimal) result[3]);
    }

    public List<ExerciseStatisticsEvolutionProjection> getExerciseEvolution(
            UUID userId,
            UUID exerciseId,
            LocalDateTime from,
            LocalDateTime to) {
        @SuppressWarnings("unchecked")
        List<Object[]> results = entityManager
                .createNativeQuery("""
                        SELECT
                            DATE(w.started_at) AS date,
                            COALESCE(SUM(ws.weight * ws.reps), 0) AS volume,
                            COALESCE(MAX(ws.weight), 0) AS max_weight
                        FROM workouts w
                        JOIN workout_exercises we
                            ON we.workout_id = w.id
                        LEFT JOIN workout_sets ws
                            ON ws.workout_exercise_id = we.id
                        WHERE w.user_id = :userId
                          AND we.exercise_id = :exerciseId
                          AND w.started_at >= :from
                          AND w.started_at < :to
                        GROUP BY DATE(w.started_at)
                        ORDER BY DATE(w.started_at)
                        """)
                .setParameter("userId", userId)
                .setParameter("exerciseId", exerciseId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        return results.stream()
                .map(result -> new ExerciseStatisticsEvolutionProjection(
                        (LocalDate) result[0],
                        (BigDecimal) result[1],
                        (BigDecimal) result[2]))
                .toList();
    }

    public record ExerciseStatisticsSummaryProjection(
            long totalSets,
            long totalReps,
            BigDecimal totalVolume,
            BigDecimal maxWeight) {
    }

    public record ExerciseStatisticsEvolutionProjection(
            LocalDate date,
            BigDecimal volume,
            BigDecimal maxWeight) {
    }

    public record StatisticsEvolutionProjection(
            LocalDate date,
            long workouts,
            long sets,
            long reps,
            BigDecimal volume,
            BigDecimal maxWeight) {
    }
}