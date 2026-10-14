-- Existing metrics, IDs, relationships and deduplication keys remain unchanged.
-- NULL laterality means unknown, never inferred from a name or present-day catalogue.
ALTER TABLE exercises ADD COLUMN laterality VARCHAR(20), ADD COLUMN measurement VARCHAR(20);
ALTER TABLE workout_exercises ADD COLUMN laterality VARCHAR(20), ADD COLUMN measurement VARCHAR(20);
ALTER TABLE workout_sets
    ALTER COLUMN weight DROP NOT NULL,
    ALTER COLUMN reps DROP NOT NULL,
    ADD COLUMN duration_seconds INTEGER CHECK (duration_seconds > 0),
    ADD COLUMN completed BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_mutation_id UUID;
ALTER TABLE routine_exercises
    ALTER COLUMN target_reps DROP NOT NULL,
    ADD COLUMN target_duration_seconds INTEGER CHECK (target_duration_seconds > 0),
    ADD CONSTRAINT chk_routine_primary_target CHECK ((target_reps IS NULL) <> (target_duration_seconds IS NULL));
ALTER TABLE exercises ADD CONSTRAINT chk_exercises_laterality CHECK (laterality IN ('BILATERAL','UNILATERAL')), ADD CONSTRAINT chk_exercises_measurement CHECK (measurement IN ('REPS','DURATION'));
ALTER TABLE workout_exercises ADD CONSTRAINT chk_workout_exercises_laterality CHECK (laterality IN ('BILATERAL','UNILATERAL')), ADD CONSTRAINT chk_workout_exercises_measurement CHECK (measurement IN ('REPS','DURATION'));
ALTER TABLE workout_sets
    ADD COLUMN left_reps INTEGER CHECK (left_reps > 0),
    ADD COLUMN left_duration_seconds INTEGER CHECK (left_duration_seconds > 0),
    ADD COLUMN left_weight NUMERIC(6,2) CHECK (left_weight >= 0),
    ADD COLUMN left_completed BOOLEAN,
    ADD CONSTRAINT chk_left_primary_metric CHECK (left_reps IS NULL OR left_duration_seconds IS NULL),
    ADD CONSTRAINT chk_left_result_presence CHECK (left_completed IS NOT NULL OR
        (left_reps IS NULL AND left_duration_seconds IS NULL AND left_weight IS NULL));
ALTER TABLE workout_sets
    ADD COLUMN right_reps INTEGER CHECK (right_reps > 0),
    ADD COLUMN right_duration_seconds INTEGER CHECK (right_duration_seconds > 0),
    ADD COLUMN right_weight NUMERIC(6,2) CHECK (right_weight >= 0),
    ADD COLUMN right_completed BOOLEAN,
    ADD CONSTRAINT chk_right_primary_metric CHECK (right_reps IS NULL OR right_duration_seconds IS NULL),
    ADD CONSTRAINT chk_right_result_presence CHECK (right_completed IS NOT NULL OR
        (right_reps IS NULL AND right_duration_seconds IS NULL AND right_weight IS NULL));
ALTER TABLE workout_sets ADD CONSTRAINT chk_set_primary_metric CHECK (reps IS NULL OR duration_seconds IS NULL);
