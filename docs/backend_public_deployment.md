# Guía segura de despliegue público del backend

Esta guía describe el despliegue del backend Node.js/Express de FitPlan detrás de HTTPS y la configuración de Android para consumirlo. La autenticación vigente usa un ID token de Google verificado por el servidor y un JWT Bearer de corta duración. No se usan llaves estáticas compartidas entre la app y el backend.

> Estado de seguridad: la credencial estática incluida en una versión anterior de esta guía debe considerarse comprometida. Su revocación, la revisión de despliegues antiguos y cualquier limpieza coordinada del historial Git siguen siendo tareas operativas pendientes. No copies secretos reales en documentación, `.env.example` ni en la app Android.

## 1. Prerrequisitos

- Un proveedor que termine TLS con una URL HTTPS estable.
- MySQL accesible mediante TLS con verificación de identidad.
- Un cliente OAuth de Google cuyo audience coincida con `GOOGLE_OAUTH_CLIENT_ID`.
- Un almacén de secretos del proveedor para JWT, Gemini y la URL de base de datos.
- Un servicio con estado compartido de rate limiting mediante MySQL para producción.

Usa `.env.backend.example` únicamente como inventario de nombres. Los valores secretos deben inyectarse desde el secret manager del proveedor.

## 2. Crear el servicio

1. Conecta el repositorio al proveedor de hosting.
2. Selecciona un runtime Node.js compatible con el lockfile del proyecto.
3. Configura `npm ci` como instalación reproducible.
4. Configura `npm start` como comando de inicio.
5. Expón el puerto indicado por `PORT` únicamente a través del proxy HTTPS administrado.
6. Ejecuta las migraciones de MySQL antes de dirigir tráfico al nuevo release.

No publiques MySQL directamente en internet. Limita su acceso a la red privada o a las direcciones del servicio backend y usa una cuenta con los permisos mínimos requeridos.

## 3. Variables de entorno del backend

| Variable | Requisito de producción |
|---|---|
| `NODE_ENV` | Debe ser `production`. |
| `PORT` | Puerto entregado por el proveedor. |
| `JWT_SECRET` | Secreto aleatorio de al menos 32 bytes, almacenado sólo en el secret manager. |
| `JWT_ISSUER` | Identificador HTTPS estable del emisor. |
| `JWT_AUDIENCE` | Audience exclusivo del backend FitPlan. |
| `JWT_ALGORITHMS` | Lista cerrada; la configuración de referencia usa `HS256`. |
| `JWT_TTL_SECONDS` | Vigencia corta; la plantilla propone 900 segundos. |
| `GEMINI_API_KEY` | Secreto del proveedor Gemini, nunca incluido en Android. |
| `GEMINI_MODEL` | Modelo aprobado para el entorno. |
| `CORS_ALLOWED_ORIGINS` | Orígenes HTTPS exactos, separados según la sintaxis de la plantilla; nunca `*`. |
| `TRUST_PROXY_HOPS` | Número exacto de proxies confiables. Usa `1` sólo si existe un único proxy administrado. |
| `DATABASE_URL` | URL MySQL inyectada como secreto. |
| `DATABASE_TLS` | Debe ser `verify_identity`. |
| `RATE_LIMIT_STORE` | Debe ser `mysql` en producción. |
| `GOOGLE_OAUTH_CLIENT_ID` | Client ID web usado para verificar el audience del ID token. Es identificador público, no secreto. |

El proceso falla de forma cerrada si faltan variables obligatorias, hay placeholders, se intenta usar CORS abierto, se deshabilita la verificación TLS de MySQL o se selecciona un rate store local en producción.

## 4. Flujo de autenticación

1. Android inicia sesión con Google y obtiene un ID token para el client ID configurado.
2. La app envía el ID token por HTTPS a `POST /api/auth/google`.
3. El backend verifica firma, issuer, audience, expiración y subject con Google.
4. El backend entrega un JWT propio de corta duración.
5. Las rutas protegidas reciben `Authorization: Bearer <jwt>`.

No guardes `JWT_SECRET`, `GEMINI_API_KEY`, `DATABASE_URL` ni credenciales del proveedor en el `.env` de Android, `BuildConfig`, recursos, APK/AAB o capturas.

## 5. Configuración de Android

El archivo local `.env` de Android sólo debe contener configuración pública necesaria para compilar, por ejemplo:

```env
BACKEND_BASE_URL=https://api.example.com
GOOGLE_WEB_CLIENT_ID=public-client-id.apps.googleusercontent.com
```

- Usa una URL HTTPS sin barra final.
- Release mantiene cleartext deshabilitado.
- El tráfico HTTP local permitido por la configuración debug no debe trasladarse al manifest principal.
- No añadas una llave estática de backend: el cliente usa el JWT obtenido tras la verificación Google.

## 6. Controles del proxy y de MySQL

- Redirige HTTP a HTTPS antes de llegar a Node.js.
- Conserva un único límite de confianza conocido para `X-Forwarded-For` y `X-Forwarded-Proto`.
- No permitas que clientes alcancen directamente el puerto Node y falsifiquen headers del proxy.
- Usa certificados MySQL con cadena y hostname válidos; no aceptes modos equivalentes a `rejectUnauthorized: false`.
- Mantén las tablas del limiter disponibles para todas las réplicas del backend.
- Centraliza stdout JSON con retención y alertas, sin cuerpos, tokens, correos ni datos de salud.

## 7. Verificación previa a producción

Estas comprobaciones deben ejecutarse contra el release candidato; esta guía no afirma que ya se hayan completado:

- `/health` responde con información mínima y sin secretos.
- Una solicitud protegida sin Bearer válido recibe `401`.
- Un origen web no permitido es rechazado; las solicitudes nativas sin `Origin` conservan el flujo esperado.
- Los headers de Helmet y HSTS aparecen a través del endpoint HTTPS público.
- El límite pre-auth y el límite por usuario producen `429` y comparten estado entre réplicas.
- MySQL rechaza una cadena o identidad TLS inválida.
- El login Google rechaza issuer/audience/expiración incorrectos y entrega un JWT sólo para un ID token válido.
- Los logs permiten correlación por `requestId` sin exponer credenciales ni datos sensibles.
- APK y AAB no contienen secretos, URLs privadas ni certificados de firma debug.

## 8. Rotación e historial

La eliminación de un valor del working tree no lo revoca ni lo quita de commits anteriores. El propietario de la infraestructura debe:

1. Revocar o rotar la credencial histórica en todos los proveedores y despliegues.
2. Confirmar que ninguna versión antigua del backend sigue aceptando autenticación estática.
3. Revisar logs de uso del proveedor dentro del periodo de retención disponible.
4. Coordinar la limpieza del historial con el equipo antes de usar `git-filter-repo` o BFG.
5. Volver a escanear el repositorio completo y sus artefactos después de la rotación y de cualquier reescritura.

No reescribas historia compartida ni fuerces un push sin una ventana de coordinación y un plan para que todos los clones se sincronicen de nuevo.
