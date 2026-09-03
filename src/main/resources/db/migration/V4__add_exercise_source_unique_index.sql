CREATE UNIQUE INDEX uq_exercises_source_source_id
    ON exercises(source, source_id)
    WHERE source_id IS NOT NULL;