# Contrato de integración: cliente Android nativo

> Fuente de verdad: controladores, DTOs, servicios, configuración de seguridad, migraciones y pruebas de integración de este repositorio, revisados el 24-09-2026. OpenAPI (`/v3/api-docs`) es útil para descubrir rutas y esquemas, pero no expresa todos los comportamientos de servicio que se detallan aquí. Si difiere de este documento, prevalece la implementación.

## 1. Vista general de la API

| Entorno | Base URL |
| --- | --- |
| Producción | `https://gym-tracker-api-s70k.onrender.com` |
| Local | `http://localhost:8080` |

El prefijo de negocio es `/api`. Swagger UI está en `/swagger-ui/index.html` y OpenAPI JSON en `/v3/api-docs`. Las solicitudes con cuerpo y las respuestas son JSON (`application/json`); la autenticación de refresh usa también el encabezado HTTP `Set-Cookie`/`Cookie`.

`GET /api/exercises` tiene página propia: `{ content, page, size, totalElements, totalPages }`, con `page` base 0 y `size` 1--100 (por defecto 20). Las listas de rutinas y entrenamientos devuelven la serialización estándar de Spring `Page`: incluye `content`, `totalElements`, `totalPages`, `size`, `number`, `sort`, `pageable`, `first`, `last`, `numberOfElements` y `empty`; sus valores por defecto son página 0/tamaño 10. Aunque aceptan `sort`, el servicio siempre ordena `createdAt DESC, id ASC`.

Los errores de aplicación suelen ser:

```json
{ "status": 400, "code": "INVALID_REQUEST", "message": "...", "timestamp": "2026-09-24T12:34:56.789" }
```

Los de validación son `{ status, code: "VALIDATION_ERROR", errors: { campo: mensaje }, timestamp }`. Los `401` generados directamente por Spring Security (bearer ausente, malformado o caducado) usan `sendError`: el cuerpo no tiene forma garantizada.

## 2. Autenticación

### Sesión nativa Android

Registro: `POST /api/users` con `{ "email": "ana@example.com", "password": "contraseña-de-ejemplo" }` devuelve `201 UserResponse` y no inicia sesión. Las tres rutas nativas son públicas y requieren HTTPS en producción. Usan JSON, no leen ni emiten cookies; cada respuesta de sesión tiene `Cache-Control: no-store`.

**Login** `POST /api/auth/mobile/login`:

```json
{ "email": "ana@example.com", "password": "contraseña-de-ejemplo" }
```

`200`:

```json
{
  "accessToken": "FAKE_ACCESS_JWT",
  "refreshToken": "FAKE_OPAQUE_REFRESH_TOKEN",
  "refreshExpiresAt": "2026-10-24T12:00:00Z"
}
```

**Refresh** `POST /api/auth/mobile/refresh`:

```json
{ "refreshToken": "FAKE_OPAQUE_REFRESH_TOKEN" }
```

`200` devuelve el mismo esquema que login, con **un refresh token nuevo** y el mismo `refreshExpiresAt` absoluto. Reemplazar de forma atómica el credential almacenado después de recibir la respuesta. No enviar refresh en URL, query ni bearer. El `accessToken` contiene `sub` (UUID), `iat` y `exp`; expira por defecto tras 1 hora. No hay un campo JSON independiente para su expiración. Para recursos protegidos usar `Authorization: Bearer <accessToken>`; ante `401` de un recurso, hacer un único refresh coordinado y reintentar una vez.

**Logout** `POST /api/auth/mobile/logout` con `{ "refreshToken": "FAKE_OPAQUE_REFRESH_TOKEN" }` devuelve `204` sin cuerpo. Revoca todos los refresh tokens activos de **esa familia/sesión**, incluso si se presenta un antecesor rotado; repetirlo es seguro. No revoca otras familias del mismo usuario ni el JWT ya emitido: descartar access y refresh locales al cerrar sesión. Si una petición de logout tiene fallo de red/5xx, reintentar o conservar la revocación pendiente.

Cada login, web o móvil, crea una familia independiente: teléfono, navegador y otro dispositivo pueden coexistir. Sólo SHA-256 del token opaco aleatorio de 256 bits se guarda en PostgreSQL; el valor bruto sólo se devuelve al emitir/rotar y nunca debe aparecer en logs. El refresh vence 30 días después del login por defecto y la rotación no prolonga ese límite. La reutilización de un token rotado dentro de 10 segundos puede generar otro sucesor de la misma familia para tolerar concurrencia. Fuera de esa ventana, se revoca toda la familia y se responde `401 INVALID_REFRESH_TOKEN`. Android debe serializar/coalescer refreshes; si llegan dos sucesores, conservar el último confirmado y evitar refrescos paralelos. Un fallo de red tras enviar refresh deja resultado incierto: recuperar dentro de la gracia si es posible; fuera de ella puede requerir login.

El backend no controla el almacenamiento del dispositivo. Android debe guardar el refresh credential mediante almacenamiento seguro respaldado por mecanismos de seguridad de Android/Keystore según corresponda. Nunca en SharedPreferences en texto plano. Mantener access token preferiblemente en memoria y no registrar ni incluir credenciales en telemetría.

| Resultado | Interpretación Android |
| --- | --- |
| `400 VALIDATION_ERROR` | JSON ausente/campo vacío o malformado; corregir solicitud, sin borrar sesión automáticamente. |
| `401 INVALID_CREDENTIALS` en login | Credenciales definitivas incorrectas. |
| `401 INVALID_REFRESH_TOKEN` en refresh | Sesión de refresh definitivamente inválida, vencida, revocada o reutilizada: borrar credenciales locales y pedir login. El mensaje no revela el token. |
| `204` en logout | Revocación aceptada o token ya ausente. |
| Red/timeout, `500`, `502`, `503`, `504` | Estado incierto o transitorio: conservar sesión local, reintentar con espera; **nunca** interpretarlo como logout. |
| `401` en recurso bearer | Probar refresh una vez; el cuerpo de este `401` de Spring Security no tiene esquema JSON garantizado. |

### Compatibilidad navegador y seguridad de transporte

El flujo Angular sigue igual: `POST /api/auth/login` recibe sólo `{ "accessToken": "FAKE_ACCESS_JWT" }` y cookie HttpOnly; `/api/auth/refresh` no acepta cuerpo y rota esa cookie; `/api/auth/logout` la revoca y borra. La cookie host-only `refreshToken` por defecto usa `Path=/api/auth`, `HttpOnly`, y en producción `Secure; SameSite=None` (local: `SameSite=Lax`). Su expiración absoluta tampoco se extiende. El refresh web no se expone en JSON ni a JavaScript.

CSRF está desactivado en la configuración actual. Las rutas nativas sólo aceptan el secreto explícito en JSON: el navegador no les entrega automáticamente la cookie web. El refresh web sí usa cookie automática; conserva la política CORS con orígenes exactos, credenciales y rechazo de orígenes no permitidos. No se ha ampliado CORS para Android, cuyos clientes HTTP nativos no están sujetos a CORS. Una protección CSRF dedicada para navegadores sigue siendo un endurecimiento futuro. Tampoco hay rate limiting en login/refresh: añadir límites en el borde o en backend es endurecimiento pendiente, sin introducir una dependencia nueva aquí.

## 3. Ejercicios

Todos requieren bearer JWT. Un usuario ve ejercicios globales no borrados y sus propios ejercicios; no ve los de otros usuarios. El `id` es UUID de servidor y es la referencia que se envía en rutinas y snapshots de entrenamiento. Para catálogo importado, identidad de migración estable es `(source, sourceId)`, con `source: EXERCISES_DATASET`; para personalizados `source: USER`, `sourceId: null` y el cliente debe conservar tanto `clientId` como el `id` devuelto. No usar nombre, traducción o metadatos como clave.

```ts
ExerciseResponse = {
  id: UUID, clientId: UUID | null,
  source: 'EXERCISES_DATASET' | 'USER', sourceId: string | null,
  editable: boolean, deletable: boolean,
  category: string | null, equipment: string | null,
  targetMuscle: string | null, muscleGroup: string | null,
  secondaryMuscles: string[] | null,
  translations: { language: string, name: string, instructions: string | null }[],
  aliases: { language: string, alias: string }[]
}
CreateExerciseRequest = {
  clientId?: UUID, category?: string, equipment?: string,
  targetMuscle?: string, muscleGroup?: string, secondaryMuscles?: string[],
  translations: { language: string, name: string, instructions?: string }[]
}
```

Los cuatro metadatos tienen máximo 100 caracteres. `translations` es obligatorio/no vacío; `language` no vacío/máx. 10 y `name` no vacío/máx. 255. `instructions` y elementos de `secondaryMuscles` no tienen límites de validación declarados. No hay endpoint para crear/editar aliases.

| Método/ruta | Resultado y condiciones |
| --- | --- |
| `GET /api/exercises?page=&size=&search=&category=&equipment=&muscleGroup=&targetMuscle=` | Página propia; filtros opcionales, valores blancos se ignoran; orden `createdAt DESC, id ASC`. |
| `GET /api/exercises/filter-options` | `{ categories, equipment, muscleGroups, targetMuscles }`: valores visibles, distintos, no blancos, orden alfabético; no incluye `secondaryMuscles`. |
| `GET /api/exercises/{id}` | `200 ExerciseResponse`; `404 RESOURCE_NOT_FOUND` si no existe, está borrado o no es visible. |
| `POST /api/exercises` | `201 ExerciseResponse`; sólo crea personalizado. Con el mismo `clientId` del mismo usuario devuelve el existente (también `201`). |
| `PUT /api/exercises/{id}` | `200`; sólo personalizado propio no borrado. Reemplaza metadatos y actualiza/crea traducciones por `language`, pero **no elimina** traducciones existentes omitidas. Ignora `clientId` de actualización: no lo cambia. |
| `DELETE /api/exercises/{id}` | `204`; soft delete sólo personalizado propio. Globales y ajenos devuelven `404`. |

`editable` y `deletable` son `true` exactamente para los personalizados del usuario actual; Android debe respetarlos.

## 4. Rutinas

Todas requieren bearer y sólo operan sobre rutinas propias no borradas. `DELETE` es soft delete. `CreateRoutineRequest` sirve tanto para crear como para reemplazo completo:

```ts
CreateRoutineRequest = {
  clientId?: UUID, name: string, description?: string,
  exercises: { exerciseId: UUID, position: number, sets: number,
    targetReps: number, restSeconds: number, notes?: string }[]
}
RoutineResponse = {
  id: UUID, clientId: UUID | null, name: string, description: string | null,
  exercises: { id: UUID, exerciseId: UUID, position: number, sets: number,
    targetReps: number, restSeconds: number, notes: string | null }[],
  createdAt: LocalDateTime, updatedAt: LocalDateTime
}
RoutineSummaryResponse = Omit<RoutineResponse, 'exercises'>
```

`name` es obligatorio/no blanco/máx. 150; `description` máx. 500. `exercises` es obligatorio, puede estar vacío y admite hasta 50. Cada `exerciseId` debe ser global visible o propio no borrado; de lo contrario `404`. `position` es 0-based (`>=0`), único por rutina; repetido devuelve `400 DUPLICATE_EXERCISE_POSITION`. `sets` y `targetReps` son `>=1`, `restSeconds >=0`, `notes` máx. 500. El orden de respuesta es siempre `position ASC`.

| Método/ruta | Resultado |
| --- | --- |
| `GET /api/routines?page=&size=&sort=` | `200 Page<RoutineSummaryResponse>`; orden efectivo `createdAt DESC, id ASC`. |
| `GET /api/routines/{id}` | `200 RoutineResponse`, o `404` si ajena, borrada o ausente. |
| `POST /api/routines` | `201`; `clientId` estable por usuario hace idempotente la creación y en retry retorna la existente con `201`. |
| `PUT /api/routines/{id}` | `200`; reemplazo total. Borra y recrea todas las filas hijo, por lo que los `RoutineExerciseResponse.id` cambian. No cambia el `clientId` de la rutina. |
| `DELETE /api/routines/{id}` | `204`; después es invisible y otro delete devuelve `404`. |

## 5. Entrenamientos

Todos requieren bearer. El estado activo se deriva de `completedAt === null`; no hay booleano de estado ni filtro/listado de activos. Crear un entrenamiento exige una rutina propia no borrada y genera un snapshot de todos sus ejercicios actuales: copia `exerciseId`, `position` y `notes`; **no** copia `sets`, `targetReps` ni `restSeconds`. No se puede añadir un ejercicio directamente a un entrenamiento.

```ts
CreateWorkoutRequest = {
  clientId?: UUID, routineId: UUID, startedAt?: LocalDateTime,
  completedAt?: LocalDateTime, notes?: string
}
WorkoutResponse = {
  id: UUID, clientId: UUID | null, routineId: UUID | null,
  startedAt: LocalDateTime, completedAt: LocalDateTime | null,
  startedAtInstant: Instant | null, completedAtInstant: Instant | null,
  calendarZone: string | null,
  notes: string | null, createdAt: LocalDateTime,
  exercises: { id: UUID, exerciseId: UUID, position: number, notes: string | null,
    sets: { id: UUID, clientId: UUID | null, setNumber: number,
      weight: decimal, reps: number, rpe: decimal | null }[] }[]
}
WorkoutSetRequest = { clientId?: UUID, setNumber: number, weight: decimal, reps: number, rpe?: decimal }
UpdateWorkoutRequest = { completed?: boolean, notes?: string }
```

`startedAt` es valor cliente o `LocalDateTime.now()`; para importación histórica puede enviarse junto con `completedAt`, siempre que ésta no sea anterior al `startedAt`. `notes` máx. 500. En POST, `clientId` por usuario es idempotente y repite el entrenamiento ya creado con `201`.

| Método/ruta | Resultado |
| --- | --- |
| `POST /api/workouts` | `201 WorkoutResponse`; inicia o importa una sesión histórica basada en snapshot de rutina. |
| `POST /api/workouts/mobile` | `201 WorkoutResponse`; crea/importa con instantes explícitos y zona de calendario capturada. |
| `GET /api/workouts?page=&size=&sort=` | `200 Page<WorkoutResponse>`; todos los entrenamientos propios, sin filtros fecha/estado; orden efectivo `createdAt DESC, id ASC`. Incluye ejercicios y sets. |
| `GET /api/workouts/{id}` | `200 WorkoutResponse` propio; ejercicios `position ASC`, sets `setNumber ASC`; si no, `404`. |
| `POST /api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets` | `201 WorkoutSetResponse`. El ejercicio hijo debe pertenecer al entrenamiento propio. `setNumber` es 1-based, único por ejercicio; duplicado sin mismo `clientId`: `400 DUPLICATE_WORKOUT_SET_NUMBER`. `weight` 0.00--9999.99, `reps >=1`, `rpe` opcional 0.0--10.0. `clientId` estable por workout-exercise devuelve el set existente con `201`. |
| `PATCH /api/workouts/{id}` | `200`. `completed:true` asigna hora actual del servidor; `completed:false` borra `completedAt`; omitido/null no cambia. `notes` sólo se reemplaza si no es null. |

No existen `DELETE /api/workouts`, update/delete de workout exercise, ni update/delete de set. Tampoco se pueden añadir exercises al snapshot, cambiar `startedAt`, `routineId` o `completedAt` arbitrariamente después de crear, ni borrar notas con PATCH (`notes:null` se ignora). La única forma actual de cambiar finalización es el booleano `completed` y siempre usa el reloj del servidor.

## 6. Estadísticas

Todas requieren bearer. Los parámetros son `LocalDate` obligatorios `YYYY-MM-DD`; `to < from` devuelve `400 INVALID_REQUEST`. No se filtran entrenamientos incompletos: el resumen cuenta todos los entrenamientos del usuario cuyo `startedAt` cae en el rango.

| Ruta | Parámetros | `200` |
| --- | --- | --- |
| `GET /api/statistics/summary` | `from`, `to` | `{ from, to, workouts: long, sets: long, reps: long, volume: decimal, maxWeight: decimal }`; vacía = ceros. |
| `GET /api/statistics/comparison` | `currentFrom`, `currentTo`, `previousFrom`, `previousTo` | `{ current: StatisticsSummaryResponse, previous: StatisticsSummaryResponse, changes: { workouts, sets, reps, volume, maxWeight } }`; changes es porcentaje `double`; previo 0 -> 0.0 si actual 0, 100.0 si no. |
| `GET /api/statistics/evolution` | `from`, `to` | `{ from, to, data: [{ date, workouts, sets, reps, volume, maxWeight }] }`; fechas ascendente, sin días de ceros. |
| `GET /api/statistics/exercises/{exerciseId}` | UUID `exerciseId`, `from`, `to` | `{ exerciseId, from, to, totalSets, totalReps, totalVolume, maxWeight, evolution: [{ date, volume, maxWeight }] }`; devuelve 200 y ceros/array vacío incluso si el exerciseId no existe o no es visible. |

## 7. Fechas, hora y zona horaria

La API distingue un **instante real** de una **fecha de calendario**. Android guarda los momentos como milisegundos Unix: convertir con `Instant.ofEpochMilli(ms)` y serializar ISO-8601 con `Z`, por ejemplo `2026-10-25T00:30:00Z`. El backend acepta instantes ISO-8601 con `Z` u offset explícito y devuelve siempre UTC `Z` en los nuevos campos `*Instant`. No enviar epoch millis como número JSON ni convertirlos a `LocalDateTime` sin zona.

Para sincronizar un entrenamiento nuevo, Android usa `POST /api/workouts/mobile` con bearer:

```json
{
  "clientId": "00000000-0000-4000-8000-000000000001",
  "routineId": "00000000-0000-4000-8000-000000000002",
  "startedAt": "2026-10-25T00:30:00Z",
  "completedAt": "2026-10-25T01:30:00Z",
  "calendarZone": "Europe/Madrid",
  "notes": "Ejemplo"
}
```

`clientId`, `completedAt` y `notes` son opcionales. `routineId`, `startedAt` y `calendarZone` (ID IANA válido) son obligatorios. `completedAt` no puede ser anterior a `startedAt` como instante. El `clientId` conserva la idempotencia existente. La respuesta `201 WorkoutResponse` mantiene los campos locales web y añade `startedAtInstant`, `completedAtInstant` y `calendarZone`:

```json
{
  "startedAt": "2026-10-25T02:30:00",
  "completedAt": "2026-10-25T02:30:00",
  "startedAtInstant": "2026-10-25T00:30:00Z",
  "completedAtInstant": "2026-10-25T01:30:00Z",
  "calendarZone": "Europe/Madrid"
}
```

El ejemplo abrevia el resto de `WorkoutResponse`. En lectura/listado, Android **usa sólo `*Instant`** para momentos reales. `startedAt`/`completedAt` siguen siendo fechas-hora locales sin offset para Angular y para el calendario/estadísticas. La zona se captura al crear el entrenamiento: viajar después no mueve el día histórico. Para una sesión que cruza zonas, la zona de calendario es la seleccionada al inicio. El servidor convierte cada instante con esa zona al valor local de las columnas existentes; `DATE(started_at)` determina el día estadístico. Un cambio de horario de verano no altera el instante: dos instantes pueden tener la misma hora local durante el solapamiento; la comparación de finalización se hace por instantes. En el salto hacia adelante no se inventa una hora local inexistente.

Los filtros de estadísticas siguen siendo `LocalDate` `YYYY-MM-DD`, inclusivos; internamente `[from 00:00, (to + 1 día) 00:00)` sobre la fecha local capturada. Semana: lunes a domingo según fechas de calendario de cada workout. Mes: primer a último día de calendario. No se usan intervalos fijos de 24 horas para definir días, semanas o meses; esto evita errores DST. Si se agregan workouts de varias zonas, cada uno cuenta por su día de origen, no por la zona actual del teléfono.

La migración V14 añade columnas `TIMESTAMP WITH TIME ZONE` para ambos instantes y `calendar_zone`, todas nullable. **No cambia ni reinterpreta filas antiguas**: sus nuevos campos son `null`, su hora local y sus estadísticas históricas permanecen intactas. Sin la zona original no existe una conversión fiable de un `LocalDateTime` histórico a epoch millis; Android debe tratar esos campos nulos como datos heredados de hora local y no fabricar un instante. Si necesita convertirlos para sincronización bidireccional, debe obtener una zona confirmada por el usuario o una migración explícita posterior. Los `createdAt` de usuario/rutina/workout siguen siendo audit `LocalDateTime` heredados, no instantes de actividad.

La ruta web `POST /api/workouts` y sus campos `startedAt`/`completedAt` conservan exactamente su semántica local anterior. `PATCH /api/workouts/{id}` para un entrenamiento móvil usa el instante del servidor y la zona capturada al completar; para uno heredado conserva el comportamiento local anterior.

## 8. Idempotencia

| Recurso / operación | clientId estable | Ámbito | Reintento esperado |
| --- | --- | --- | --- |
| `POST /api/exercises` | Sí | usuario | Retorna el exercise existente; HTTP sigue siendo 201. |
| `POST /api/routines` | Sí | usuario | Retorna la rutina existente; HTTP sigue siendo 201. |
| `POST /api/workouts` | Sí | usuario | Retorna el workout existente; HTTP sigue siendo 201. |
| `POST .../sets` | Sí | workout exercise | Retorna el set existente; HTTP sigue siendo 201. |
| Registro, login, refresh, logout | No | -- | No son operaciones de cola idempotentes. |
| PUT/PATCH/DELETE | No explícito | -- | No hay ETag/versionado ni clave de idempotencia para mutaciones. |

Generar UUID v4 una sola vez antes de poner una creación en cola; persistirlo junto con el payload y reutilizarlo en cada retry. `clientId` no equivale a clave global: las restricciones de base de datos lo delimitan al usuario, salvo sets que se delimitan al workout exercise. Un `201` puede significar creación original o replay correcto; comparar el `clientId` devuelto.

## 9. Auditoría offline-first

| Clase | Operaciones | Motivo / regla |
| --- | --- | --- |
| A. Seguras para retry en cola | POST exercise/routine/workout/set con `clientId` persistido | El servidor deduplica por la clave indicada. Resolver antes los IDs de dependencias. |
| B. Seguras con condiciones | GETs; PUT rutina; PUT ejercicio; PATCH workout; DELETE exercise/routine; logout | Reintentar GET sólo ante red/5xx. Las mutaciones no tienen control de concurrencia/versionado: serializar por recurso, volver a leer tras fallo incierto y no reintentar DELETE tras un `404` como si fuera error. PUT rutina recrea hijos. PATCH no puede expresar limpiar notas ni timestamp final histórico. Logout móvil puede repetirse con su refresh credential. |
| C. Inseguras / capacidad backend necesaria | Actualizar/borrar sets; actualizar/borrar/agregar workout exercises; borrar workouts; importar un workout con exercises que no proceden de una rutina sincronizada; sincronización de conflictos | No hay endpoint o contrato de idempotencia/versiones para estas necesidades. La sesión nativa usa los endpoints móviles de autenticación descritos arriba. |

## 10. Grafo de dependencias de sincronización

```text
Exercise global (source/sourceId) o Exercise personalizado (clientId -> id)
        ↓ exerciseId
Routine (clientId -> id)
        ↓ snapshot de sus RoutineExercise, al POST /workouts
Workout (clientId -> id) ──→ WorkoutExercise IDs de servidor
        ↓ workoutId + workoutExerciseId
WorkoutSet (clientId -> id)
```

Un workout depende de una rutina propia no borrada, incluso al crear histórico. Sus workout exercises no son los IDs de routine exercises: son filas snapshot nuevas y sólo se conocen al recibir `WorkoutResponse`. Por ello no se puede encolar un set usando una clave local de routine exercise; hay que esperar al `workoutExerciseId` del snapshot y mapearlo por `exerciseId` + `position` (la combinación es única dentro del snapshot).

## 11. Semántica HTTP y manejo Android

| Señal | Interpretación/acción |
| --- | --- |
| `400 VALIDATION_ERROR` | Mostrar `errors` por campo; no reintentar sin corregir. |
| `400 INVALID_PARAMETER`, `INVALID_REQUEST`, `DUPLICATE_EXERCISE_POSITION`, `DUPLICATE_WORKOUT_SET_NUMBER` | Error de input/estado; no reintento automático. |
| `401` en recurso bearer | Access inválido/caducado; intentar una sola restauración de sesión mediante el flujo móvil; no repetir la petición infinitamente. El cuerpo puede no ser JSON. |
| `401 INVALID_CREDENTIALS` / `INVALID_REFRESH_TOKEN` | Login o sesión inválida; borrar estado auth. Un refresh inválido revoca/limpia sesión. |
| `404 RESOURCE_NOT_FOUND` | Recurso ausente, borrado, ajeno o relación inválida: refrescar estado y resolver conflicto, no reintentar a ciegas. |
| `409 EMAIL_ALREADY_EXISTS` | Conflicto de registro, requiere intervención. |
| `5xx`, timeout, DNS o fallo de red | Temporal/no disponibilidad: conservar cola y aplicar backoff exponencial con jitter. No cerrar sesión ni descartar payload. |

No hay respuesta de error global garantizada para excepciones no manejadas, errores de binding de parámetros o el `401` de Spring Security; el cliente debe basarse primero en status y tratar el JSON de error como opcional.

## 12. Producción en Render

Producción corre en Render (`https://gym-tracker-api-s70k.onrender.com`) y puede sufrir cold start después de inactividad. Android nunca debe depender de disponibilidad inmediata: usar timeouts adecuados, cola persistente, backoff con jitter y UI que conserve la operación local pendiente. Un timeout tras POST con `clientId` se resuelve reintentando exactamente el mismo payload, nunca creando un nuevo `clientId`.

## 13. Capacidades faltantes para Android offline-first

### Requeridas antes de implementar funcionalidades Android

- Conversión de registros web heredados a instantes sólo tras conocer su zona original; sin ella deben conservarse como hora local.
- Mutaciones de workout necesarias para edición offline: update/delete de sets, add/update/delete de workout exercises, delete de workout y actualización explícita de notas/completedAt según producto.
- Creación/importación de workout con snapshot de exercises y sets autocontenidos, sin exigir una rutina previamente sincronizada.

### Deseables

- Versiones/ETag o `updatedAt` aplicable con `If-Match`, y respuestas de conflicto específicas para detectar edición concurrente.
- Idempotency keys para PATCH/PUT/DELETE y semántica `200/201` que diferencie create de replay.
- Endpoint de delta/sync paginado con tombstones, cursor y filtro `updatedSince`; hoy sólo hay listados completos y soft deletes de exercise/routine no se exponen como tombstones.
- Filtros de historial por fecha y estado, y endpoint de workout activo.

### Opcionales

- Batch de operaciones/dependencias para acelerar primer sync.
- Endpoint de resolución/diagnóstico de conflictos y trazabilidad de requests.
- Esquemas OpenAPI enriquecidos con todos los cuerpos de error, páginas y reglas de idempotencia.

## 14. Matriz de endpoints

| Feature | Método | Endpoint | Auth | clientId | Offline retry safe |
| --- | --- | --- | --- | --- | --- |
| Registro | POST | `/api/users` | No | No | No |
| Login | POST | `/api/auth/login` | No | No | No |
| Refresh web | POST | `/api/auth/refresh` | Cookie | No | No para Android |
| Logout | POST | `/api/auth/logout` | Cookie opcional | No | Condicional |
| Login nativo | POST | `/api/auth/mobile/login` | No | No | No; crea sesión nueva |
| Refresh nativo | POST | `/api/auth/mobile/refresh` | JSON refresh | No | Condicional; resultado incierto tras timeout |
| Logout nativo | POST | `/api/auth/mobile/logout` | JSON refresh | No | Sí, mismo token |
| Perfil | GET | `/api/users/me` | Bearer | -- | Sí, lectura |
| Listar/filtrar exercises | GET | `/api/exercises`, `/filter-options`, `/{id}` | Bearer | -- | Sí, lectura |
| Crear exercise | POST | `/api/exercises` | Bearer | Sí | Sí |
| Editar/borrar exercise | PUT/DELETE | `/api/exercises/{id}` | Bearer | No | Condicional |
| Listar/leer routines | GET | `/api/routines`, `/{id}` | Bearer | -- | Sí, lectura |
| Crear routine | POST | `/api/routines` | Bearer | Sí | Sí |
| Editar/borrar routine | PUT/DELETE | `/api/routines/{id}` | Bearer | No | Condicional |
| Crear/listar/leer workout | POST/GET | `/api/workouts`, `/{id}` | Bearer | POST sí | POST sí; GET sí lectura |
| Crear workout nativo | POST | `/api/workouts/mobile` | Bearer | Sí | Sí con mismo `clientId` |
| Completar/notas workout | PATCH | `/api/workouts/{id}` | Bearer | No | Condicional |
| Crear set | POST | `/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets` | Bearer | Sí | Sí |
| Estadísticas | GET | `/api/statistics/*` | Bearer | -- | Sí, lectura |

## Validación de discrepancias

El contrato frontend existente concuerda con la implementación revisada en rutas y reglas principales. La cookie HttpOnly sigue siendo exclusiva del flujo web; Android usa los endpoints nativos de JSON. `PUT /api/exercises/{id}` no elimina traducciones omitidas; las actualiza/crea por idioma.
