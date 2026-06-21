# FitPlan MX

FitPlan MX es una app Android de fitness, nutricion y bienestar preventivo para registrar habitos diarios, entrenamientos, progreso corporal, hidratacion, comida, metricas de salud y recomendaciones de apoyo con IA.

La app esta pensada como herramienta de seguimiento personal. No sustituye diagnostico medico, tratamiento, consulta profesional ni decisiones clinicas.

## Funciones principales

- Onboarding con objetivo, nivel, dias disponibles, lugar de entrenamiento y disciplina.
- Dashboard con plan del dia, racha, balance muscular y accesos rapidos.
- Registro de comidas, macros, hidratacion y notas.
- Estimacion opcional de comida por foto usando Gemini, con consentimiento explicito antes del envio.
- Rutinas y seguimiento de actividad con ubicacion GPS cuando el usuario lo permite.
- Registro preventivo de glucosa, presion, sueno, peso y bienestar.
- Reporte local en PDF para seguimiento personal.
- Login con Google cuando `GOOGLE_WEB_CLIENT_ID` esta configurado.
- Suscripcion Premium por Google Play Billing cuando `PREMIUM_SUBSCRIPTION_ID` existe en Play Console.

## Stack

- Kotlin
- Jetpack Compose
- Material 3
- Room
- DataStore / SharedPreferences
- WorkManager
- Google Credential Manager
- Google Play Billing
- Gemini API via HTTPS
- MapLibre + Play Services Location
- Robolectric, JUnit y Roborazzi para pruebas

## Capturas disponibles

Las capturas locales usadas durante QA estan en la raiz del proyecto:

- `fitplan_final.png`
- `fitplan_register.png`
- `fitplan_food_screen.png`
- `fitplan_after_wake.png`

## Requisitos

- Android Studio
- JDK 17
- Android SDK compatible con `compileSdk 36`
- Emulador o dispositivo fisico

## Instalacion local

1. Abre este directorio en Android Studio.
2. Crea un archivo `.env` en la raiz del proyecto.
3. Usa `.env.example` como plantilla.
4. Sincroniza Gradle.
5. Ejecuta la app en debug.

Comandos utiles:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
```

Para release firmado, consulta `docs/release-signing.md`.

## Variables de entorno

No subas claves reales al repositorio.

```env
GEMINI_API_KEY=MY_GEMINI_API_KEY
GOOGLE_WEB_CLIENT_ID=MY_GOOGLE_WEB_CLIENT_ID
PREMIUM_SUBSCRIPTION_ID=fitplan_premium_monthly
```

- `GEMINI_API_KEY`: habilita consejos preventivos y analisis de fotos de comida. Si no esta configurada, la app usa recomendaciones locales donde aplica.
- `GOOGLE_WEB_CLIENT_ID`: habilita Google Sign-In.
- `PREMIUM_SUBSCRIPTION_ID`: debe coincidir con un producto/subscripcion configurado en Play Console.

## Permisos Android

Permisos declarados en `AndroidManifest.xml`:

- `INTERNET`: llamadas a Gemini y servicios remotos configurados.
- `CAMERA`: captura opcional de foto de comida. La camara se declara como hardware no obligatorio.
- `ACCESS_FINE_LOCATION` y `ACCESS_COARSE_LOCATION`: seguimiento GPS de actividad cuando el usuario lo solicita.
- `POST_NOTIFICATIONS`: recordatorios locales de bienestar.

No se declaran permisos de foreground service en esta fase porque no hay un servicio real de tracking en segundo plano con notificacion persistente.

## Privacidad

FitPlan MX maneja datos sensibles: salud, nutricion, peso, glucosa, presion, ubicacion, fotos, login y pagos. Para reducir riesgo en esta fase:

- `android:allowBackup` esta desactivado.
- `backup_rules.xml` y `data_extraction_rules.xml` excluyen bases de datos, preferencias, archivos y almacenamiento externo.
- Las fotos de comida no se envian a Gemini sin consentimiento explicito.
- Los reportes PDF se generan localmente en cache y se comparten solo por accion del usuario.

Antes de publicar en Google Play se debe tener una politica de privacidad publica, no editable, accesible por URL y consistente con la seccion Data Safety.

## Disclaimer medico

FitPlan MX ofrece seguimiento de habitos y orientacion general. No reemplaza atencion medica profesional. Si el usuario tiene sintomas, dolor, embarazo, diabetes, hipertension, enfermedad cardiaca u otra condicion medica, debe consultar a un profesional de salud.

## Estado de publicacion

Estado actual: preparacion previa a Google Play.

Pendiente antes de produccion:

- Politica de privacidad publica.
- Formulario Data Safety completo.
- Declaracion de apps de salud si aplica en Play Console.
- Validacion de Google Sign-In en proyecto OAuth de produccion.
- Configuracion real de producto Premium en Play Console.
- Capturas finales por densidad/form factor.
- Firma de release y generacion de AAB.
- Pruebas en internal testing track.

Documentos de preparacion:

- `docs/privacy-policy.md`
- `docs/data-safety-play-console.md`
- `docs/health-declaration.md`
- `docs/play-store-listing.md`
- `docs/release-signing.md`

## Roadmap

- Cerrar checklist de Play Store.
- Preparar AAB firmado.
- Ejecutar pruebas internas.
- Pulir copy de tienda, capturas y video corto.
- Publicar primero en canal de testing interno/cerrado.
