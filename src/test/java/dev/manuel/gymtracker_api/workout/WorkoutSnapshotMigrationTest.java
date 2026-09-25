package dev.manuel.gymtracker_api.workout;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Testcontainers
class WorkoutSnapshotMigrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Test
    void upgradesV14RowsWithoutInventingMissingNamesOrInstants() throws Exception {
        String schema = "snapshot_migration_test";
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).target("14").load().migrate();
        UUID user = UUID.randomUUID();
        UUID routine = UUID.randomUUID();
        UUID exercise = UUID.randomUUID();
        UUID workout = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        UUID missingNameWorkout = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            connection.setSchema(schema);
            statement.execute("INSERT INTO users(id,email,password_hash,created_at,updated_at) VALUES ('" + user + "','legacy@test.com','x',now(),now())");
            statement.execute("INSERT INTO routines(id,user_id,name,created_at,updated_at) VALUES ('" + routine + "','" + user + "','Push A',now(),now())");
            statement.execute("INSERT INTO exercises(id,source,created_at,updated_at) VALUES ('" + exercise + "','USER',now(),now())");
            statement.execute("INSERT INTO exercise_translations(id,exercise_id,language,name) VALUES ('" + UUID.randomUUID() + "','" + exercise + "','en','Bench Press')");
            statement.execute("INSERT INTO workouts(id,user_id,routine_id,started_at,created_at) VALUES ('" + workout + "','" + user + "','" + routine + "',now(),now())");
            statement.execute("INSERT INTO workouts(id,user_id,started_at,created_at) VALUES ('" + missingNameWorkout + "','" + user + "',now(),now())");
            statement.execute("INSERT INTO workout_exercises(id,workout_id,exercise_id,position) VALUES ('" + child + "','" + workout + "','" + exercise + "',0)");
        }
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).load().migrate();
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            connection.setSchema(schema);
            var result = statement.executeQuery("SELECT name_snapshot,started_at_instant FROM workouts WHERE id='" + workout + "'");
            result.next();
            assertEquals("Push A", result.getString(1));
            assertNull(result.getObject(2));
            result = statement.executeQuery("SELECT exercise_name_snapshot FROM workout_exercises WHERE id='" + child + "'");
            result.next();
            assertEquals("Bench Press", result.getString(1));
            result = statement.executeQuery("SELECT name_snapshot FROM workouts WHERE id='" + missingNameWorkout + "'");
            result.next();
            assertNull(result.getString(1));
        }
    }
}
