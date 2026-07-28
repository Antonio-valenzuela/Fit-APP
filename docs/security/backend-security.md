# Controles de seguridad del backend

## Arranque, secretos e identidad

`config/security.js` valida toda la configuración antes de que Express escuche. `JWT_SECRET`, `GEMINI_API_KEY`, `DATABASE_URL`, Google OAuth, TLS de MySQL y el store compartido de cuotas son obligatorios en producción. Se rechazan valores vacíos, placeholders y secretos JWT de menos de 32 bytes. No existe fallback de API key o JWT.

Las variables del servidor se documentan en `.env.backend.example`; `.env.example` queda reservado al cliente Android y las variables server-only se excluyen de `BuildConfig`.

El endpoint `/api/auth/google` acepta únicamente `idToken`, valida firma y audiencia con Google en el servidor, registra un principal canónico `google:<sub>` y emite un JWT interno HS256 mínimo y de corta duración. La verificación posterior fija algoritmo, issuer, audience, expiración y sujeto; autorización por rol permanece separada. Las identidades históricas se migran como `legacy:<id>`, evitando colisiones entre emisores.

El adaptador MySQL usa un pool real, prueba conectividad antes del `listen`, exige TLS con verificación de identidad en producción y ofrece transacciones con commit/rollback. No existe un stub permisivo en producción.

## Frontera HTTP

- CORS permite solicitudes nativas sin `Origin` y orígenes web de una allowlist. En producción sólo admite orígenes HTTPS.
- Helmet y CORS se ejecutan antes de los límites; Express no expone `X-Powered-By`. HSTS sólo se emite en producción sobre solicitudes reconocidas como HTTPS.
- `TRUST_PROXY_HOPS` es un entero acotado; `true` está prohibido.
- JSON ordinario se limita a 256 KiB. El parser de 6 MiB existe sólo en las rutas de imagen, después de autenticación y rate limit; la imagen decodificada se limita a 4 MiB.
- El limitador pre-auth usa la IP resuelta por los saltos configurados. Los límites post-auth usan sujeto verificado, método y ruta canónica; Gemini usa una cuota menor.
- Producción requiere store MySQL compartido, tiempos UTC/epoch y limpieza limitada por namespace fuera del camino de cada petición.
- Las respuestas autenticadas sensibles declaran `Cache-Control: private, no-store`.

## Consentimiento, logging y auditoría

`services/securityLogger.js` escribe JSON de una línea mediante allowlist. Descarta campos desconocidos, neutraliza caracteres de control y nunca registra tokens, correo, cuerpos, payload médico, coordenadas, SQL o parámetros.

Los eventos cubren autenticación, autorización, rate limits, consentimiento, apagado y fallos fatales. La migración 006 elimina IP/user-agent históricos y alinea estados. `/health/sync` exige consentimiento vigente, no lo promueve implícitamente, bloquea `denied`/`revoked`, filtra tipos denegados en `partial` y ejecuta datos y auditoría dentro de una transacción.

La migración 007 crea principales canónicos, vuelve textuales/binarias las claves de identidad, recrea FKs y crea la tabla de cuotas compartida. Las migraciones 006 y 007 deben aplicarse y validarse sobre una instancia MySQL temporal con datos representativos antes de producción. Las pruebas locales validan su contrato, pero este workspace no dispone de MySQL para ejecutar la migración real.

## Ciclo de vida

SIGTERM y SIGINT dejan de aceptar tráfico y cierran recursos. Rechazos no manejados, excepciones, errores de escucha, fallos de cierre y timeouts terminan con código distinto de cero sin registrar mensajes de excepción sensibles. Las conexiones activas sólo se fuerzan después del timeout acotado `SHUTDOWN_TIMEOUT_MS`.
