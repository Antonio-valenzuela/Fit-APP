# Aislamiento de datos Android

Fecha de revisión: 2026-07-21

## Modelo de identidad local

- Cada sesión usa un `ownerId` opaco; el correo nunca se utiliza como clave primaria de Room.
- Los modos se distinguen explícitamente como `LOCAL_ACCOUNT`, `GOOGLE_LOCAL` y `LEGACY_LOCAL`.
- El login local/offline exige un PIN de 6 a 12 dígitos. El PIN se deriva con PBKDF2-HMAC-SHA256, sal aleatoria por identidad, 210 000 iteraciones y comparación constante.
- Una credencial parcial o dañada falla cerrada. Después del tercer PIN incorrecto se aplica un backoff persistente exponencial, limitado a cinco minutos.
- El cierre de sesión elimina el propietario activo de memoria y cancela el trabajo asíncrono de la sesión.

## Alcance en Room

La base `AppDatabase` usa el esquema 12. `food_logs`, `food_entries`, `food_items`, `workout_logs`, `activity_sessions` y `health_logs` incluyen `ownerId`; sus DAO filtran lecturas, actualizaciones y borrados por propietario. `users`, `daily_activity`, `streaks`, `daily_missions` y `achievements` usan la identidad opaca en `uid`/`userId`.

Los repositorios rechazan entidades existentes de otro propietario; sólo una entidad nueva puede recibir el propietario activo. Un cambio de sesión no puede reutilizar un objeto obsoleto para sobrescribir datos de otra identidad.

## Migración 11 a 12

`MIGRATION_11_12` realiza dos acciones:

1. Añade `ownerId` a las seis tablas sensibles y asigna `legacy_owner_v1` a las filas existentes.
2. Reasigna `users.uid = local_user` y las cuatro tablas de progreso con `userId = local_user` a `legacy_owner_v1`.

La recuperación del perfil histórico no es automática para cualquier usuario: sólo se autoriza cuando el correo introducido coincide con el correo del perfil Room migrado. La prueba `OwnerIsolationRoomMigrationInstrumentedTest` crea datos en v11, ejecuta la migración y valida esquema, propietario, perfil y progreso. La prueba compila en este entorno; su ejecución requiere dispositivo o emulador.

## Borrado de cuenta

La purga usa `AccountDataDao` como operación Room transaccional y elimina las once tablas vinculadas a la identidad. Después elimina preferencias owner-scoped, binding, sal/hash del PIN, estado de backoff y marcador legacy sólo para la identidad activa. El material de otras identidades no se toca.

Las pruebas verifican que volver a registrar el mismo correo con otro PIN crea un perfil vacío y no recupera datos anteriores.

## Límites verificados

- `allowBackup=false` y reglas de extracción/backup restrictivas.
- Producción niega tráfico cleartext; debug sólo lo permite a `localhost`, `127.0.0.1` y `10.0.2.2`.
- Analytics sólo emite tres eventos operativos en builds debug y descarta propiedades.
- No se registran PIN, correo, token, GPS, métricas de salud ni metadata de fotos.

## Validación pendiente de entorno

- Ejecutar la migración instrumentada y las pruebas de ciclo de vida en Android 10/API 29 físico o emulado.
- Probar actualización real desde una instalación v11 con una copia de datos representativa antes de publicar en producción.
