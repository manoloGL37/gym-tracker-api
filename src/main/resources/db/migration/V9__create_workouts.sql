CREATE TABLE workouts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    routine_id UUID,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_workouts_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_workouts_routine
        FOREIGN KEY (routine_id)
        REFERENCES routines(id)
);