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
        UUID setId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
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
            statement.execute("INSERT INTO workout_sets(id,client_id,workout_exercise_id,set_number,weight,reps) VALUES ('" + setId + "','" + clientId + "','" + child + "',1,22.50,10)");
        }
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).load().migrate();
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).load().validate();
        assertEquals(0, Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).load().migrate().migrationsExecuted);
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
            result = statement.executeQuery("SELECT id,client_id,weight,reps,duration_seconds,left_reps,right_reps,completed,version FROM workout_sets WHERE id='" + setId + "'");
            result.next();
            assertEquals(setId, result.getObject(1));
            assertEquals(clientId, result.getObject(2));
            assertEquals(new java.math.BigDecimal("22.50"), result.getBigDecimal(3));
            assertEquals(10, result.getInt(4));
            assertNull(result.getObject(5));
            assertNull(result.getObject(6));
            assertNull(result.getObject(7));
            assertEquals(true, result.getBoolean(8));
            assertEquals(0, result.getLong(9));
            result = statement.executeQuery("SELECT laterality,measurement FROM workout_exercises WHERE id='" + child + "'");
            result.next();
            assertNull(result.getString(1));
            assertNull(result.getString(2));

        }
    }

    @Test
    void upgradesV16PreservingEveryHistoricalColumnAndIndex() throws Exception {
        String schema = "v16_tracking_upgrade";
        var flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).target("16").load();
        flyway.migrate();
        try (var connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            connection.setSchema(schema);
            statement.execute("""
                    INSERT INTO users VALUES
                    ('00000000-0000-0000-0000-000000000001','upgrade@test.invalid','synthetic',now(),now());
                    INSERT INTO exercises(id,owner_id,source,client_id,created_at,updated_at)
                    VALUES ('00000000-0000-0000-0000-000000000002',
                            '00000000-0000-0000-0000-000000000001','USER',
                            '00000000-0000-0000-0000-000000000012',now(),now());
                    INSERT INTO routines(id,user_id,name,client_id,created_at,updated_at)
                    VALUES ('00000000-0000-0000-0000-000000000003',
                            '00000000-0000-0000-0000-000000000001','Historical routine',
                            '00000000-0000-0000-0000-000000000013',now(),now());
                    INSERT INTO routine_exercises(id,routine_id,exercise_id,position,sets,target_reps,rest_seconds,notes)
                    VALUES ('00000000-0000-0000-0000-000000000004',
                            '00000000-0000-0000-0000-000000000003',
                            '00000000-0000-0000-0000-000000000002',0,3,10,60,'Historical target');
                    INSERT INTO routine_rotation VALUES
                    ('00000000-0000-0000-0000-000000000001',0,'00000000-0000-0000-0000-000000000003');
                    INSERT INTO workouts(id,user_id,routine_id,client_id,started_at,completed_at,created_at,name_snapshot,notes)
                    VALUES ('00000000-0000-0000-0000-000000000005',
                            '00000000-0000-0000-0000-000000000001',
                            '00000000-0000-0000-0000-000000000003',
                            '00000000-0000-0000-0000-000000000015',
                            '2024-10-27 02:30:00','2024-10-27 03:30:00',now(),'Performed routine','Original notes');
                    INSERT INTO workout_exercises(id,workout_id,exercise_id,position,client_id,exercise_name_snapshot)
                    VALUES ('00000000-0000-0000-0000-000000000006',
                            '00000000-0000-0000-0000-000000000005',
                            '00000000-0000-0000-0000-000000000002',0,
                            '00000000-0000-0000-0000-000000000016','Performed exercise');
                    INSERT INTO workout_sets(id,workout_exercise_id,client_id,set_number,weight,reps,rpe)
                    VALUES ('00000000-0000-0000-0000-000000000007',
                            '00000000-0000-0000-0000-000000000006',
                            '00000000-0000-0000-0000-000000000017',1,22.50,10,7.5),
                           ('00000000-0000-0000-0000-000000000008',
                            '00000000-0000-0000-0000-000000000006',NULL,2,0,8,NULL);
                    INSERT INTO refresh_tokens(id,user_id,family_id,token_hash,created_at,expires_at)
                    VALUES ('00000000-0000-0000-0000-000000000009',
                            '00000000-0000-0000-0000-000000000001',
                            '00000000-0000-0000-0000-000000000019',repeat('a',64),now(),now()+interval '1 day');
                    """);
            // Capture all V16 columns, including nullable IDs, snapshots, timestamps and auth hashes.
            var queries = new java.util.LinkedHashMap<String, String>();
            var before = new java.util.LinkedHashMap<String, String>();
            for (String table : java.util.List.of("users", "exercises", "routines", "routine_exercises",
                    "routine_rotation", "workouts", "workout_exercises", "workout_sets", "refresh_tokens")) {
                String columns;
                try (var result = statement.executeQuery("SELECT string_agg(quote_ident(column_name), ',' ORDER BY ordinal_position)"
                        + " FROM information_schema.columns WHERE table_schema='" + schema + "' AND table_name='" + table + "'")) {
                    result.next();
                    columns = result.getString(1);
                }
                queries.put(table, "SELECT jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text FROM (SELECT "
                        + columns + " FROM " + table + ") t");
            }
            queries.put("indexes", "SELECT jsonb_agg(indexdef ORDER BY indexname)::text FROM pg_indexes WHERE schemaname='" + schema + "'");
            for (var entry : queries.entrySet()) {
                try (var result = statement.executeQuery(entry.getValue())) {
                    result.next();
                    before.put(entry.getKey(), result.getString(1));
                }
            }
            var upgraded = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas(schema).load();
            assertEquals(2, upgraded.migrate().migrationsExecuted);
            upgraded.validate();
            assertEquals("18", upgraded.info().current().getVersion().toString());
            assertEquals(0, upgraded.migrate().migrationsExecuted);
            for (var entry : queries.entrySet()) {
                try (var result = statement.executeQuery(entry.getValue())) {
                    result.next();
                    assertEquals(before.get(entry.getKey()), result.getString(1), entry.getKey());
                }
            }
            try (var result = statement.executeQuery("SELECT count(*) FROM workout_sets WHERE completed AND version=0"
                    + " AND duration_seconds IS NULL AND left_completed IS NULL AND right_completed IS NULL AND last_mutation_id IS NULL")) {
                result.next();
                assertEquals(2, result.getInt(1));
            }
        }
    }

    @Test
    void upgradesV17AdditivelyAndPersistsOnlyValidOverrides() throws Exception {
        String schema = "v17_laterality_upgrade";
        var config = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema);
        config.target("17").load().migrate();
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            connection.setSchema(schema);
            statement.execute("""
                    INSERT INTO users(id,email,password_hash,created_at,updated_at)
                    VALUES ('00000000-0000-0000-0000-000000000001','v17@test.invalid','x',now(),now());
                    INSERT INTO exercises(id,source,created_at,updated_at,laterality)
                    VALUES ('00000000-0000-0000-0000-000000000002','USER',now(),now(),'BILATERAL');
                    INSERT INTO routines(id,user_id,name,created_at,updated_at)
                    VALUES ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000001','Leg A',now(),now());
                    INSERT INTO routine_exercises(id,routine_id,exercise_id,position,sets,target_reps,rest_seconds)
                    VALUES ('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000003',
                            '00000000-0000-0000-0000-000000000002',0,3,12,60);
                    INSERT INTO workouts(id,user_id,routine_id,started_at,created_at)
                    VALUES ('00000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000001',
                            '00000000-0000-0000-0000-000000000003',now(),now());
                    INSERT INTO workout_exercises(id,workout_id,exercise_id,position)
                    VALUES ('00000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000005',
                            '00000000-0000-0000-0000-000000000002',0);
                    """);
            var queries = new java.util.LinkedHashMap<String, String>();
            var before = new java.util.LinkedHashMap<String, String>();
            for (String table : java.util.List.of("exercises", "routines", "routine_exercises", "workouts", "workout_exercises")) {
                queries.put(table, "SELECT jsonb_agg(to_jsonb(t) ORDER BY id)::text FROM " + table + " t");
                try (var result = statement.executeQuery(queries.get(table))) {
                    result.next(); before.put(table, result.getString(1));
                }
            }
            var upgrade = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .schemas(schema).load();
            assertEquals(1, upgrade.migrate().migrationsExecuted);
            upgrade.validate();
            assertEquals(0, upgrade.migrate().migrationsExecuted);
            queries.put("routine_exercises", "SELECT jsonb_agg(to_jsonb(t)-'laterality_override' ORDER BY id)::text FROM routine_exercises t");
            for (var entry : queries.entrySet()) {
                try (var result = statement.executeQuery(entry.getValue())) {
                    result.next(); assertEquals(before.get(entry.getKey()), result.getString(1), entry.getKey());
                }
            }
            try (var result = statement.executeQuery("SELECT laterality_override FROM routine_exercises")) {
                result.next(); assertNull(result.getString(1));
            }
            for (String value : java.util.List.of("'UNILATERAL'", "'BILATERAL'", "NULL")) {
                statement.executeUpdate("UPDATE routine_exercises SET laterality_override=" + value);
                try (var result = statement.executeQuery("SELECT laterality_override FROM routine_exercises")) {
                    result.next(); assertEquals(value.equals("NULL") ? null : value.replace("'", ""), result.getString(1));
                }
            }
            org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,
                    () -> statement.executeUpdate("UPDATE routine_exercises SET laterality_override='LEFT'"));
        }
    }
}
