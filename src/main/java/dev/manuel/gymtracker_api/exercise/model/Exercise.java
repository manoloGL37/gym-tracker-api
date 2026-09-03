package dev.manuel.gymtracker_api.exercise.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "exercises")
@Getter
@Setter
@NoArgsConstructor
public class Exercise {

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "source_id", length = 255)
    private String sourceId;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String equipment;

    @Column(name = "target_muscle", length = 100)
    private String targetMuscle;

    @Column(name = "muscle_group", length = 100)
    private String muscleGroup;

    @Column(name = "secondary_muscles", columnDefinition = "text[]")
    private String[] secondaryMuscles;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}