# FitPlan MX - Play Store checklist

Este checklist prepara la app para revision en Google Play. No reemplaza asesoria legal.

## Data Safety

- [ ] Declarar recoleccion/uso de datos de salud y fitness.
- [ ] Declarar datos de nutricion, peso, glucosa, presion, actividad y bienestar.
- [ ] Declarar ubicacion aproximada/precisa si se usa tracking GPS.
- [ ] Declarar fotos/imagenes cuando se usan para analisis de comida.
- [ ] Declarar datos de cuenta de Google si se usa login.
- [ ] Declarar pagos/compras gestionadas por Google Play Billing.
- [ ] Declarar si Gemini u otros terceros procesan imagenes o datos enviados por el usuario.
- [ ] Asegurar que Data Safety coincide con la politica de privacidad.

## Politica de privacidad

- [ ] Publicar URL publica, activa, no geobloqueada y no editable por usuarios.
- [ ] Nombrar FitPlan MX y la entidad/desarrollador responsable.
- [ ] Incluir contacto de privacidad.
- [ ] Explicar datos recolectados: salud, nutricion, peso, glucosa, presion, ubicacion, fotos, cuenta y pagos.
- [ ] Explicar finalidad de uso de cada dato.
- [ ] Explicar terceros: Gemini/Google AI, Google Sign-In, Google Play Billing.
- [ ] Explicar retencion y eliminacion de datos.
- [ ] Explicar seguridad y almacenamiento local.
- [ ] Incluir el mismo enlace dentro de la app o texto equivalente visible.

## Salud y disclaimer

- [ ] Completar declaracion de Health apps en Play Console si aplica.
- [ ] Mantener disclaimer visible: FitPlan no sustituye diagnostico medico.
- [ ] Evitar promesas de tratamiento, cura, diagnostico o resultados garantizados.
- [ ] Revisar textos de IA para no sugerir decisiones medicas basadas solo en la app.
- [ ] Confirmar que glucosa/presion se presentan como seguimiento personal.

## Permisos

- [x] `CAMERA` tiene `uses-feature android.hardware.camera required=false`.
- [x] No se declaran permisos de foreground service sin service real.
- [ ] Justificar `POST_NOTIFICATIONS` para recordatorios locales.
- [ ] Justificar `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION` para tracking de actividad.
- [ ] Confirmar que cada permiso se solicita en runtime antes de usarse.
- [ ] Preparar respuestas de permisos en Play Console si se requieren.

## Ubicacion

- [ ] Confirmar que GPS se usa solo cuando el usuario inicia tracking.
- [ ] Explicar en UX para que se usa la ubicacion.
- [ ] Evitar background location si no hay servicio persistente real.
- [ ] Si se agrega tracking en segundo plano futuro, declarar service, notificacion y foreground service type.

## Fotos y camara

- [x] La camara es opcional para estimar macros.
- [x] La foto se prepara localmente antes de cualquier envio.
- [x] El usuario debe confirmar envio a Gemini.
- [ ] Explicar en politica de privacidad que imagenes pueden procesarse por Gemini.
- [ ] Validar que no se guarden fotos sensibles fuera de cache sin necesidad.

## IA / Gemini

- [x] Existe fallback local cuando `GEMINI_API_KEY` no esta configurada para consejos preventivos.
- [x] El analisis de fotos requiere consentimiento explicito.
- [ ] Revisar prompts para no generar diagnosticos ni tratamientos.
- [ ] Documentar procesamiento por tercero en politica de privacidad.
- [ ] Confirmar manejo de errores, timeouts y respuestas no JSON.

## Pagos

- [ ] Configurar `PREMIUM_SUBSCRIPTION_ID` real en Play Console.
- [ ] Validar compra, restauracion y acknowledge en internal testing.
- [ ] Confirmar que beneficios Premium estan descritos claramente.
- [ ] Confirmar que no se desbloquean compras fuera del flujo permitido por Google Play.

## Capturas y listing

- [ ] Preparar icono final, feature graphic y capturas por formato.
- [ ] Escribir descripcion corta y larga de Play Store.
- [ ] Incluir disclaimer de salud en descripcion si corresponde.
- [ ] Evitar claims medicos o promesas no demostrables.
- [ ] Preparar video corto opcional.

## Testing interno

- [ ] Ejecutar `:app:testDebugUnitTest`.
- [ ] Ejecutar `:app:lintDebug`.
- [ ] Ejecutar `:app:assembleDebug`.
- [ ] Probar login Google con OAuth real.
- [ ] Probar compra Premium con tester de licencia.
- [ ] Probar captura de comida y consentimiento Gemini.
- [ ] Probar permisos denegados: camara, ubicacion y notificaciones.
- [ ] Probar low-end/performance mode.

## Firma y release

- [ ] Crear upload key segura.
- [ ] Configurar `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- [ ] Ejecutar `:app:bundleRelease`.
- [ ] Subir AAB a internal testing.
- [ ] Verificar versionCode/versionName antes de cada subida.

## Riesgos restantes

- [ ] Politica de privacidad aun pendiente.
- [ ] Data Safety aun pendiente.
- [ ] Declaracion de salud aun pendiente.
- [ ] Validacion real de OAuth, Billing y Gemini aun pendiente.
- [ ] Minify/ofuscacion queda como backlog hasta tener reglas ProGuard verificadas.
