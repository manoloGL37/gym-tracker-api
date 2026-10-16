# Contrato de lateralidad por ejercicio de rutina

Fecha: 2026-10-15. Base: main 0c9563c. Rama: feat/routine-laterality-api.
Plan funcional leído: C:/Users/manol/AndroidStudioProjects/GymTracker-Routine-Laterality/docs/ROUTINE_LATERALITY_PLAN.md.

## Modelo

RoutineExercise añade lateralityOverride nullable (enum BILATERAL / UNILATERAL).
PostgreSQL: routine_exercises.laterality_override VARCHAR(20), CHECK de enum.
No modifica la identidad ni clasificación del catálogo, measurement, objetivos ni carga.
Lateralidad efectiva al iniciar: lateralityOverride != null ? lateralityOverride : catalog.laterality.
Si ambos son null, el snapshot conserva null. El response de rutina publica el override,
no un valor efectivo que pueda confundirse con la configuración guardada.

## Endpoints y presencia del campo

Todos requieren Bearer JWT y usan la cuenta autenticada.

- POST /api/routines: cada exercises[] admite lateralityOverride; omitido o null crea herencia.
- PUT /api/routines/{id}: actualización completa existente, con semántica de presencia para este campo.
- GET /api/routines/{id}: cada exercises[] incluye lateralityOverride, también cuando es null.
- GET /api/routines: mantiene resúmenes paginados; recuperar cada detalle para sincronizar ejercicios.
- POST /api/workouts: copia lateralidad efectiva a WorkoutExercise.laterality al crear el snapshot.
- GET /api/workouts/{id}: devuelve el snapshot almacenado, independiente de cambios futuros.
- POST /api/workouts/mobile: sigue importando lateralidad realizada del payload; no recalcula desde la rutina.

| lateralityOverride en un elemento PUT | Resultado |
| --- | --- |
| Omitido | Conserva valor anterior del mismo exerciseId; nuevo ejercicio hereda |
| null | Elimina override explícitamente; hereda catálogo |
| "BILATERAL" | Fuerza bilateral |
| "UNILATERAL" | Fuerza unilateral |

El PUT sigue reemplazando filas e IDs hijos. La conservación se hace por exerciseId,
independientemente de position. Si un exerciseId aparecía varias veces con valores
DISTINTOS (incluido null), cualquier aparición entrante con campo omitido produce 400
atómico. El cliente debe mandar el campo explícito en todas las apariciones conservadas.
Si los duplicados tenían todos el mismo valor, la omisión conserva ese valor.
No se emparejan por posición ni por IDs hijos efímeros. Eliminar un elemento de exercises
elimina su configuración como parte de la eliminación explícita del elemento; eliminar
y volver a añadir en otra petición crea un elemento nuevo que hereda si omite el campo.
Enums desconocidos producen 400. Rutinas ajenas o eliminadas producen 404.
No hace falta PATCH: PUT distingue omisión y null mediante presencia en deserialización.
Los otros campos mantienen sus reglas actuales de reemplazo y validación.

## Ejemplo JSON

POST /api/routines (201) o PUT /api/routines/{id} (200):

```json
{
  "clientId": "11111111-1111-1111-1111-111111111111",
  "name": "Pierna A",
  "description": null,
  "exercises": [{
    "exerciseId": "22222222-2222-2222-2222-222222222222",
    "position": 0,
    "sets": 3,
    "targetReps": 12,
    "targetDurationSeconds": null,
    "restSeconds": 60,
    "notes": null,
    "lateralityOverride": "UNILATERAL"
  }]
}
```

Response (GET detalle devuelve la misma estructura):

```json
{
  "id": "33333333-3333-3333-3333-333333333333",
  "clientId": "11111111-1111-1111-1111-111111111111",
  "name": "Pierna A",
  "description": null,
  "exercises": [{
    "id": "44444444-4444-4444-4444-444444444444",
    "exerciseId": "22222222-2222-2222-2222-222222222222",
    "position": 0,
    "sets": 3,
    "targetReps": 12,
    "targetDurationSeconds": null,
    "restSeconds": 60,
    "notes": null,
    "lateralityOverride": "UNILATERAL"
  }],
  "createdAt": "2026-10-15T12:00:00",
  "updatedAt": "2026-10-15T12:00:00"
}
```

Para heredar, enviar el PUT completo con `"lateralityOverride": null` en el elemento.
Para conservar, omitir esa propiedad del elemento. En JSON, null debe enviarse realmente:
un serializador que omita null no puede expresar el borrado.
Inicio de sesión: POST /api/workouts con
`{"routineId":"33333333-3333-3333-3333-333333333333"}` devuelve exercises[].laterality
igual a UNILATERAL en este ejemplo; no publica el override en el snapshot.

## Compatibilidad y sincronización Android

Clientes antiguos que omiten la propiedad conservan overrides al editar o reordenar;
se rechaza una correspondencia ambigua sin escribir cambios. POST idempotente conserva
su comportamiento: repetir clientId devuelve la rutina previamente creada y no la edita.
PUT repetido conserva el estado lógico; los IDs hijos siguen siendo reemplazables.
No se introducen revisiones ni resolución de conflictos concurrentes: PUT conserva la
semántica actual de última escritura para valores explícitos.

Android debe persistir la propiedad en Room 12, dominio, DTO y WriteAttempt; usar
presencia del campo en el detalle de rutinas como evidencia de soporte (incluido null).
No basta detectar campos en el catálogo. Ante servidor antiguo que no publique el campo,
no confirmar como sincronizado un override solicitado. No alterar intentos en vuelo.
Al iniciar offline, capturar override ?: catálogo en el snapshot local. Importar móvil
conserva ese snapshot incluso si entretanto cambió la plantilla. Reintentos mobile no
reclasifican importaciones existentes. No cambiar históricos ni null históricos.
Android y las pruebas de dispositivo quedan fuera de esta entrega backend.

## Migración e integración

V18__routine_exercise_laterality_override.sql es aditiva, nullable y sin backfill.
V17 no se modifica. Filas existentes heredan; no se toca ninguna tabla de históricos.
Se verificó en lectura el worktree del hotfix: no había V18 al iniciar esta tarea.
Volver a comprobar numeración antes de integrar; no modificar migraciones ya desplegadas.
No se ha hecho merge, push ni despliegue. Main y los otros worktrees no se modifican.

## Validación

Las suites RoutineControllerIntegrationTest, WorkoutControllerIntegrationTest y
WorkoutSnapshotMigrationTest cubren persistencia de los tres valores, clientes antiguos,
reordenación, reintentos PUT, borrado explícito, duplicados ambiguos, enum inválido,
aislamiento por cuenta/rutina, snapshots futuros, históricos activos/completados/null,
importación móvil idempotente y migración real PostgreSQL 17→18 con datos anteriores.
El test existente de migración 16→última también conserva sus comparaciones históricas.

Comando reproducible en PowerShell, con Docker disponible y un secreto sintético de test:

```powershell
$env:JWT_SECRET = 'routine-laterality-local-test-secret-at-least-32-bytes'
.\mvnw.cmd clean verify
```

Resultado final: `clean verify` correcto, 201 pruebas, 0 fallos, 0 errores,
0 omitidas, PostgreSQL 17 real en Testcontainers, Hibernate ddl-auto=validate y JAR
Spring Boot generado. Incluye 40 pruebas de rutinas, 61 de entrenamientos y 3 de migraciones.
Se ejecuta también `-DskipTests package` sobre las fuentes finales para verificar el empaquetado.
La primera ejecución sin JWT_SECRET falló por configuración del entorno; la validación
final usa únicamente la clave sintética indicada, sin secretos de producción.

READY FOR ANDROID INTEGRATION: SÍ (contrato backend validado; Android pendiente).
El commit final se indica en la entrega para no introducir un hash autorreferencial.
