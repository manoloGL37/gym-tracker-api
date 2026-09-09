package dev.manuel.gymtracker_api.exercise.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "exercise_aliases")
@Getter
@Setter
@NoArgsConstructor
public class ExerciseAlias {

    @Id
    private UUID id;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(nullable = false, length = 10)
    private String language;

    @Column(nullable = false, length = 255)
    private String alias;
}