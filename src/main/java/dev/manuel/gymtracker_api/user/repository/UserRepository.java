package dev.manuel.gymtracker_api.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import dev.manuel.gymtracker_api.user.model.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
}
