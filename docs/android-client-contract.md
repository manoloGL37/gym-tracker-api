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

### Registro, login y perfil

| Operación | Método/ruta | Auth | Cuerpo / respuesta |
| --- | --- | --- | --- |
| Registro | `POST /api/users` | Pública | `CreateUserRequest { email, password }` -> `201 UserResponse { id, email, createdAt }` |
| Login | `POST /api/auth/login` | Pública | `LoginRequest { email, password }` -> `200 { accessToken }` y `Set-Cookie` de refresh |
| Perfil actual | `GET /api/users/me` | `Bearer` | `200 UserResponse` |
| Refresh | `POST /api/auth/refresh` | Cookie de refresh | sin cuerpo -> `200 { accessToken }` y cookie rotada |
| Logout | `POST /api/auth/logout` | Cookie opcional | sin cuerpo -> `204`, revoca familia si la cookie es válida y siempre la borra |

`email` debe ser no vacío y email válido. La contraseña de registro es 8--100 caracteres; en login sólo se exige no vacía. Un email ya registrado devuelve `409 EMAIL_ALREADY_EXISTS`; credenciales inválidas, `401 INVALID_CREDENTIALS`. Registrar no inicia sesión.

El access token es un JWT firmado cuyo `sub` es el UUID de usuario e incluye `iat` y `exp`; se envía como `Authorization: Bearer <accessToken>`. No hay roles, ni `tokenType`, usuario, refresh token o expiración como campos JSON. Por defecto expira a los 3.600.000 ms (una hora); el cliente puede leer `exp`, pero debe tratar un `401` como la autoridad final.

### Refresh, expiración y cookies

Login crea una sesión persistente y almacena sólo SHA-256 del refresh token. La cookie se llama `refreshToken` por defecto, es host-only (sin `Domain`), `HttpOnly`, `Path=/api/auth` y tiene `Max-Age` de la sesión. La vida del refresh es absoluta desde el login, por defecto 2.592.000.000 ms (30 días), y no se prolonga al refrescar.

Cada refresh correcto revoca el token presentado, crea otro de la misma familia, entrega una cookie y un JWT nuevos. Hay una ventana de gracia de 10 segundos para refrescos concurrentes: reutilizar el token recién rotado durante esa ventana crea un sucesor válido; reutilizarlo después revoca los tokens activos de toda la familia y devuelve `401 INVALID_REFRESH_TOKEN`. Coalescer refreshes sigue siendo obligatorio en el cliente. Un refresh inválido también emite una cookie vacía con `Max-Age=0`. Logout es idempotente desde el punto de vista HTTP, pero el access JWT no se revoca: Android debe descartarlo localmente.

En producción (`prod`), la cookie usa `Secure=true` y `SameSite=None`; en local por defecto, `Secure=false` y `SameSite=Lax`. `SameSite=None` sin `Secure` impide iniciar la aplicación. CSRF está desactivado y la app es stateless.

### Decisión Android: la cookie actual no es un contrato nativo suficiente

No se debe asumir que el modelo de cookie `HttpOnly` del navegador se transportará ni gestionará de forma fiable con el cliente HTTP nativo de Android. El backend no devuelve el refresh token en JSON ni acepta `Authorization`/cuerpo para `/api/auth/refresh` o logout: espera exclusivamente la cookie `refreshToken`. Por tanto, **el flujo actual no permite implementar de forma soportada restauración/rotación de sesión nativa**.

Para Android, el objetivo recomendado es access JWT de corta vida en memoria y refresh token de larga vida en Android Keystore/EncryptedSharedPreferences, enviado en un mecanismo nativo explícito (por ejemplo, cuerpo o encabezado dedicado) sobre TLS. Eso exige primero una variante de sesión/refresh/logout diseñada para móviles, con rotación y revocación equivalentes; no se implementa en esta tarea. Hasta entonces, sólo un cliente que preserve cookies de forma deliberada y segura podría operar, pero no es la arquitectura nativa autorizada por este contrato.

### CORS

CORS es relevante para web, no para peticiones nativas Android. Permite orígenes exactos configurados, credenciales, métodos `GET, POST, PUT, PATCH, DELETE, OPTIONS` y encabezados `Authorization, Content-Type, Accept, Origin`, con preflight de 3600 segundos; `*` se rechaza. En producción sólo está permitido por defecto `https://gym-tracker-eight-dun.vercel.app`. No añadir un origen Android: las apps nativas no tienen un Origin web que deba autorizarse.

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

`LocalDate` se serializa como `YYYY-MM-DD`. Los timestamps son `LocalDateTime` ISO-8601 sin offset, por ejemplo `2026-09-24T18:30:00` (la fracción de segundo puede estar presente). El backend no declara zona del usuario, zona de servidor, UTC ni conversión de offsets: Android no debe etiquetarlos como UTC ni aplicar conversión automática. Debe acordarse una convención de producto antes de sincronizar entre zonas.

`createdAt` de usuario, `createdAt/updatedAt` de rutina y `createdAt` de entrenamiento los genera el servidor. `startedAt` puede venir del cliente; `completedAt` puede venir del cliente sólo en POST. Estadísticas interpretan ambos extremos como inclusivos sobre `startedAt`, implementado internamente como `[from 00:00, (to + 1 día) 00:00)` en `LocalDateTime`.

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
| B. Seguras con condiciones | GETs; PUT rutina; PUT ejercicio; PATCH workout; DELETE exercise/routine; logout | Reintentar GET sólo ante red/5xx. Las mutaciones no tienen control de concurrencia/versionado: serializar por recurso, volver a leer tras fallo incierto y no reintentar DELETE tras un `404` como si fuera error. PUT rutina recrea hijos. PATCH no puede expresar limpiar notas ni timestamp final histórico. Logout puede repetirse, pero requiere el canal móvil de refresh inexistente. |
| C. Inseguras / capacidad backend necesaria | Crear sesión nativa persistente; actualizar/borrar sets; actualizar/borrar/agregar workout exercises; borrar workouts; importar un workout con exercises que no proceden de una rutina sincronizada; sincronización de conflictos | No hay endpoint o contrato de idempotencia/versiones para estas necesidades. La cookie HttpOnly es específica del navegador. |

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
| `401` en recurso bearer | Access inválido/caducado; intentar una sola restauración de sesión cuando exista el futuro flujo móvil; no repetir la petición infinitamente. El cuerpo puede no ser JSON. |
| `401 INVALID_CREDENTIALS` / `INVALID_REFRESH_TOKEN` | Login o sesión inválida; borrar estado auth. Un refresh inválido revoca/limpia sesión. |
| `404 RESOURCE_NOT_FOUND` | Recurso ausente, borrado, ajeno o relación inválida: refrescar estado y resolver conflicto, no reintentar a ciegas. |
| `409 EMAIL_ALREADY_EXISTS` | Conflicto de registro, requiere intervención. |
| `5xx`, timeout, DNS o fallo de red | Temporal/no disponibilidad: conservar cola y aplicar backoff exponencial con jitter. No cerrar sesión ni descartar payload. |

No hay respuesta de error global garantizada para excepciones no manejadas, errores de binding de parámetros o el `401` de Spring Security; el cliente debe basarse primero en status y tratar el JSON de error como opcional.

## 12. Producción en Render

Producción corre en Render (`https://gym-tracker-api-s70k.onrender.com`) y puede sufrir cold start después de inactividad. Android nunca debe depender de disponibilidad inmediata: usar timeouts adecuados, cola persistente, backoff con jitter y UI que conserve la operación local pendiente. Un timeout tras POST con `clientId` se resuelve reintentando exactamente el mismo payload, nunca creando un nuevo `clientId`.

## 13. Capacidades faltantes para Android offline-first

### Requeridas antes de implementar funcionalidades Android

- Contrato de sesión móvil: emitir/aceptar refresh token en transporte nativo seguro, rotación, logout/revocación y documentación de almacenamiento Keystore; mantener aislado el flujo browser-cookie.
- Decisión y contrato de zona horaria (preferiblemente timestamps con offset/UTC y zona de usuario) para no corromper fechas históricas/estadísticas entre dispositivos.
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
| Refresh | POST | `/api/auth/refresh` | Cookie | No | No (gap Android) |
| Logout | POST | `/api/auth/logout` | Cookie opcional | No | Condicional |
| Perfil | GET | `/api/users/me` | Bearer | -- | Sí, lectura |
| Listar/filtrar exercises | GET | `/api/exercises`, `/filter-options`, `/{id}` | Bearer | -- | Sí, lectura |
| Crear exercise | POST | `/api/exercises` | Bearer | Sí | Sí |
| Editar/borrar exercise | PUT/DELETE | `/api/exercises/{id}` | Bearer | No | Condicional |
| Listar/leer routines | GET | `/api/routines`, `/{id}` | Bearer | -- | Sí, lectura |
| Crear routine | POST | `/api/routines` | Bearer | Sí | Sí |
| Editar/borrar routine | PUT/DELETE | `/api/routines/{id}` | Bearer | No | Condicional |
| Crear/listar/leer workout | POST/GET | `/api/workouts`, `/{id}` | Bearer | POST sí | POST sí; GET sí lectura |
| Completar/notas workout | PATCH | `/api/workouts/{id}` | Bearer | No | Condicional |
| Crear set | POST | `/api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets` | Bearer | Sí | Sí |
| Estadísticas | GET | `/api/statistics/*` | Bearer | -- | Sí, lectura |

## Validación de discrepancias

El contrato frontend existente concuerda con la implementación revisada en rutas y reglas principales. Esta versión para Android añade la conclusión operativa que allí no era necesaria: el refresh por cookie HttpOnly no es un flujo nativo soportado. También hace explícito un detalle de implementación importante para clientes que actualicen exercises: `PUT /api/exercises/{id}` no elimina traducciones omitidas; las actualiza/crea por idioma.
