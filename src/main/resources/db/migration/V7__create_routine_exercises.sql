CREATE TABLE routine_exercises (
    id UUID PRIMARY KEY,
    routine_id UUID NOT NULL,
    exercise_id UUID NOT NULL,
    position INTEGER NOT NULL,
    sets INTEGER NOT NULL,
    target_reps INTEGER NOT NULL,
    rest_seconds INTEGER NOT NULL,
    notes VARCHAR(500),

    CONSTRAINT fk_routine_exercises_routine
        FOREIGN KEY (routine_id)
        REFERENCES routines(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_routine_exercises_exercise
        FOREIGN KEY (exercise_id)
        REFERENCES exercises(id),

    CONSTRAINT uq_routine_exercise_position
        UNIQUE (routine_id, position),

    CONSTRAINT chk_routine_exercise_position
        CHECK (position >= 0),

    CONSTRAINT chk_routine_exercise_sets
        CHECK (sets > 0),

    CONSTRAINT chk_routine_exercise_target_reps
        CHECK (target_reps > 0),

    CONSTRAINT chk_routine_exercise_rest_seconds
        CHECK (rest_seconds >= 0)
);