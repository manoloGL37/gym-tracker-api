ALTER TABLE exercises ADD COLUMN client_id UUID;
ALTER TABLE routines ADD COLUMN client_id UUID;
ALTER TABLE workouts ADD COLUMN client_id UUID;
ALTER TABLE workout_sets ADD COLUMN client_id UUID;

CREATE UNIQUE INDEX uq_exercises_owner_client_id
    ON exercises(owner_id, client_id)
    WHERE client_id IS NOT NULL;

CREATE UNIQUE INDEX uq_routines_user_client_id
    ON routines(user_id, client_id)
    WHERE client_id IS NOT NULL;

CREATE UNIQUE INDEX uq_workouts_user_client_id
    ON workouts(user_id, client_id)
    WHERE client_id IS NOT NULL;

CREATE UNIQUE INDEX uq_workout_sets_exercise_client_id
    ON workout_sets(workout_exercise_id, client_id)
    WHERE client_id IS NOT NULL;
