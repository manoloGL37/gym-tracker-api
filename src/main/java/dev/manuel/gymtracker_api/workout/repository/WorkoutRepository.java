package dev.manuel.gymtracker_api.workout.repository;

import dev.manuel.gymtracker_api.workout.model.Workout;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WorkoutRepository
        extends JpaRepository<Workout, UUID> {

    Page<Workout> findByUserId(
            UUID userId,
            Pageable pageable
    );

    Optional<Workout> findByIdAndUserId(
            UUID id,
            UUID userId
    );

    Optional<Workout> findByUserIdAndClientId(UUID userId, UUID clientId);
}
