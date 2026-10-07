CREATE TABLE routine_rotation (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    position INTEGER NOT NULL CHECK (position >= 0),
    routine_id UUID NOT NULL REFERENCES routines(id),
    PRIMARY KEY (user_id, position),
    -- Check uniqueness at commit so an in-place reorder can temporarily repeat an ID.
    CONSTRAINT uq_rotation_routine UNIQUE (user_id, routine_id) DEFERRABLE INITIALLY DEFERRED
);
