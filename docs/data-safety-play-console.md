# FitPlan MX - Data Safety para Play Console

Usa este documento como base para llenar Data Safety. Verifica cada respuesta en Play Console contra el comportamiento real de la app antes de enviar.

## Resumen

FitPlan MX maneja datos sensibles de salud, fitness, nutricion, ubicacion, fotos, cuenta y pagos. No declares "no se recopilan datos" si tienes Gemini, Google Sign-In, Billing o registros de salud/nutricion activos.

## Categorias probables

| Categoria Play | Aplica | Ejemplos en FitPlan MX | Finalidad |
|---|---:|---|---|
| Salud y fitness | Si | peso, glucosa, presion, rutinas, progreso, actividad | Funcionalidad de app, seguimiento personal |
| Fotos y videos | Si | foto de comida enviada a Gemini con consentimiento | Analisis de comida/macros |
| Ubicacion | Si | GPS aproximado/preciso para recorridos | Funcionalidad de tracking |
| Informacion personal | Si | nombre/correo si usa Google Sign-In | Cuenta/autenticacion |
| Compras | Si | suscripcion Premium via Google Play Billing | Monetizacion y beneficios Premium |
| Actividad de la app | Posible | uso de secciones, registros locales | Funcionalidad local |
| Datos financieros | Gestionado por Google Play | pagos de suscripcion | Procesado por Google Play Billing |

## Datos compartidos

Revisa y declara como compartidos/procesados por terceros cuando aplique:

- Gemini / Google AI: imagenes de comida, texto de prompts, datos necesarios para generar estimaciones o recomendaciones.
- Google Sign-In: datos de autenticacion de cuenta.
- Google Play Billing: compras y suscripciones.

## Seguridad

- [x] Backup automatico desactivado.
- [x] Reglas de backup/extraccion excluyen datos sensibles locales.
- [x] Consentimiento explicito antes de enviar imagen/datos de comida a Gemini.
- [ ] Politica de privacidad publica pendiente.
- [ ] Revisar cifrado en transito para llamadas externas.
- [ ] Validar que no se registren datos sensibles en logs de release.

## Respuestas recomendadas a revisar

- Los datos se usan para funcionalidad de la app.
- Los datos de salud/fitness se usan para seguimiento personal.
- Las fotos se usan solo si el usuario decide tomar foto y confirmar envio a Gemini.
- La ubicacion se usa solo cuando el usuario inicia tracking.
- La app no vende datos.
- La app no debe presentarse como dispositivo medico.

