package dev.manuel.gymtracker_api.routine.repository;

import dev.manuel.gymtracker_api.routine.model.Routine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoutineRepository extends JpaRepository<Routine, UUID> {

    Page<Routine> findByUserIdAndDeletedAtIsNull(
            UUID userId,
            Pageable pageable
    );

    Optional<Routine> findByIdAndUserIdAndDeletedAtIsNull(
            UUID id,
            UUID userId
    );

    Optional<Routine> findByUserIdAndClientId(UUID userId, UUID clientId);
}
