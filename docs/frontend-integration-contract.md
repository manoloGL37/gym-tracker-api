# Frontend Integration Contract

This contract is derived from the Spring controllers, DTOs, validation annotations, services, security configuration, repositories/migrations, and OpenAPI annotations in this repository. Field names are the JSON names emitted by Jackson from Java records (camelCase).

## 1. Backend Overview

Gym Tracker is a stateless REST modular monolith for account registration, a shared/private exercise catalog, user routines, workouts created from routines, recorded set performance, and calculated statistics. Java 21 / Spring Boot / JPA persist to PostgreSQL through Flyway. Modules are `auth`, `user`, `exercise`, `routine`, `workout`, and `statistics`.

The database is the intended source of truth for authenticated users. Authentication is a signed JWT containing the user UUID as its subject; no server session is kept.

## 2. Base URLs and Environments

| Environment | Base URL | Evidence |
| --- | --- | --- |
| Local API | `http://localhost:8080` | `server.port` defaults to `8080`; Docker maps `8080:8080`. |
| API prefix | `/api` | All application controllers use it. |
| Production | Not committed / not determinable | README names Render + Neon but explicitly stores no production URL. |

The frontend should supply the API origin as an Angular environment value. Local PostgreSQL is exposed on `5433`, but that is not a frontend endpoint. Swagger is public at `/swagger-ui/index.html` and OpenAPI JSON at `/v3/api-docs`.

No CORS configuration or `@CrossOrigin` annotation exists. A browser frontend on a different origin will require deployment/proxy alignment or a backend CORS change before direct cross-origin calls work.

## 3. Authentication Model

### Flow

1. Register with `POST /api/users`. It creates an account and returns a `UserResponse`; **it does not return a token and does not log the user in**.
2. Log in with `POST /api/auth/login` using the same email/password.
3. Send the returned token on every protected API request: `Authorization: Bearer <accessToken>`.
4. Call `GET /api/users/me` to establish the current profile.

`POST /api/users`, `POST /api/auth/login`, Swagger UI, and `/v3/api-docs/**` are public. Every other route is protected. The token is a compact JWS signed with the configured HMAC key; claims are `sub` (user UUID), `iat`, and `exp`. Default expiration is `3,600,000` ms (one hour) from issuance. There is no refresh-token endpoint, refresh token, token revocation, or token-expiry value in `AuthResponse`.

```http
POST /api/auth/login
Content-Type: application/json

{ "email": "ana@example.com", "password": "Password123!" }
```

```json
{ "accessToken": "eyJhbGciOiJIUzI1NiJ9..." }
```

Missing, malformed, expired, invalid-signature, or otherwise invalid tokens do not authenticate the request. A protected request then receives HTTP `401`; the security entry point uses `sendError(401)`, not the application's `ErrorResponse` record. Treat any `401` as logout/re-authentication required. A valid token for a deleted user can pass the filter but `/me` later returns `404`.

There are no roles or authorities and no controller-level role restriction. Consequently an application `403` is not a normal ownership outcome: attempts to access a foreign resource are deliberately represented as `404`. Spring Security can still produce its framework-default `403` for an access-denied condition, but no custom JSON handler is configured.

## 4. Current User / User Management

| Method and path | Auth | Request | Success | Errors / rules |
| --- | --- | --- | --- | --- |
| `POST /api/users` | No | `CreateUserRequest` | `201 UserResponse` | `400` validation; `409 EMAIL_ALREADY_EXISTS` if exact stored email already exists. Password is BCrypt-hashed and never returned. Does not authenticate. |
| `GET /api/users/me` | Yes | — | `200 UserResponse` | `401` absent/invalid JWT; `404 RESOURCE_NOT_FOUND` if JWT subject has no user row. The subject UUID, never a client ID, determines the user. |

Use `/api/users/me` as the only current-user/profile read API. There is no user update, deletion, list, password change, or email-change endpoint.

```json
// POST /api/users request
{ "email": "ana@example.com", "password": "Password123!" }

// UserResponse
{
  "id": "5b7c6dcc-e70e-442c-a29e-cb2b516b72a3",
  "email": "ana@example.com",
  "createdAt": "2026-09-10T14:30:00.123456"
}
```

## 5. Exercises API

All exercise routes require a JWT. The catalog returned to a user consists of global dataset exercises (`owner_id IS NULL`) plus that user's custom exercises (`owner_id = JWT subject`). Soft-deleted exercises are excluded. The response deliberately does **not** expose `ownerId`, `source`, `sourceId`, or timestamps, so the frontend cannot reliably label an individual returned exercise as global versus custom from the current contract.

| Method and path | Parameters / body | Success | Actual behavior and errors |
| --- | --- | --- | --- |
| `GET /api/exercises` | Query: `search`, `category`, `equipment`, `muscleGroup`, `targetMuscle`; `page` default `0`, min `0`; `size` default `20`, min `1`, max `100` | `200 ExercisePageResponse` | Lists only available exercises. Filters are ANDed; nonblank textual filters are case-insensitive exact matches after trim. `search` is a trimmed, case-insensitive substring over translation `name` or alias `alias`, any language. Sorted server-side by `createdAt DESC`, then `id ASC`; no caller-selected sort. Invalid page/size is `400`. |
| `GET /api/exercises/{id}` | UUID path `id` | `200 ExerciseResponse` | Reads a global or caller-owned, non-deleted exercise. Missing, deleted, or another user's custom exercise returns `404 RESOURCE_NOT_FOUND`. |
| `POST /api/exercises` | `CreateExerciseRequest` | `201 ExerciseResponse` | Creates a user-owned exercise with internal source `USER`. The annotation only imposes min 1 when `translations` is non-null, but service code immediately dereferences it: clients must send at least one valid item; omitted/null can currently become an unhandled server error. No alias creation API. |
| `PUT /api/exercises/{id}` | `CreateExerciseRequest` | `200 ExerciseResponse` | Full update shape, only for a non-deleted caller-owned exercise; global/foreign/missing is `404`. Existing translations with a requested language are updated and new languages added. **Omitted existing languages are not deleted.** The immediate response contains only saved/requested translations, while a later GET can contain all retained translations. |
| `DELETE /api/exercises/{id}` | UUID path `id` | `204` empty | Soft-deletes only caller-owned exercises. Global, foreign, already-deleted, or missing is `404`. |

`category`, `equipment`, `targetMuscle`, and `muscleGroup` are arbitrary strings, not enums. Dataset import is a startup-only internal feature, not an HTTP endpoint. Dataset identities (`source=EXERCISES_DATASET`, `sourceId`) are not returned, so are unusable as frontend API fields.

Example create/update body:

```json
{
  "category": "strength",
  "equipment": "barbell",
  "targetMuscle": "chest",
  "muscleGroup": "chest",
  "secondaryMuscles": ["triceps", "shoulders"],
  "translations": [
    { "language": "en", "name": "Bench press", "instructions": "Lower with control." },
    { "language": "es", "name": "Press de banca", "instructions": null }
  ]
}
```

## 6. Routines API

All routes require authentication; routines are private to their `userId`. A routine list returns summaries only; retrieve one item to obtain its planned exercises. `DELETE` is a soft delete (`deletedAt` is set), and it does not remove its routine-exercise records. Workouts may still reference the deleted routine at database level, but a new workout cannot be created from it.

| Method and path | Request / parameters | Success | Errors / notes |
| --- | --- | --- | --- |
| `GET /api/routines` | Standard Spring pageable query params: `page`, `size`, `sort` accepted/bound | `200 Page<RoutineSummaryResponse>` | Defaults `page=0`, `size=10`, controller declares `createdAt,DESC`; service overrides all requested sorting to `createdAt DESC, id ASC`. Only non-deleted caller routines. |
| `GET /api/routines/{id}` | UUID `id` | `200 RoutineResponse` | Exercises ordered `position ASC`. Foreign/deleted/missing => `404`. |
| `POST /api/routines` | `CreateRoutineRequest` | `201 RoutineResponse` | Referenced exercises must be global or caller-owned and non-deleted; unavailable/missing exercise => `404`. `exercises` may be empty. It has no `@NotNull`, but service code immediately streams it, so clients must send `[]` rather than omit/null. Creation does not pre-check duplicate positions; the database uniqueness constraint can therefore surface as an unhandled persistence error. |
| `PUT /api/routines/{id}` | `CreateRoutineRequest` | `200 RoutineResponse` | Replaces all routine-exercise rows with supplied array; changed rows receive new IDs. Checks duplicated `position` and returns `400 DUPLICATE_EXERCISE_POSITION`; validates referenced exercises as above. |
| `DELETE /api/routines/{id}` | UUID `id` | `204` empty | Soft delete; foreign/deleted/missing => `404`. |

```json
{
  "name": "Upper body",
  "description": "Monday",
  "exercises": [
    {
      "exerciseId": "8ee1b40d-414b-4f0e-91c7-f89fa246f30c",
      "position": 0,
      "sets": 3,
      "targetReps": 10,
      "restSeconds": 90,
      "notes": "Warm up first"
    }
  ]
}
```

```json
{
  "id": "0f21f83e-df53-4ccc-8d57-c87c5dba7b77",
  "name": "Upper body", "description": "Monday",
  "exercises": [{ "id": "a795f7ab-3c9d-40f4-b105-518d8f995c94", "exerciseId": "8ee1b40d-414b-4f0e-91c7-f89fa246f30c", "position": 0, "sets": 3, "targetReps": 10, "restSeconds": 90, "notes": "Warm up first" }],
  "createdAt": "2026-09-10T14:30:00", "updatedAt": "2026-09-10T14:30:00"
}
```

## 7. Workouts API

Workouts are user-owned sessions created exclusively by cloning a current, non-deleted routine owned by that same user. Creation sets server timestamps and copies each routine exercise's `exerciseId`, `position`, and `notes`; planned `sets`, `targetReps`, and `restSeconds` are not copied into the workout response. There is no standalone workout-exercise creation/update/delete API.

| Method and path | Request / parameters | Success | Errors / behavior |
| --- | --- | --- | --- |
| `POST /api/workouts` | `{ "routineId": "UUID" }` | `201 WorkoutResponse` | Server sets `id`, `startedAt`, `createdAt`; `completedAt` and `notes` start `null`. `404` when routine is foreign, deleted, or missing. No custom historical start time can be supplied. |
| `GET /api/workouts` | Pageable `page`, `size`, `sort` | `200 Page<WorkoutResponse>` | Defaults page `0`, size `10`; service ignores caller sorting and uses `createdAt DESC, id ASC`. Every list element embeds exercises and sets (not a summary). Only caller's workouts. |
| `GET /api/workouts/{id}` | UUID `id` | `200 WorkoutResponse` | Embeds exercises ordered by position and each set ordered set number. Foreign/missing => `404`. |
| `PATCH /api/workouts/{id}` | `UpdateWorkoutRequest` | `200 WorkoutResponse` | Only supplied non-null fields change. `completed:true` sets `completedAt` to server `now`; `completed:false` clears it. There is no client-provided completion time. `notes:null` means leave unchanged, so notes cannot be cleared through this API. |
| `POST /api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets` | `WorkoutSetRequest` | `201 WorkoutSetResponse` | Workout must belong to caller and exercise must belong to that workout; otherwise `404`. One set number per workout exercise; duplicate => `400 DUPLICATE_WORKOUT_SET_NUMBER`. No set update/delete. |

```json
// Add a performed set
{ "setNumber": 1, "weight": 60.00, "reps": 10, "rpe": 8.5 }

// Patch completion and/or notes
{ "completed": true, "notes": "Felt good" }
```

`weight` and `rpe` are JSON numbers represented as Java `BigDecimal`; use numbers, not formatted display strings. `rpe` is nullable. Workout deletion, direct workout creation, custom duration, exercise notes update, and editing/removing a set are not implemented.

## 8. Statistics API

All routes require authentication. Dates are mandatory query parameters in ISO local-date form `YYYY-MM-DD`; both ends are inclusive in the API meaning. Internally, each range is `[from 00:00, day-after-to 00:00)`, using the application/server local date-time basis. `to < from` returns `400 INVALID_REQUEST`; missing/malformed date parameters are Spring request-binding errors (not normalized by the custom handler).

| Method and path | Query | Response / semantics |
| --- | --- | --- |
| `GET /api/statistics/summary` | `from`, `to` | `StatisticsSummaryResponse`. Counts all caller workouts whose `startedAt` is in range, including uncompleted/empty workouts. `sets` counts set rows; `reps` sums reps; `volume` sums `weight * reps`; `maxWeight` is maximum set weight. Empty data returns zero numeric aggregates. |
| `GET /api/statistics/comparison` | `currentFrom`, `currentTo`, `previousFrom`, `previousTo` | Current and previous summaries plus percentage changes `(current - previous) / previous * 100`. If prior metric is zero, change is `0` if current is also zero, otherwise `100`. |
| `GET /api/statistics/evolution` | `from`, `to` | `StatisticsEvolutionResponse`, only dates that have a workout; no zero-filled missing days. Each point has daily metrics using the same formula as summary. |
| `GET /api/statistics/exercises/{exerciseId}` | UUID `exerciseId`; `from`, `to` | `ExerciseStatisticsResponse` aggregates caller workout exercises matching the UUID. It does **not** check that the exercise exists or is accessible, despite the controller's OpenAPI `404` annotation; arbitrary UUID yields zero-valued response if no matching data. Evolution has only days with matching workout-exercise rows. |

Statistics are scoped by `workouts.user_id`, not by the current visibility or ownership of an exercise. Thus past results remain statistically queryable by an exercise UUID even if a custom exercise is later soft-deleted.

## 9. All Frontend-Relevant DTOs

### Requests

| DTO | Field | Type | Required / validation | Client meaning |
| --- | --- | --- | --- | --- |
| `CreateUserRequest` | `email` | string | required; nonblank; valid email | Account email. |
|  | `password` | string | required; nonblank; 8–100 chars | Plaintext sent only to register/login. |
| `LoginRequest` | `email` | string | required; nonblank; valid email | Login identity. |
|  | `password` | string | required; nonblank | Login password; no size annotation here. |
| `CreateExerciseRequest` | `category`, `equipment`, `targetMuscle`, `muscleGroup` | string \| null | optional; max 100 each | Free-text catalog metadata. |
|  | `secondaryMuscles` | string[] \| null | optional; no element/length constraint | Secondary muscle names. |
|  | `translations` | `ExerciseTranslationRequest[]` | No `@NotNull`; min 1 if non-null; items validated. Must be sent with >=1 item operationally because service dereferences it. | Exercise name/instructions by language. |
| `ExerciseTranslationRequest` | `language` | string | required; nonblank; max 10 | Language code; no controlled enum. |
|  | `name` | string | required; nonblank; max 255 | Display name. |
|  | `instructions` | string \| null | optional; no size limit | Instructions. |
| `CreateRoutineRequest` | `name` | string | required; nonblank; max 150 | Routine name. |
|  | `description` | string \| null | optional; max 500 | Routine description. |
|  | `exercises` | `RoutineExerciseRequest[]` | No `@NotNull`; max 50; items validated. Must be sent (possibly `[]`) because service dereferences it. | Replaced wholesale on PUT. |
| `RoutineExerciseRequest` | `exerciseId` | UUID string | required | Available global/caller-owned exercise. |
|  | `position` | integer | >= 0 | Display/clone order. Must be unique on PUT and in DB. |
|  | `sets`, `targetReps` | integer | >= 1 | Planned prescription. |
|  | `restSeconds` | integer | >= 0 | Planned rest. |
|  | `notes` | string \| null | optional; max 500 | Copied into resulting workout exercise. |
| `CreateWorkoutRequest` | `routineId` | UUID string | required | Existing caller routine. |
| `UpdateWorkoutRequest` | `completed` | boolean \| null | optional | `true` stamps completion now; `false` clears timestamp. |
|  | `notes` | string \| null | optional; max 500 | Updated only when non-null. |
| `WorkoutSetRequest` | `setNumber` | integer | >= 1 | Unique per workout exercise. |
|  | `weight` | number | required; 0.00–9999.99 | Performed weight. |
|  | `reps` | integer | >= 1 | Performed reps. |
|  | `rpe` | number \| null | optional; 0.0–10.0 when provided | Perceived exertion. |

### Responses

| DTO | Fields | Notes |
| --- | --- | --- |
| `AuthResponse` | `accessToken: string` | Only login response. |
| `UserResponse` | `id: UUID`, `email: string`, `createdAt: LocalDateTime` | Password/hash and `updatedAt` omitted. |
| `ExerciseResponse` | `id`, nullable metadata strings, `secondaryMuscles: string[] \| null`, `translations`, `aliases` | Ownership/source are omitted. `aliases` can only originate from dataset/imported data. |
| `ExerciseTranslationResponse` | `language`, `name`, `instructions` | All strings except instructions effectively non-null in persisted rows. |
| `ExerciseAliasResponse` | `language`, `alias` | Read-only. |
| `ExercisePageResponse` | `content`, `page`, `size`, `totalElements`, `totalPages` | Custom compact pagination format. |
| `RoutineSummaryResponse` | `id`, `name`, `description`, `createdAt`, `updatedAt` | List item; excludes exercises. |
| `RoutineResponse` | summary fields + `exercises: RoutineExerciseResponse[]` | Detail/create/update shape. |
| `RoutineExerciseResponse` | `id`, `exerciseId`, `position`, `sets`, `targetReps`, `restSeconds`, `notes` | `id` is server-generated and can change after routine PUT. |
| `WorkoutResponse` | `id`, `routineId`, `startedAt`, `completedAt`, `notes`, `exercises`, `createdAt` | No `completed` boolean: derive it as `completedAt !== null`. |
| `WorkoutExerciseResponse` | `id`, `exerciseId`, `position`, `notes`, `sets` | Server-generated clone from routine. |
| `WorkoutSetResponse` | `id`, `setNumber`, `weight`, `reps`, `rpe` | Server-generated set ID. |
| `StatisticsSummaryResponse` | `from`, `to`, `workouts`, `sets`, `reps`, `volume`, `maxWeight` | Numeric values include decimal `BigDecimal`s. |
| `StatisticsComparisonResponse` | `current`, `previous`, `changes` | `changes` has `workouts`, `sets`, `reps`, `volume`, `maxWeight` as `double` percentages. |
| `StatisticsEvolutionResponse` | `from`, `to`, `data` | Data type is `StatisticsEvolutionPoint[]`. |
| `StatisticsEvolutionPoint` | `date`, `workouts`, `sets`, `reps`, `volume`, `maxWeight` | Daily aggregate. |
| `ExerciseStatisticsResponse` | `exerciseId`, `from`, `to`, `totalSets`, `totalReps`, `totalVolume`, `maxWeight`, `evolution` | Per UUID, not existence-checked. |
| `ExerciseStatisticsEvolutionPoint` | `date`, `volume`, `maxWeight` | Daily per-exercise aggregate. |

## 10. Enums

There are no API-visible Java enum fields. Category, equipment, muscles, and language are persisted/requested as free-text strings; the frontend must not generate restrictive enum types for them.

The only domain enum is internal-only `ExerciseSource`: `EXERCISES_DATASET`, `USER`. It is neither accepted nor returned by an HTTP DTO. Do not rely on it in TypeScript until the backend exposes it.

## 11. Validation Rules

Mirror the request constraints in client forms for UX, but submit knowing that the backend is authoritative. Section 9 contains every Bean Validation constraint. In addition:

- UUID path/body values must parse as UUIDs. Invalid path/query type conversions receive `400 INVALID_PARAMETER` for controller path/query mismatch; malformed JSON/body UUID may follow Spring's default message-conversion error path.
- `page` and `size` validation is explicit only for exercises. Routine/workout `Pageable` has no declared maximum size; do not depend on a global maximum.
- `@NotBlank` does not trim/store normalized values. Email uniqueness is exact database/string equality: there is no lower-casing, trimming, or case-insensitive uniqueness code.
- `translations` and routine `exercises` omit `@NotNull` yet their creation/update services call `.stream()` on them. Send non-null arrays; `translations` must be non-empty. Null can currently produce an unhandled server error rather than a validation response.
- Translation language is unique per exercise in PostgreSQL. The API does not validate duplicate languages in a create request before saving, so a database violation can become an unhandled server error. Routine position is DB-unique too; creation similarly lacks the service duplicate check.
- The DB also enforces routine fields and set values consistently with most annotations. Decimal database precision is `weight NUMERIC(6,2)` and `rpe NUMERIC(3,1)`; client values meeting annotated max/min but with excess decimal scale may be rounded/rejected by database/JPA behavior rather than a clean validation response.
- Unknown JSON properties are not explicitly configured in source; do not send them as contractual fields.

## 12. Error Handling Contract

Domain and Bean Validation errors use these application shapes; `timestamp` is a server `LocalDateTime` JSON value.

```json
// ErrorResponse: e.g. duplicate email / missing resource / invalid credentials
{
  "status": 409,
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "Email already registered: ana@example.com",
  "timestamp": "2026-09-10T14:30:00.123456"
}
```

```json
// ValidationErrorResponse: request-body validation
{
  "status": 400,
  "code": "VALIDATION_ERROR",
  "errors": { "password": "size must be between 8 and 100" },
  "timestamp": "2026-09-10T14:30:00.123456"
}
```

Known custom codes are `EMAIL_ALREADY_EXISTS` (409), `INVALID_CREDENTIALS` (401), `RESOURCE_NOT_FOUND` (404), `INVALID_PARAMETER` (400), `VALIDATION_ERROR` (400), `DUPLICATE_EXERCISE_POSITION` (400), `DUPLICATE_WORKOUT_SET_NUMBER` (400), and `INVALID_REQUEST` (400, such as invalid statistic range). A resource example message is `"Routine not found with id: <uuid>"`.

For `@RequestParam` constraint validation, the handler's `errors` is exactly `{ "parameters": "One or more parameters have invalid values" }`; body errors use field names. Security 401 and framework 403 are **not** returned by this advice: Security's configured 401 calls `sendError(401)` and 403 has no custom handler. Framework-level errors such as malformed JSON, missing required parameters, database constraint violations, and uncaught exceptions also lack an explicit common response contract; the frontend must branch primarily on status and tolerate Spring Boot's default error body or an empty body. Unhandled errors are normally `500`.

## 13. HTTP Status Codes

| Status | Current use |
| --- | --- |
| `200` | Successful reads, login, PUT/PATCH updates, statistics. |
| `201` | User, custom exercise, routine, workout, and workout-set creation. |
| `204` | Exercise/routine soft deletion. |
| `400` | Bean/query validation, invalid parameter, invalid credentials payload, invalid date range, duplicate routine position/set number. Some malformed request failures use framework defaults. |
| `401` | Invalid credentials; or unauthenticated protected request. Bodies differ as described above. |
| `403` | Possible Spring Security default access-denied response; no normal API ownership case is designed to return it. |
| `404` | Missing or inaccessible/foreign resources (ownership is deliberately hidden). |
| `409` | Duplicate user email. |
| `500` | Unhandled failures, including some DB constraint/race cases. |

## 14. Ownership and Authorization Rules

| Resource | Visibility / mutation rule |
| --- | --- |
| Users | Caller can only retrieve their JWT subject through `/me`. |
| Dataset exercises | Globally readable by every authenticated user, not editable or deletable through API. |
| Custom exercises | Readable/listed only for their owner; only owner can update/soft-delete. Not shared. |
| Routines | Only caller's active routines can list/read/update/delete. |
| Workouts | Only caller's workouts can list/read/update/add sets. |
| Workout exercises/sets | Reachable only under a caller-owned workout; no direct read/write routes otherwise. |
| Statistics | Query calculations always filter by caller's workout `user_id`. |

The user UUID comes exclusively from the verified JWT subject. Foreign, private, soft-deleted, or missing resource access is almost always reported as `404`, not `403`; hide edit/delete UI based on frontend-known ownership/custom status, but always handle a 404 race/stale state.

## 15. Date and Time Contract

The API exposes Java `LocalDate` as `YYYY-MM-DD` (for statistic ranges/points) and `LocalDateTime` as an ISO-8601 local date-time, for example `2026-09-10T14:30:00.123456`. It has no `Z` or offset. `createdAt`, `updatedAt`, `startedAt`, and `completedAt` are produced with `LocalDateTime.now()` on the API server; no Jackson timezone setting is present, so the source does not establish UTC behavior. Do not append `Z` and do not treat it as an offset-safe instant without agreeing a deployment timezone.

Server-generated fields: user/routine/exercise internal creation/update timestamps, workout `startedAt`/`createdAt`, and `completedAt` when `completed=true`. `completedAt` can be `null`; `description`, notes, optional exercise metadata, secondary muscles, instructions, and `rpe` can also be null.

## 16. Pagination, Filtering, Search and Sorting

| Resource | Pagination | Filtering/search | Sort |
| --- | --- | --- | --- |
| Exercises | Custom response, default `page=0`, `size=20`; size 1–100 | `search`, `category`, `equipment`, `muscleGroup`, `targetMuscle` as described in §5 | Always `createdAt DESC`, `id ASC`. |
| Routines | Standard serialized Spring `Page`, default size 10/page 0 | No filters/search | Controller binds pageable params but service always uses `createdAt DESC`, `id ASC`. |
| Workouts | Standard serialized Spring `Page`, default size 10/page 0 | No filters/search/status/date history filter | Same forced sort. |
| Statistics | None | Required date ranges; exercise UUID on exercise detail | Evolution is date ascending by query. |

A standard Spring `Page` serializes `content` plus metadata such as `totalElements`, `totalPages`, `size`, `number`, `numberOfElements`, `first`, `last`, `empty`, `sort`, and `pageable`; consume it as Spring's Page JSON rather than `ExercisePageResponse`. There are no list APIs for arbitrary categories/equipment/muscles, no workout status/filter, no date filter for workout history, and no configurable sorting actually honored by services.

## 17. Resource Relationships

```text
Authenticated User
├─ owns Routines ── contains RoutineExercise(exerciseId, planned prescription)
│                    └─ references available global or owned custom Exercise
├─ owns Workouts ── created from one active owned Routine
│                    └─ clones WorkoutExercise(exerciseId, position, notes)
│                         └─ contains performed WorkoutSets
└─ Statistics ── aggregates the user's Workouts and WorkoutSets

Exercise ── embeds translations[] and aliases[] in exercise responses
```

Routine and workout responses embed child items but never embed full `ExerciseResponse`: resolve the `exerciseId` via the exercise catalog/detail endpoint. Workouts retain copied exercise IDs even if the routine later changes. There is a database foreign key from workout to routine without delete cascade, so routine soft deletion avoids breaking its historical workouts.

## 18. TypeScript Interface Suggestions

```ts
export interface AuthResponse { accessToken: string; }
export interface CreateUserRequest { email: string; password: string; }
export interface LoginRequest { email: string; password: string; }
export interface User { id: string; email: string; createdAt: string; }

export interface ExerciseTranslation { language: string; name: string; instructions: string | null; }
export interface ExerciseAlias { language: string; alias: string; }
export interface Exercise {
  id: string; category: string | null; equipment: string | null;
  targetMuscle: string | null; muscleGroup: string | null;
  secondaryMuscles: string[] | null; translations: ExerciseTranslation[]; aliases: ExerciseAlias[];
}
export interface CreateExerciseRequest {
  category?: string | null; equipment?: string | null; targetMuscle?: string | null;
  muscleGroup?: string | null; secondaryMuscles?: string[] | null;
  translations: Array<{ language: string; name: string; instructions?: string | null }>;
}
export interface ExercisePage { content: Exercise[]; page: number; size: number; totalElements: number; totalPages: number; }

export interface RoutineExerciseRequest { exerciseId: string; position: number; sets: number; targetReps: number; restSeconds: number; notes?: string | null; }
export interface RoutineExercise extends RoutineExerciseRequest { id: string; }
export interface CreateRoutineRequest { name: string; description?: string | null; exercises: RoutineExerciseRequest[]; }
export interface RoutineSummary { id: string; name: string; description: string | null; createdAt: string; updatedAt: string; }
export interface Routine extends RoutineSummary { exercises: RoutineExercise[]; }

export interface WorkoutSet { id: string; setNumber: number; weight: number; reps: number; rpe: number | null; }
export interface WorkoutExercise { id: string; exerciseId: string; position: number; notes: string | null; sets: WorkoutSet[]; }
export interface Workout { id: string; routineId: string | null; startedAt: string; completedAt: string | null; notes: string | null; exercises: WorkoutExercise[]; createdAt: string; }
export interface WorkoutSetRequest { setNumber: number; weight: number; reps: number; rpe?: number | null; }
export interface UpdateWorkoutRequest { completed?: boolean | null; notes?: string | null; }

export interface StatisticsSummary { from: string; to: string; workouts: number; sets: number; reps: number; volume: number; maxWeight: number; }
export interface StatisticsEvolutionPoint extends Omit<StatisticsSummary, 'from' | 'to'> { date: string; }
export interface ExerciseStatistics { exerciseId: string; from: string; to: string; totalSets: number; totalReps: number; totalVolume: number; maxWeight: number; evolution: Array<{date: string; volume: number; maxWeight: number}>; }
export interface SpringPage<T> { content: T[]; totalElements: number; totalPages: number; size: number; number: number; numberOfElements: number; first: boolean; last: boolean; empty: boolean; }
```

Use `string` for UUIDs and date/time wire values. Java `BigDecimal` JSON numbers are shown as `number`; if exact decimal arithmetic is required in the UI, retain/display their source strings carefully, but requests are accepted as JSON numbers.

## 19. Recommended Angular API Service Boundaries

- `AuthApiService`: login only; token lifecycle belongs with the auth state layer.
- `UserApiService`: registration and `/me`.
- `ExerciseApiService`: catalog query/detail and custom exercise CRUD.
- `RoutineApiService`: routine list/detail and full replacement write/delete.
- `WorkoutApiService`: routine-based creation, history/detail, completion/notes patch, and set creation.
- `StatisticsApiService`: range-based summary, comparison, evolution, and per-exercise metrics.

Keep mapping, retry, and migration orchestration in a separate migration/sync coordinator rather than embedding it in each service.

## 20. Frontend Authentication Architecture Recommendations

Use an auth state store holding the token and `UserResponse`; after login, immediately call `/users/me`. Add an HTTP interceptor that attaches `Authorization: Bearer <token>` only to API-origin requests. Guards should require a locally valid authenticated state, while `401` in the interceptor should clear state, remove the persisted token, preserve unsynced local migration data, and route to login.

There is no refresh path, so expire/re-login after one hour (or on 401). In-memory token storage best limits XSS persistence; it loses login on reload. `localStorage` survives reload and is practical for this API but exposes a bearer token to XSS. Use it only with a strong CSP/XSS posture and clear it on logout; this backend does not use HttpOnly cookies. Never store passwords.

## 21. Offline-to-Server Migration Considerations

Existing endpoints can upload a custom exercise (`POST /exercises`) and a routine (`POST /routines`), then create a workout only from a server routine and add its set records one by one. This supports a constrained migration, not a complete lossless offline import.

| Concern | Confirmed effect |
| --- | --- |
| ID mapping | All server resources use newly generated UUIDs. Store local exercise ID → server UUID; then use server UUID in routines. Routine POST returns exercise-row IDs, then workout POST returns cloned workout-exercise IDs needed to create sets. |
| Dataset IDs | Neither `source` nor `sourceId` is exposed. Local data that refers to a known dataset item can only be matched by a prior locally-maintained mapping/name heuristic; there is no reliable server dataset identity API. |
| Workouts/history | Cannot upload an arbitrary historical workout: POST requires an extant server routine and server stamps `startedAt`/`createdAt`; only completion is mutable and it stamps now. History dates, duration, arbitrary ad-hoc exercises, and imported completion times cannot be preserved. |
| Planned vs performed | Workout creation copies routine exercises/notes, but creates no sets; add every set individually. The current API cannot change cloned workout exercise details nor alter/remove a failed/mistyped set. |
| Duplicates/retry | No idempotency key, client IDs, bulk endpoints, or lookup-by-client-ID. Retried POSTs can duplicate custom exercises, routines, workouts, and sets (set retry returns duplicate only when the first set definitely persisted). |
| Partial failure | Routine/custom-exercise creation is transactional internally, but cross-request migration is not. Retain a durable local per-item state/mapping and resume carefully. Do not mark the whole migration done until all persisted IDs/reads are verified. |

Confirmed backend gaps for a faithful migration: no batch/import endpoint; no externally visible dataset source ID; no historical/custom workout creation or start/completion timestamps; no workout/set editing/deletion; no custom `WorkoutExercise` endpoint; and no idempotency/client-reference facility.

## 22. Suggested Initial Frontend Synchronization Flow

1. Preserve the local store untouched; register if needed, then log in and retrieve `/api/users/me`.
2. Snapshot locally unsynchronized data and create a durable migration ledger keyed by local IDs/statuses. Do not infer success merely from a network response that was lost.
3. Fetch the available server exercise catalog. Match only IDs that the old frontend already knows correspond to server UUIDs; otherwise require an explicit user choice or create a custom exercise. Create custom exercises first and record returned UUIDs.
4. Create routines, substituting each local exercise ID with a recorded server UUID. Validate positions uniquely client-side. Record each returned routine UUID.
5. For future/current sessions, create a server workout from its mapped routine, then use returned `workout.exercises[].id` to POST each set sequentially. Mark complete through PATCH after all desired sets succeed.
6. Read back the routine/workout and verify expected IDs/set counts. Mark only verified source records synchronized; retry failed, unconfirmed records with the ledger.
7. Switch authenticated state to server reads; keep a local cache/offline queue separately until backend supports safe reconciliation.

Do not claim successful historical migration: present the date/history limitation to product/users or defer historical workout upload until the backend adds an import-capable contract.

## 23. Backend Capabilities Not Yet Exposed in Frontend

Confirmed features worth surfacing: account registration/login/current profile; a paginated multilingual global-plus-private exercise catalog with alias-aware search; user custom exercises; planned routines; start/complete workout sessions and per-set logging; paginated server workout history; and summary/comparison/evolution/per-exercise statistics.

## 24. Frontend Features That May Not Yet Be Supported by Backend

**Confirmed gaps:** account edits; public/private exercise indicator in payload; alias management; direct or historical workout creation; workout deletion; editing/deleting sets; adding/editing/deleting workout exercises; duration; client-selected workout timestamps; exercise source IDs; bulk import; idempotency; and a refresh-token/logout endpoint.

**Requires Angular repository cross-check:** any locally stored fields beyond routines, planned prescription, workout notes, exercise metadata/translations, and set number/weight/reps/RPE may lack a server equivalent. In particular, the backend has no API fields for media, workout duration, or a generic user profile beyond email/created date.

## 25. Recommended Integration Order

1. Configure API origin/proxy and resolve CORS for the deployed frontend.
2. Implement auth state, bearer interceptor, login/register, `/me`, logout, and 401 handling.
3. Mirror DTOs/error parsing/pagination and validate against Swagger/local API.
4. Integrate read-only exercise catalog and exercise-ID resolution, then custom-exercise CRUD.
5. Integrate routine list/detail/create/replace/delete.
6. Integrate server workouts for newly performed sessions: create from routine, add sets, patch completion/notes, history/detail.
7. Integrate statistics after actual server workout data exists.
8. Build the cautious local-data migration ledger/flow; define product handling for unsupported historic data before enabling it.
9. Remove obsolete local persistence only after server read-back and migration verification.

## 26. End-to-End Test Scenarios for Frontend Integration

- Register, log in separately, retrieve `/me`, reload, and verify bearer-token restoration policy.
- Reject invalid registration/login and render both application validation errors and `INVALID_CREDENTIALS`.
- Send an absent, tampered, and expired token; verify 401 clears auth state.
- Page/search/filter the catalog, retrieve an item, create/update/delete a custom exercise, and confirm another account receives 404 for it.
- Confirm global exercise edit/delete receives 404; exercise list must not expose another user's custom item.
- Create a routine with global/custom IDs; retrieve it, replace its exercise list, delete it, and verify it disappears from list.
- Start a workout from that routine, add valid sets, reject a duplicate set number, complete it, and reload workout history/detail.
- Verify statistics totals/evolution update after persisted sets and do not include another account's data.
- Test empty statistic ranges and invalid `to < from` date range.
- Run the migration flow with intentional offline/network failure between objects; retry from ledger without marking unverified data done.

## 27. Known Backend Limitations / Integration Risks

| Priority | Risk |
| --- | --- |
| Blocker | No CORS policy is implemented. A separately hosted Angular origin cannot be assumed to call the API directly. |
| Blocker for lossless legacy history | Workouts can only start now from a live routine; historical timestamps/ad-hoc workout import is impossible. |
| Important | No refresh token; all protected API use stops on expiry and must re-authenticate. |
| Important | Migration writes are non-idempotent and non-bulk; dropped responses and retries can duplicate data. |
| Important | API hides exercise source/ownership, preventing a reliable global/custom UI label and dataset ID migration. |
| Important | Workout sets/exercises cannot be corrected or removed after creation; no workout deletion. |
| Important | Error bodies are inconsistent for security/framework/database failures; some invalid duplicate input can surface as 500. |
| Minor | Routine/workout lists can generate per-workout child queries and embed full sets; page size should be conservative. |
| Minor | Routine/workout `sort` query input is not honored; filters/history date filtering are absent. |
| Minor | LocalDateTime has no offset/timezone contract. |

## 28. Swagger / OpenAPI Cross-Check

The configured OpenAPI title/version and `bearerAuth` HTTP bearer scheme match `OpenApiConfig`; controllers are tagged and their actual 22 routes are annotated. Security requirements are declared for exercises, routines, workouts, statistics, and `/users/me`; public register/login align with `SecurityConfig`.

Discrepancies/limits found by source comparison:

- `GET /api/statistics/exercises/{exerciseId}` declares a `404` response in OpenAPI annotations, but its service never reads/existence-checks the exercise. It returns a zero aggregate for any UUID with no matching caller data.
- Many successful OpenAPI response annotations declare only a description, not an explicit response schema. Springdoc can infer controller return types, but this is less explicit than the DTO contract above.
- The annotations describe selected `400`/`404` outcomes but do not represent all actual framework/default error variations, DB-constraint `500` cases, or the security entry point's non-`ErrorResponse` 401 body.
- The controller describes routine/workout sorting broadly as pagination, but service code overrides caller sorting.

No Swagger files were modified.

## 29. Final Frontend Integration Checklist

- [ ] Backend base URL/proxy and CORS behavior confirmed for each environment.
- [ ] Register-then-login flow and one-hour JWT handling implemented.
- [ ] Bearer interceptor, 401 logout, and `/users/me` bootstrap implemented.
- [ ] Exact DTO interfaces and nullable fields mirrored.
- [ ] No artificial enums created for free-text exercise fields/languages.
- [ ] Custom versus global exercise UI limitation acknowledged.
- [ ] Both custom and Spring `Page` response forms handled.
- [ ] Routine replacement and workout clone semantics understood.
- [ ] Statistics dates and inclusive ranges handled as `YYYY-MM-DD`.
- [ ] Application and non-application error bodies handled defensively.
- [ ] Local-to-server ID migration ledger and unsupported-history policy defined.
- [ ] Backend gaps and Swagger discrepancy reviewed before implementation.
