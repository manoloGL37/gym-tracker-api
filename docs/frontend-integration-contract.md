# Frontend Integration Contract

## Base URL and CORS

| Environment | API base URL |
| --- | --- |
| Local | `http://localhost:8080` |
| Production | `https://gym-tracker-api-s70k.onrender.com` |

All application routes use the `/api` prefix. Swagger is public at `/swagger-ui/index.html` and OpenAPI JSON at `/v3/api-docs`.

Cross-origin browser access is enabled only for origins listed in `APP_CORS_ALLOWED_ORIGINS`. It is a comma-separated list of origins, without paths or trailing slash:

```text
APP_CORS_ALLOWED_ORIGINS=http://localhost:4200,https://gym-tracker-eight-dun.vercel.app
```

Its default is `http://localhost:4200,https://gym-tracker-eight-dun.vercel.app`. The policy allows `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, and `OPTIONS`, and the `Authorization`, `Content-Type`, `Accept`, and `Origin` headers. It does not allow `*`, does not enable credentials/cookies, and caches preflight for one hour. JWTs continue to be sent in `Authorization: Bearer <accessToken>`.

## Authentication and errors

Register using public `POST /api/users`, then log in through public `POST /api/auth/login`; registration does not authenticate the user. Every `/api` route except those two requires the bearer JWT. Access tokens expire after one hour. There is no refresh-token flow, so an Angular interceptor must treat `401` as re-authentication required while retaining unsynchronized local data.

Ownership always comes from the JWT subject. Foreign private resources, unavailable exercises, and deleted resources are generally represented as `404`; no request body may provide an owner/user ID.

Application validation errors use `{ status, code: "VALIDATION_ERROR", errors, timestamp }`; domain errors use `{ status, code, message, timestamp }`. Security's 401 response is still produced by Spring Security and must be handled by HTTP status rather than assumed to have the application error shape.

## Exercise contract

`GET /api/exercises`, `GET /api/exercises/{id}`, and exercise create/update responses now include:

```ts
interface Exercise {
  id: string;
  clientId: string | null;
  source: 'EXERCISES_DATASET' | 'USER';
  sourceId: string | null;
  editable: boolean;
  deletable: boolean;
  category: string | null;
  equipment: string | null;
  targetMuscle: string | null;
  muscleGroup: string | null;
  secondaryMuscles: string[] | null;
  translations: Array<{ language: string; name: string; instructions: string | null }>;
  aliases: Array<{ language: string; alias: string }>;
}
```

`source`/`sourceId` are the stable catalog identity for dataset exercises. `editable` and `deletable` are the permitted actions for the authenticated caller; `ownerId` remains private. A user-created exercise can carry an optional UUID `clientId` in `POST /api/exercises`. The server returns the existing caller-owned exercise on a retry with the same ID. Global exercises cannot be overwritten because their owner and their source are server controlled.

## Routines and workouts

Routine request/response additions are backward-compatible:

```ts
interface CreateRoutineRequest {
  clientId?: string;
  name: string;
  description?: string | null;
  exercises: RoutineExerciseRequest[];
}
interface RoutineSummary { id: string; clientId: string | null; /* existing fields */ }
interface Routine extends RoutineSummary { exercises: RoutineExercise[]; }
```

`exercises` is required and may be `[]`. Duplicate positions are consistently rejected with `400 DUPLICATE_EXERCISE_POSITION`. Referenced exercises must be global or owned by the JWT user.

`POST /api/workouts` still creates its workout-exercise snapshot from a non-deleted routine owned by the caller. It now accepts optional domain timestamps and migration identity:

```ts
interface CreateWorkoutRequest {
  clientId?: string;
  routineId: string;
  startedAt?: string;
  completedAt?: string | null;
  notes?: string | null;
}
interface Workout {
  id: string;
  clientId: string | null;
  routineId: string;
  startedAt: string;
  completedAt: string | null;
  notes: string | null;
  exercises: WorkoutExercise[];
  createdAt: string;
}
```

When omitted, `startedAt` remains server `now`, preserving the former behavior. `createdAt` is always the database/audit creation time; it is never backdated. `startedAt` and `completedAt` represent when the session occurred, so historical duration is preserved as their difference.

Sets remain created through `POST /api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets` and now accept/return optional `clientId: UUID`:

```ts
interface WorkoutSetRequest { clientId?: string; setNumber: number; weight: number; reps: number; rpe?: number | null; }
interface WorkoutSet extends WorkoutSetRequest { id: string; }
```

The `workoutExerciseId` comes from the returned workout snapshot, never from a local ID. Existing APIs still do not edit/delete individual workout exercises or sets, and cannot create an ad-hoc workout exercise. Thus a local historical workout must correspond to a routine snapshot that can be created on the server. Preserve any unmatched/ad-hoc local snapshot in the local archive and surface it for user resolution rather than silently dropping it.

## Retry-safe local migration

No bulk migration endpoint was added. Resource-level operations are smaller, reuse existing validation/ownership rules, fit Render/Neon request limits, and let a browser resume after a cold start or lost response. A single bulk request would add a large transactional payload and a second validation model without removing the need for local recovery state.

The frontend must generate one stable UUID with `crypto.randomUUID()` for every local **custom exercise**, **routine**, **workout**, and **workout set** before its first server request. Send it as `clientId` on every retry. It is not needed for routine-exercise or workout-exercise rows: both are server-owned child snapshots deterministically created from the routine; workout exercises are mapped by the returned `position` and `exerciseId`.

For a non-null client ID, the server returns the existing resource belonging to that same authenticated user/resource parent instead of creating another. PostgreSQL enforces the same guarantee with partial unique indexes:

- `exercises(owner_id, client_id)`
- `routines(user_id, client_id)`
- `workouts(user_id, client_id)`
- `workout_sets(workout_exercise_id, client_id)`

Null client IDs preserve normal existing CRUD behavior and are not deduplicated. A retry must reuse the original client ID and payload; changing a payload under an already-used ID does not overwrite the persisted resource. IDs are scoped to the owner (or owning workout exercise), so one user cannot retrieve or overwrite another user's migration data by guessing an ID.

## Required synchronization sequence

1. Keep the local store and a durable per-item migration ledger intact. Register, log in, and call `GET /api/users/me`.
2. Fetch `/api/exercises`. Map known dataset records by `source === 'EXERCISES_DATASET'` plus `sourceId`; create local custom exercises with stable `clientId`s and save each returned server UUID.
3. Create/retry routines after substituting local exercise IDs with server UUIDs. Store routine UUIDs and returned routine exercise positions.
4. Create/retry workouts from their mapped routine using historical `startedAt`, `completedAt`, and `notes`. Store the returned workout UUID and its returned workout-exercise IDs.
5. Create/retry each set using the corresponding returned workout-exercise ID and its stable set `clientId`.
6. Read back each routine/workout, verify expected child IDs and set counts, then mark only verified ledger items synchronized.
7. Switch authenticated reads/writes to the API. Retain local data until verification succeeds; do not erase unsupported ad-hoc snapshots.

Do not use a client-supplied user ID, owner ID, server UUID, or a guessed private exercise ID as a migration authority.

## Statistics and OpenAPI

`GET /api/statistics/exercises/{exerciseId}?from=YYYY-MM-DD&to=YYYY-MM-DD` returns `200` with zero-valued aggregates for an unknown UUID or for no caller data. Its OpenAPI annotation now matches that behavior and no longer documents a 404. Statistics range filtering uses the workout domain `startedAt`, so imported historical sessions appear in their original dates.

## Remaining limitations and Angular order

There are still no refresh tokens, logout/revocation endpoint, account editing, alias management API, workout deletion, set/workout-exercise editing or deletion, or arbitrary historical workout-exercise import. Date-times are `LocalDateTime` values with no offset; Angular must apply a documented product timezone consistently.

Suggested Angular order: configure the API environment/CORS; implement auth state and bearer/401 interceptor; mirror DTOs and error parsing; integrate catalog and custom exercises; routines; current workouts and sets; historical migration ledger; then statistics after server data exists. Do not remove the local persistence layer until migration read-back succeeds.
