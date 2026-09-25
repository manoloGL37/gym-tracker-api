ALTER TABLE workouts ADD COLUMN name_snapshot VARCHAR(150);
ALTER TABLE workout_exercises ADD COLUMN exercise_name_snapshot VARCHAR(255);
ALTER TABLE workout_exercises ADD COLUMN client_id UUID;
ALTER TABLE workout_exercises ALTER COLUMN exercise_id DROP NOT NULL;

-- ponytail: current names are the best available backfill; prior names cannot be recovered.
UPDATE workouts w SET name_snapshot = r.name
FROM routines r WHERE w.routine_id = r.id;

UPDATE workout_exercises we SET exercise_name_snapshot = (
    SELECT et.name FROM exercise_translations et
    WHERE et.exercise_id = we.exercise_id
    ORDER BY CASE WHEN et.language = 'en' THEN 0 ELSE 1 END, et.language
    LIMIT 1
);

CREATE UNIQUE INDEX uq_workout_exercises_workout_client_id
    ON workout_exercises(workout_id, client_id) WHERE client_id IS NOT NULL;
