ALTER TABLE routine_exercises
    ADD COLUMN laterality_override VARCHAR(20),
    ADD CONSTRAINT chk_routine_exercise_laterality_override
        CHECK (laterality_override IN ('BILATERAL', 'UNILATERAL'));
