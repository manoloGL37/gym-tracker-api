package dev.manuel.gymtracker_api.exercise.specification;

import dev.manuel.gymtracker_api.exercise.model.Exercise;
import dev.manuel.gymtracker_api.exercise.model.ExerciseAlias;
import dev.manuel.gymtracker_api.exercise.model.ExerciseTranslation;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class ExerciseSpecification {

    private ExerciseSpecification() {
    }

    public static Specification<Exercise> availableForUser(UUID userId) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.and(
                        criteriaBuilder.isNull(root.get("deletedAt")),
                        criteriaBuilder.or(
                                criteriaBuilder.isNull(root.get("ownerId")),
                                criteriaBuilder.equal(
                                        root.get("ownerId"),
                                        userId
                                )
                        )
                );
    }

    public static Specification<Exercise> search(String search) {
        return (root, query, criteriaBuilder) -> {

            String pattern = "%" + search.trim().toLowerCase() + "%";

            Subquery<UUID> translationSubquery =
                    query.subquery(UUID.class);

            var translationRoot =
                    translationSubquery.from(ExerciseTranslation.class);

            translationSubquery
                    .select(translationRoot.get("exerciseId"))
                    .where(
                            criteriaBuilder.equal(
                                    translationRoot.get("exerciseId"),
                                    root.get("id")
                            ),
                            criteriaBuilder.like(
                                    criteriaBuilder.lower(
                                            translationRoot.get("name")
                                    ),
                                    pattern
                            )
                    );

            Subquery<UUID> aliasSubquery =
                    query.subquery(UUID.class);

            var aliasRoot =
                    aliasSubquery.from(ExerciseAlias.class);

            aliasSubquery
                    .select(aliasRoot.get("exerciseId"))
                    .where(
                            criteriaBuilder.equal(
                                    aliasRoot.get("exerciseId"),
                                    root.get("id")
                            ),
                            criteriaBuilder.like(
                                    criteriaBuilder.lower(
                                            aliasRoot.get("alias")
                                    ),
                                    pattern
                            )
                    );

            return criteriaBuilder.or(
                    criteriaBuilder.exists(translationSubquery),
                    criteriaBuilder.exists(aliasSubquery)
            );
        };
    }

    public static Specification<Exercise> hasCategory(String category) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("category")),
                        category.trim().toLowerCase()
                );
    }

    public static Specification<Exercise> hasEquipment(String equipment) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("equipment")),
                        equipment.trim().toLowerCase()
                );
    }

    public static Specification<Exercise> hasMuscleGroup(String muscleGroup) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("muscleGroup")),
                        muscleGroup.trim().toLowerCase()
                );
    }

    public static Specification<Exercise> hasTargetMuscle(String targetMuscle) {
        return (root, query, criteriaBuilder) ->
                criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("targetMuscle")),
                        targetMuscle.trim().toLowerCase()
                );
    }
}