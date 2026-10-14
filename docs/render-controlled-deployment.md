# Auditoría y despliegue controlado — 14-10-2026

Repositorio: C:\Proyectos\gymtracker-api-exercise-tracking.
Rama: feat/unilateral-timed-api. Base auditada: 17eba25; main local: fe26fd6.
Este documento prepara acciones futuras. No autoriza ejecutarlas ahora.

## Evidencia local

- Java 21.0.9; PostgreSQL 17.10 mediante Testcontainers (imagen postgres:17).
- Arranque real de Spring Boot/Tomcat en puerto aleatorio, con Hibernate validate.
- Instalación vacía V1–V17; actualización de históricos V14–V17.
- Prueba adicional V16–V17: compara todas las columnas anteriores de usuarios
  sintéticos, ejercicios, rutinas, objetivos, rotación, entrenamientos, ejercicios
  realizados, series y sesiones; compara también todos los índices del esquema.
  Conserva UUID, clientId (incluidos nulos), carga, reps, RPE, fechas, notas,
  nombres históricos, referencias y hashes. Segundo migrate: cero operaciones.
- Las FK, restricciones de posición/número y los índices únicos parciales
  de sincronización de V12/V15 siguen vigentes. V17 no los modifica.
- completed=true/version=0 para series anteriores, métricas nuevas nulas.
  No se infiere lateralidad ni medición; no se inventan lados o zona horaria.
- No se han utilizado datos reales ni una copia de producción. Esta evidencia
  valida el esquema esperado por Flyway, no descarta drift en Render.
- Suite completa: 195 pruebas en 15 clases, 0 fallos, 0 errores, 0 omitidas.
  Comando: ./mvnw.cmd -q clean test "-Dspring.jpa.hibernate.ddl-auto=validate"
  con JWT_SECRET exclusivo de pruebas; código de salida 0.
  Informes: target/surefire-reports. Log: target/audit-full-tests.log.
- ./mvnw.cmd -q -DskipTests package: código de salida 0; JAR ejecutable generado.
- El JAR arrancó además con perfil prod, ddl-auto=validate y DB efímera
  independiente: Flyway V17 success=true, token_hash character(64) y health 200
  con JSON exacto. Logs: target/audit-jar-startup.log y audit-jar-postgres.log.
  Primer intento: conexión prematura durante init de PostgreSQL; repetido
  después de pg_isready, sin cambiar código, con resultado correcto.
  Contenedor y proceso de este ensayo detenidos al finalizar.
  SHA256 del JAR local:
  12a6fbed1587c5841adb55fbee54120c19270ac4d423e501a3a4f57cc6a99bcc.
  No se ha construido/verificado la imagen Docker completa; sí su JAR y Java 21.
- Advertencia al finalizar tests: Hikari detecta una conexión cerrada tras
  detener un contenedor de pruebas; no hubo error de test ni de validación.

## token_hash: causa y corrección

Flyway V13 define refresh_tokens.token_hash CHAR(64), NOT NULL y UNIQUE.
La entidad String esperaba VARCHAR(64). Ambos aparecen así en bd16916
(introducción de sesiones), antes de V17.

Se reprodujo el fallo real de arranque: SchemaManagementException,
found bpchar (Types#CHAR), expecting varchar(64) (Types#VARCHAR), con
spring.jpa.hibernate.ddl-auto=validate. En la configuración del repositorio
no se activa validate: el desajuste no bloqueaba por sí solo ese arranque.

La corrección mínima añade @JdbcTypeCode(SqlTypes.CHAR) al atributo.
No cambia Flyway V13, V17, columnas, hashes, índices, algoritmos ni cookies.
SHA-256 se guarda como 64 caracteres hexadecimales, por lo que CHAR(64)
no introduce relleno en estos valores. No hay invalidación de sesiones.

Las pruebas de contexto y autenticación activan validate permanentemente;
la suite final lo activa también para el resto de integraciones.
Las pruebas web/móvil comprueban login, rotación, expiración, replay,
logout y aislamiento de sesiones. No se usa ddl-auto=update.
Referencia: https://github.com/hibernate/hibernate-orm/blob/main/documentation/src/main/asciidoc/introduction/Entities.adoc

## Compatibilidad y límites

- JSON antiguo: siguen reps/weight, rutas, bearer, clientId y campos anteriores.
  Clasificación omitida permanece desconocida y se valida como REPS.
- Catálogo: PUT antiguo conserva laterality/measurement omitidos.
- Rutinas: objetivos antiguos por repeticiones siguen admitidos. Un PUT antiguo
  que sustituye un objetivo temporal del mismo ejercicio por reps devuelve 400
  y revierte la transacción. Eliminar filas conserva la semántica de reemplazo
  de la lista: los clientes no deben reconstruir listas omitiendo datos desconocidos.
- Entrenamientos: PATCH de notas/completado no toca métricas ni lados.
  Reimportar un clientId ya confirmado devuelve el estado persistido, sin
  sobrescribir cambios posteriores. No se crean series nuevas por un retry.
- PATCH de serie requiere expectedVersion/clientMutationId; modifica solo
  los campos enviados y el lado elegido. Los nulos no borran carga;
  clearAdditionalWeight=true la elimina explícitamente.
- 409 STALE_SET_VERSION: GET del entrenamiento, reconciliar con el estado
  actual y emitir otra intención con versión actual y UUID de mutación nuevo.
  Repetir la última mutación confirmada devuelve el resultado sin duplicarla.
  Solo se conserva la última mutación: un retry más antiguo tras otra escritura
  devuelve 409; no debe reintentarse indefinidamente ni cambiar el UUID a ciegas.
- clientId es necesario para deduplicar POST; no se promete idempotencia si
  se omite o se genera otro UUID al reintentar.
- La serialización por cuenta protege estos writes de workout; el techo es
  contención entre escrituras de una misma cuenta, no un bloqueo global.
- PWA: siguen las rutas y las identidades del importador existente. Se prueban
  sus contratos HTTP en backend; no se ha ejecutado la PWA real.
- Android anterior: compatible con sus contratos y registros históricos.
  No se dispone de APK/código para asegurar que tolere nuevos campos nulos
  (reps/weight/targetReps), enums o resultados unilaterales. Hacer smoke de
  esa versión antes de habilitar datos nuevos en cuentas compartidas.
- Android nuevo: las operaciones nuevas están cubiertas por integración
  backend; falta la prueba de dispositivo con su APK.
- Estadísticas actuales no suman lados ni convierten segundos a repeticiones.

## Health

GET /api/health sin Authorization devuelve exactamente HTTP 200,
Content-Type application/json, {"status":"UP"} en servidor local real.
La ruta protegida /api/workouts continúa devolviendo 401 sin bearer.
El controlador devuelve una constante, no consulta DB ni expone cuentas,
credenciales, versiones o detalles internos. Es liveness, no readiness de DB.

URL prevista para cron-job.org:
https://gym-tracker-api-s70k.onrender.com/api/health
Usar GET sin credenciales, esperar 200 y ese JSON. No se ha configurado cron.
Se hicieron dos GET públicos a esa URL desde este entorno: ambos devolvieron
503, sin JSON de health. No se confirma disponibilidad actual en producción
ni se atribuye la causa a Render, al código o a la conectividad sin más evidencia.
Resolver/verificar esta señal antes del GO. No se autenticó ninguna cuenta
de producción ni se consultó su DB. El test local no implica despliegue.

## Procedimiento futuro: antes de integrar o desplegar

1. Reservar ventana y responsable. Mantener Android nuevo sin distribución
   general hasta completar verificación. Pausar escrituras durante backup y
   migración con el mecanismo operativo disponible; revisar transacciones largas.
   No iniciar un push mientras Render tenga autodespliegue activo:
   desactivarlo en la ventana autorizada antes de integrar/publicar cambios.
2. En Render registrar servicio, rama vinculada, SHA del último despliegue
   exitoso, Dockerfile/build/start, perfil, health path y ajustes de Flyway/JPA
   sin copiar valores secretos. El SHA local de main no prueba lo desplegado.
   Comprobar que el servicio apunta a la DB y esquema previstos.
3. Un operador autorizado ejecutará consultas de metadatos, sin filas personales:

   SELECT current_database(), current_schema(), current_setting('server_version');
   SELECT installed_rank,version,description,checksum,success
     FROM flyway_schema_history ORDER BY installed_rank;
   SELECT table_name,column_name,data_type,character_maximum_length,is_nullable
     FROM information_schema.columns
     WHERE table_schema=current_schema()
       AND table_name IN ('refresh_tokens','workout_sets','routine_exercises',
                          'workout_exercises','exercises')
     ORDER BY table_name,ordinal_position;

   No asumir V16: si faltan migraciones anteriores, hay checksums distintos,
   columnas manuales o filas incompatibles, detenerse y ensayar ese estado
   en una copia aislada antes de migrar. No ejecutar repair automáticamente.
4. Guardar backup completo fuera del repo y del disco efímero del servicio.
   Usar pg_dump de la misma versión mayor que el servidor o una compatible
   más reciente. Configurar conexión mediante PGHOST/PGPORT/PGDATABASE/PGUSER,
   TLS y el gestor de secretos/PGPASSFILE existente, sin imprimir contraseñas.
   Ejemplo PowerShell, tras seleccionar la conexión correcta:

   pg_dump --format=custom --file $backupPath
   if ($LASTEXITCODE -ne 0) { throw 'Backup fallido' }
   pg_restore --list $backupPath
   if ($LASTEXITCODE -ne 0) { throw 'Archivo ilegible' }
   Get-FileHash -Algorithm SHA256 -LiteralPath $backupPath

   backupPath debe ser una ruta segura previamente elegida. Registrar fecha,
   DB, versión, tamaño, checksum y retención; no subir el dump a Git.
   Un listado no basta: restaurar en una DB vacía DE PRUEBAS distinta y verificar:

   pg_restore --exit-on-error --single-transaction --no-owner --no-privileges --dbname audit_restore $backupPath
   if ($LASTEXITCODE -ne 0) { throw 'Restauración de ensayo fallida' }

   Confirmar explícitamente host/DB de prueba antes del restore. Comparar
   historial Flyway y recuentos de tablas con el backup, sin divulgar datos.
   Ensayar el artefacto candidato en esa copia: validate, migrate y lecturas.
   Mantener la copia protegida; cualquier revisión de datos requiere autorización
   específica. Esta auditoría no ha accedido ni restaurado datos de producción.

## Integración y despliegue futuro

5. Revisar diff y commits locales. En la sesión autorizada futura:

   git fetch origin
   git switch main
   git pull --ff-only origin main
   git merge --no-ff feat/unilateral-timed-api
   ./mvnw.cmd clean test "-Dspring.jpa.hibernate.ddl-auto=validate"
   ./mvnw.cmd package -DskipTests

   Usar JWT_SECRET exclusivo de pruebas al ejecutar tests; no reutilizar el de
   producción. Si main avanzó, resolver conflictos y repetir la suite completa.
   main está en otro worktree: ejecutar estos comandos en ese worktree, no
   forzar el checkout de una rama ya abierta. Revisar SHA de integración.
   Push únicamente tras aprobación de ese resultado y backup verificado.
   Nada de estos pasos se ha ejecutado en esta auditoría.
6. En Render elegir Manual Deploy / Deploy a specific commit del SHA integrado.
   Conservar secretos y DB existentes. Dockerfile usa Java 21 y empaqueta con
   tests omitidos: por ello la suite anterior es un requisito independiente.
   Mantener ddl-auto=none o validate, nunca update/create/create-drop.
   Confirmar un único proceso responsable de la migración y margen de tiempo
   para los ALTER TABLE: requieren locks; la duración local no estima producción.
7. Logs esperados: validación Flyway correcta, migración pendiente hasta V17,
   success y arranque de EntityManagerFactory/Tomcat. Si ya era V17, ninguna
   migración pendiente. No continuar ante errores, checksum mismatch o validate.
   Verificar por metadatos V17 success=true, ninguna migración fallida y las
   columnas/restricciones nuevas. El operador puede consultar:

   SELECT version,description,checksum,success FROM flyway_schema_history
     WHERE version='17';
   SELECT conname,pg_get_constraintdef(oid) FROM pg_constraint
     WHERE conrelid IN ('workout_sets'::regclass,'routine_exercises'::regclass);

   Confirmar que V17 se aplicó una vez y que recuentos/identidades históricas
   no variaron durante la ventana sin escrituras.
8. GET público de health: exigir 200 y JSON exacto. Establecer el health path
   de Render en /api/health durante la ventana; comprobar DB separadamente
   mediante las operaciones de cuenta de pruebas. No configurar cron todavía.
9. Cuenta de pruebas propia: registro, login web/móvil, perfil, refresh y logout.
   Importar un workout antiguo con clientId fijo; repetirlo y comprobar mismo
   id y número de series. PATCH de notas debe conservar sus resultados.
10. Con APK nuevo y esa cuenta: importar bilateral REPS/DURATION y unilateral
    REPS/DURATION con carga ausente, cero y decimal. Leer estado/listado.
    Registrar un lado; PATCH del otro debe preservar el primero. Repetir
    payload de mutación y confirmar versión/IDs; enviar versión obsoleta y
    comprobar 409 STALE_SET_VERSION; GET y nueva intención deben recuperarlo.
    Simular pérdida de respuesta ignorando un resultado confirmado y repetir
    la misma solicitud. Usar una segunda cuenta propia para verificar 404
    sobre recursos ajenos. No usar datos de usuarios reales.
11. Con APK anterior y PWA reales: login, lectura de historia antigua, creación/
    importación habitual y retry sin duplicados. Probar lectura de datos nuevos
    en cuenta de pruebas para descubrir tolerancia a nulos. Si falla, limitar
    habilitación a clientes/cuentas compatibles o corregir el cliente.
    Registrar resultados y solo entonces ampliar distribución y reabrir tráfico.

## Fallos y rollback de código

- Fallo de build antes de migrar: conservar servicio anterior y corregir build.
- Fallo de migración: detener despliegue; comprobar transacción e historial.
  V17 solo contiene DDL transaccional PostgreSQL: un fallo normalmente revierte
  esa migración; no asumirlo sin verificar, ni borrar tablas o ejecutar repair.
- V17 aplicada y fallo de aplicación: conservar esquema y datos. Priorizar un
  parche de código compatible con V17, probado en copia aislada. No revertir
  migraciones, restablecer NOT NULL ni restaurar el backup automáticamente.
- Rollback permitido: solo a un artefacto conocido que incluya V1–V17 sin
  modificar checksums, tolere métricas nulas/lados/versiones y preserve los
  writes nuevos. Validar ese artefacto con esquema V17 y datos antiguos/nuevos
  antes de seleccionarlo en Render Deploys > Rollback.
- No se certifica fe26fd6 ni un binario pre-V17 como rollback seguro: carece
  del modelo nuevo y puede fallar al leer nulos; una configuración de Flyway
  también puede rechazar migraciones aplicadas ausentes del binario antiguo.
  No desactivar validación para esconderlo. Un rollback de Render no desmigra DB.
- Para el primer despliegue V17 aún no hay un release previo V17 certificado.
  Antes de abrir tráfico, conservar el artefacto V17 aprobado como referencia
  para futuras reversiones; en este primer salto, si el candidato falla tras
  migrar, pausar operaciones afectadas y desplegar una corrección V17.
  Preparar/probar un fallback V17 antes si se exige reversión inmediata.
- Revisar configuración que reutiliza Render en rollback (start, health,
  variables/grupos) sin cambiar secretos. Dashboard rollback desactiva
  autodeploy; no reactivarlo hasta resolver el incidente.
- Restauración de datos: solo decisión separada y explícita del responsable,
  tras valorar escrituras posteriores. Preferir recuperación en otra DB y
  reconciliación; nunca una restauración automática sobre producción.

Fuentes operativas consultadas:
https://render.com/docs/deploys
https://render.com/docs/rollbacks
https://render.com/docs/postgresql-backups

## Criterio de salida

READY técnico requiere suite completa verde, migraciones conservadoras verificadas,
arranque estricto y health público. El GO operativo requiere además backup
restaurado/verificado, SHA/esquema reales identificados y smoke de clientes.
La certificación local no sustituye esas puertas ni autoriza un despliegue.

Dictamen: READY FOR CONTROLLED DEPLOYMENT: SÍ para el candidato backend local.
GO de producción: pendiente; exige los controles operativos anteriores,
incluida resolución/verificación del 503 observado y smoke de clientes.
Corrección y pruebas: commit local 5ae7464. El commit documental posterior
incluye este informe. No hubo merge, push, despliegue ni migraciones de producción.