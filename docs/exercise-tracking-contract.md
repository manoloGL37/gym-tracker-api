# Contrato de ejercicios y resultados — 14-10-2026

Este documento define la extensión del contrato Android/PWA. Los endpoints,
autenticación bearer, IDs de servidor, `clientId`, nombres históricos y fechas
existentes siguen vigentes. No hay un segundo modelo de entrenamiento.

## Ejercicios, rutinas e instantáneas

`CreateExerciseRequest` (POST/PUT `/api/exercises`) y `ExerciseResponse` añaden:

| Campo | Valores | Ausente o null |
| --- | --- | --- |
| `laterality` | `BILATERAL`, `UNILATERAL` | Desconocida; PUT conserva el valor existente |
| `measurement` | `REPS`, `DURATION` | Desconocida; PUT conserva el valor existente |

Los ejercicios del catálogo anterior permanecen sin clasificación: no se deduce
lateralidad o medición de su nombre. El importador del dataset conserva estos
campos cuando actualiza otras propiedades.

Cada ejercicio de `POST /api/workouts/mobile` acepta los mismos dos campos;
`WorkoutExerciseResponse` los devuelve en GET detalle, GET listado y POST. Son
parte de la instantánea realizada: no se buscan en el catálogo al leer históricos.
Un `exerciseId` no sustituye estos campos en la importación móvil. Android debe
enviar la clasificación capturada al realizar el ejercicio. Una importación
antigua que no los envía permanece desconocida y contiene resultados sin lado.

`POST /api/workouts` sigue copiando la rutina actual; ahora copia también la
clasificación del catálogo al crear sus ejercicios. Un objetivo temporal explícito
de rutina determina `measurement: DURATION` si el catálogo aún no está clasificado.

Las filas de rutina (`POST/PUT /api/routines`, `RoutineExerciseResponse`) aceptan
exactamente uno de `targetReps` o `targetDurationSeconds`, entero positivo.
`targetReps` pasa a ser nullable y `targetDurationSeconds` es nuevo y nullable.
Si el catálogo declara medición, el objetivo debe coincidir. Ejemplo:

```json
{"exerciseId":"00000000-0000-4000-8000-000000000001","position":0,"sets":3,"targetReps":null,"targetDurationSeconds":45,"restSeconds":60,"notes":null}
```

Un PUT antiguo que intenta sustituir una fila temporal existente con el mismo
`exerciseId` sin objetivo temporal (también al reordenar) devuelve 400 y revierte toda la
operación. No borra su objetivo por omisión. Las filas antiguas con `targetReps`
siguen funcionando. Eliminar filas mediante PUT conserva la semántica existente.

## Una fila por serie lógica

`setNumber` sigue siendo único y 1-based dentro del ejercicio realizado.
La identidad es el `id` de la serie; no hay IDs de serie separados por lado.
`clientId` sigue identificando la serie completa dentro del workout-exercise.

`WorkoutSetRequest`, tanto en `POST .../sets` como dentro de la importación móvil:

| Campo | Tipo / regla |
| --- | --- |
| `clientId` | UUID opcional, identidad local estable de la serie |
| `setNumber` | Entero >= 1 |
| `reps` | Entero positivo opcional; nombre existente de `repetitions` bilateral |
| `durationSeconds` | Entero positivo opcional, segundos exactos |
| `weight` | Decimal opcional; nombre existente de `additionalWeightKg` bilateral |
| `rpe` | Decimal opcional entre 0 y 10, de la serie lógica |
| `completed` | Booleano opcional; bilateral omitido/null significa true |
| `left`, `right` | Objetos opcionales, descritos abajo; exclusivos de UNILATERAL |

`left` corresponde a `side: LEFT` y `right` a `side: RIGHT`. Cada objeto acepta:

```json
{"repetitions":10,"durationSeconds":null,"additionalWeightKg":22.5,"completed":true}
```

Dentro de un objeto de lado `completed` es obligatorio. La ausencia del objeto
significa que ese lado no está registrado. No enviar `side` dentro del objeto:
su clave `left` o `right` determina el lado. No hay propiedad `side` en resultados
bilaterales; en PATCH se usa el enum explícito.

Reglas comunes:

- `REPS` solo permite repeticiones; `DURATION` solo permite segundos. La métrica
  correspondiente es obligatoria al completar el resultado. Un resultado
  incompleto puede no tener aún su métrica. No admite cero, negativos, fracciones
  ni enteros mayores que 2147483647 en repeticiones o segundos. El backend no
  convierte minutos, milisegundos ni valores fraccionarios.
- Carga opcional en ambas mediciones: 0–9999.99 kg, hasta dos decimales, sin
  redondear entradas con más precisión. null significa no registrada; 0 es
  una carga registrada. Los clientes deben conservar decimales de kg.
- UNILATERAL rechaza `reps`, `durationSeconds` o `weight` en el nivel de serie.
  Solo escribe los lados enviados. Su `completed` de serie se calcula siempre
  como LEFT completo **y** RIGHT completo; el valor enviado no lo fuerza.
- BILATERAL rechaza objetos de lado. Clasificación desconocida acepta el
  contrato histórico sin lado, con repeticiones; nunca crea mediciones laterales.
- `measurement: null` conserva la interpretación histórica REPS al validar
  resultados, sin modificar la clasificación persistida. Para tiempo, enviar
  explícitamente DURATION.

`WorkoutSetResponse` devuelve todos los campos anteriores, más `id` UUID y
`version` entero largo desde 0. `completed` siempre es booleano. Los campos
opcionales sin valor son null. No añade `repetitions`/`additionalWeightKg` como
alias en el nivel bilateral: los nombres de lectura/escritura siguen siendo
`reps`/`weight`.

## Ejemplos de las combinaciones

Cada fragmento corresponde a una serie 1. La clasificación se envía en el
ejercicio que contiene la serie.

| Ejercicio | laterality / measurement | JSON de serie |
| --- | --- | --- |
| Press banca | BILATERAL / REPS | `{"setNumber":1,"reps":10,"weight":80}` |
| Repeticiones sin carga | BILATERAL / REPS | `{"setNumber":1,"reps":10,"weight":null}` |
| Remo unilateral o step-up | UNILATERAL / REPS | `{"setNumber":1,"left":{"repetitions":10,"additionalWeightKg":22,"completed":true},"right":{"repetitions":9,"additionalWeightKg":24,"completed":true}}` |
| Plancha | BILATERAL / DURATION | `{"setNumber":1,"durationSeconds":45,"weight":null}` |
| Plancha lastrada | BILATERAL / DURATION | `{"setNumber":1,"durationSeconds":45,"weight":20}` |
| Plancha lateral | UNILATERAL / DURATION | `{"setNumber":1,"left":{"durationSeconds":30,"completed":true},"right":{"durationSeconds":45,"completed":true}}` |
| Plancha lateral lastrada | UNILATERAL / DURATION | `{"setNumber":1,"left":{"durationSeconds":30,"additionalWeightKg":10,"completed":true},"right":{"durationSeconds":45,"additionalWeightKg":12.5,"completed":true}}` |

Importación completa, solo LEFT completado (serie `completed: false` en respuesta):

```json
{
  "clientId": "00000000-0000-4000-8000-000000000010",
  "routineId": null,
  "nameSnapshot": "Core",
  "startedAt": "2026-10-14T08:00:00Z",
  "completedAt": "2026-10-14T08:30:00Z",
  "calendarZone": "Europe/Madrid",
  "exercises": [{
    "clientId": "00000000-0000-4000-8000-000000000011",
    "exerciseId": null,
    "exerciseNameSnapshot": "Plancha lateral lastrada",
    "position": 0,
    "laterality": "UNILATERAL",
    "measurement": "DURATION",
    "sets": [{
      "clientId": "00000000-0000-4000-8000-000000000012",
      "setNumber": 1,
      "left": {"durationSeconds": 30, "additionalWeightKg": 10, "completed": true}
    }]
  }]
}
```

`completedAt` del entrenamiento no fuerza el completado de todas sus series:
se puede finalizar una sesión con lados o series pendientes.

## Actualizaciones parciales, conflictos y reintentos

Nuevo endpoint autenticado:

`PATCH /api/workouts/{workoutId}/exercises/{workoutExerciseId}/sets/{setId}`

```json
{
  "expectedVersion": 0,
  "clientMutationId": "00000000-0000-4000-8000-000000000013",
  "side": "RIGHT",
  "durationSeconds": 45,
  "additionalWeightKg": 12.5,
  "completed": true
}
```

`expectedVersion` (long >= 0) y `clientMutationId` (UUID) son obligatorios.
`side` es LEFT o RIGHT para unilateral y se omite/null para bilateral. Los
campos de métrica se llaman `repetitions`, `durationSeconds`, `additionalWeightKg`
en este endpoint para ambos casos. `completed` es opcional. No permite cambiar
clasificación, IDs, setNumber o RPE.

Campos omitidos/null conservan su valor. `clearAdditionalWeight: true` borra
la carga del resultado seleccionado; combinarlo con una carga no nula es 400.
`completed: false` desmarca solo ese resultado. Si el lado aún no existe, se
crea incompleto y sin copiar nada del opuesto. Se valida el resultado fusionado.
Una mutación válida incrementa `version` de la serie y devuelve 200 con la
serie completa, ambos lados y nueva versión.

La versión es de **toda la serie**, para que la fusión y su completado sean
atómicos. Si otra escritura se adelantó: 409 `STALE_SET_VERSION`, sin cambios.
Android debe releer el workout, reconciliar el lado local y crear una nueva
mutación con la versión actual; no incrementar una versión a ciegas.

Persistir `clientMutationId`, `expectedVersion` y payload en Room antes de enviar.
Un retry de la última mutación confirmada de esa serie devuelve 200 sin volver
a escribir, incluso tras perder la respuesta después del commit. El primer
payload de ese UUID gana: no reutilizar el UUID para otra operación. Se conserva
la última identidad de mutación, no un registro ilimitado: si ya hubo otra
mutación posterior, un retry antiguo devuelve 409 y exige reconciliación.
No duplica datos ni restaura una versión anterior. Serializar la cola por serie.

Los POST conservan sus claves de deduplicación: `(userId, workout.clientId)`
y `(workoutExerciseId, set.clientId)`. Responden 201 en creación y replay.
El POST móvil es atómico e inmutable como antes: repetirlo devuelve el grafo
persistido, no actualiza el snapshot. Para editar usar PATCH, nunca cambiar el
payload de un POST ya encolado. El backend serializa las escrituras de workout
por cuenta y mantiene los índices únicos para cubrir reintentos concurrentes.

401 sin autenticación; 404 si cualquier relación de la ruta no pertenece a la
cuenta o al padre indicado; 400 ante enum/número inválido, métrica incompatible,
lado inapropiado o duplicado de número sin identidad coincidente. Una identidad
local igual en otra cuenta no accede a los datos de la primera.

## Históricos, PWA, estadísticas y salud

V17 añade columnas y permite null en carga/repeticiones; no cambia UUIDs,
claves, relaciones, números ni métricas históricas. Los antiguos resultados
realizados quedan completos, versión 0, sin duración ni objetos de lado. Su
lateralidad y medición siguen desconocidas. Android debe conservar ese null
en importaciones, sin reclasificar usando el catálogo actual.

La importación histórica PWA por POST `/api/workouts` y POST `.../sets` sigue
aceptando los mismos payloads de repeticiones y peso; la importación por snapshot
`/api/workouts/mobile` sigue aceptando snapshots antiguos sin campos nuevos.
No se añade sincronización con un servidor PWA externo ni un importador masivo.
Para importar tiempo/lados, usar un snapshot con clasificación explícita.
Los clientes anteriores conservan sus operaciones históricas; para mostrar
datos nuevos deben actualizar sus lectores: `reps` y `weight` ahora pueden ser
null y nunca se rellenan con cero ni con el resultado del otro lado.

Las estadísticas existentes cuentan series lógicas completas una sola vez.
Sus agregados de repeticiones/volumen siguen usando exclusivamente los campos
bilaterales históricos; no suman ni promedian lados ni convierten segundos a
repeticiones. No hay aún estadísticas específicas por lado o de duración.

`GET /api/health` es público, ligero y devuelve 200 `{"status":"UP"}`.
Indica respuesta del proceso HTTP, no disponibilidad de PostgreSQL ni readiness
de todos los servicios. No expone versiones, credenciales ni datos de cuentas.
No se añade ningún proceso interno para mantener Render despierto.


## Validacion ejecutada

El 14-10-2026 se ejecuto `./mvnw.cmd -q clean test` con Java 21 y un
JWT_SECRET exclusivo de pruebas, sobre PostgreSQL 17 en Testcontainers:
193 pruebas en 15 clases, 0 fallos, 0 errores y 0 omitidas; Maven termino con
codigo 0. Incluye 60 pruebas de entrenamientos, 36 de rutinas, 18 de estadisticas,
26 de catalogo y la migracion de historicos V14 hasta V17. Se verificaron IDs y
clientId conservados, medidas originales, ausencia de lados inventados, validacion
Flyway y segundo migrate sin operaciones. Las integraciones tambien aplican
V1-V17 desde una base vacia. Informes locales: `target/surefire-reports`;
log de la ejecucion final: `target/exercise-tracking-tests.log`.

Comprobacion adicional, fuera de la configuracion habitual: activar
`-Dspring.jpa.hibernate.ddl-auto=validate` detecta una discrepancia preexistente
en `refresh_tokens.token_hash` (SQL CHAR frente a mapeo VARCHAR). Esa validacion
global estricta no pasa; no se ha cambiado autenticacion ni su esquema para
resolverla en esta tarea. La suite normal y la validacion Flyway descritas arriba
si pasan. No se han aplicado migraciones a produccion ni realizado despliegues.


Actualización de la auditoría final: la discrepancia token_hash descrita arriba se ha corregido mediante el mapeo JPA CHAR, sin cambiar SQL ni datos. La validación estricta y la evidencia actual sustituyen esa observación pendiente; véase [auditoría de Render](render-controlled-deployment.md).