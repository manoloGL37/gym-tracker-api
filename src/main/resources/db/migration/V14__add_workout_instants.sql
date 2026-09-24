-- ponytail: legacy wall-clock rows stay untouched; their original zone cannot be recovered safely.
ALTER TABLE workouts ADD COLUMN started_at_instant TIMESTAMP WITH TIME ZONE;
ALTER TABLE workouts ADD COLUMN completed_at_instant TIMESTAMP WITH TIME ZONE;
ALTER TABLE workouts ADD COLUMN calendar_zone VARCHAR(100);
