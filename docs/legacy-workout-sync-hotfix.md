# Hotfix de sincronización Android antigua — 15-10-2026

Rama aislada fix/legacy-workout-sync desde 0c9563c. Sin merge/push/despliegue.
No se consultaron ni modificaron datos reales de producción.

## Causa demostrada

Android 1.0.1 serializa gramos como kg con escala 3: 80000 -> weight:80.000.
WorkoutSetRequest de V17 añadió @Digits(4,2). Bean Validation rechaza incluso
ceros finales: POST /api/workouts/mobile devuelve 400 VALIDATION_ERROR en
exercises[0].sets[0].weight antes de WorkoutService y de la reconciliación por
clientId. LegacyWorkoutValidationTest, ejecutado antes del parche, esperaba
201 y recibió 400. Log sintético: target/legacy-repro.log.

## Decisión

Normalizar a escala 2 exclusivamente cuando la escala sea mayor de 2 y los
decimales sobrantes sean ceros. Se comprueba con stripTrailingZeros().scale()
y se ajusta con setScale(2), sin redondear. 100.50 permanece 100.50; 80.000 pasa
a 80.00; 1.001 permanece inválido. Mantener @Digits, rango y validaciones.
Sin cambios SQL/Flyway, identidades, rutinas, nombres, PWA Import, sides ni
PATCH. DTO compartido también conserva POST de series antiguas.

## Pruebas y recuperación

LegacyWorkoutValidationTest: cuatro valores válidos antiguos y cuatro
rechazos de precisión/rango. La prueba existente de snapshot móvil incluye
80.000 y 10.000, reenvío, referencias históricas y conteos sin duplicación.
Suite general con PostgreSQL 17 efímero y clave JWT solo sintética; resultados
finales abajo. Un primer intento general no tenía JWT_SECRET; otro detectó
que normalizar todas las escalas alteraba 100.50, por lo que se acotó el cambio
antes de la validación final. No se debilitaron aserciones.

La recuperación requiere además la acción individual preparada en Android,
worktree GymTracker-Sync-Hotfix, misma rama. La APK 1.0.2 omite un HTTP_400
ya persistido y no lo reactiva al pulsar el reintento general. Tras publicar
manualmente backend y actualizar la APK conservando firma/almacenamiento,
reintentar el snapshot con el clientId original. El servicio devuelve el
registro ya confirmado si existía; Android verifica el contenido antes del
ack. No hay que borrar, cambiar IDs ni editar payloads reales.

El mensaje por sí solo también puede indicar INVALID_SNAPSHOT (sin HTTP) o
HTTP_422. El usuario confirma que es un entrenamiento completado, pero no
se inspeccionó su payload privado: atribución individual todavía pendiente.

Validación final: **203 pruebas, 0 fallos, 0 errores, 0 omitidas**. Comando:
`mvnw.cmd -o -Dspring.jpa.hibernate.ddl-auto=validate test package`, con
JWT_SECRET sintético en el proceso. BUILD SUCCESS. JAR ejecutable generado.
Informes: target/surefire-reports; log: target/hotfix-final-validation.log.
