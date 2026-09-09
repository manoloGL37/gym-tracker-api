CREATE TABLE exercises (
    id UUID PRIMARY KEY,

    owner_id UUID,

    source VARCHAR(50) NOT NULL,
    source_id VARCHAR(255),

    category VARCHAR(100),
    equipment VARCHAR(100),
    target_muscle VARCHAR(100),
    muscle_group VARCHAR(100),

    secondary_muscles TEXT[],

    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,

    CONSTRAINT fk_exercises_owner
        FOREIGN KEY (owner_id)
        REFERENCES users(id)
);

CREATE TABLE exercise_translations (
    id UUID PRIMARY KEY,

    exercise_id UUID NOT NULL,
    language VARCHAR(10) NOT NULL,
    name VARCHAR(255) NOT NULL,
    instructions TEXT,

    CONSTRAINT fk_exercise_translations_exercise
        FOREIGN KEY (exercise_id)
        REFERENCES exercises(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_exercise_translation_language
        UNIQUE (exercise_id, language)
);

CREATE TABLE exercise_aliases (
    id UUID PRIMARY KEY,

    exercise_id UUID NOT NULL,
    language VARCHAR(10) NOT NULL,
    alias VARCHAR(255) NOT NULL,

    CONSTRAINT fk_exercise_aliases_exercise
        FOREIGN KEY (exercise_id)
        REFERENCES exercises(id)
        ON DELETE CASCADE
);