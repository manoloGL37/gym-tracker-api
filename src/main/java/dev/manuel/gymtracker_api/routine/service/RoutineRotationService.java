package dev.manuel.gymtracker_api.routine.service;

import dev.manuel.gymtracker_api.common.exception.ResourceNotFoundException;
import dev.manuel.gymtracker_api.routine.dto.RoutineRotation;
import dev.manuel.gymtracker_api.routine.repository.RoutineRepository;
import dev.manuel.gymtracker_api.user.model.User;
import dev.manuel.gymtracker_api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class RoutineRotationService {
    private final UserRepository users;
    private final RoutineRepository routines;

    public RoutineRotationService(UserRepository users, RoutineRepository routines) {
        this.users = users;
        this.routines = routines;
    }

    @Transactional(readOnly = true)
    public RoutineRotation get(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return new RoutineRotation(List.copyOf(user.getRoutineRotation()));
    }

    @Transactional
    public RoutineRotation replace(UUID userId, RoutineRotation request) {
        List<UUID> ids = request.routineIds();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Duplicate routine IDs are not allowed");
        }
        User user = lockAccount(userId);
        for (UUID id : ids) {
            routines.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Routine", id));
        }
        user.getRoutineRotation().clear();
        user.getRoutineRotation().addAll(ids);
        return new RoutineRotation(List.copyOf(ids));
    }

    @Transactional
    public void remove(UUID userId, UUID routineId) {
        lockAccount(userId).getRoutineRotation().remove(routineId);
    }

    private User lockAccount(UUID userId) {
        // ponytail: serialize rotation PUT/deletion per account; use resource versioning if conflict detection is needed.
        return users.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
