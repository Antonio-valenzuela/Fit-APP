# FitPlan MX - estrategia de producto

## 1. Diagnostico critico de la idea

FitPlan MX es comercialmente viable para Mexico y LatAm porque combina tres habitos que el usuario promedio ya intenta controlar por separado: comida, ejercicio y salud metabolica basica. El riesgo principal no es tecnico; es de foco. Si el MVP intenta competir al mismo tiempo con MyFitnessPal, Strong, Apple Health, una app de diabetes y una app de coaching, se vuelve caro, lento y dificil de revisar en App Store.

La decision correcta para MVP es registrar datos reales, mostrar resumen claro y dar recomendaciones generales no medicas. Diagnostico, tratamiento, ajuste de medicamentos, interpretacion clinica o promesas de perdida de peso deben quedar fuera. Glucosa y presion arterial se pueden registrar, pero con lenguaje preventivo y disclaimer fuerte.

Riesgos:
- Legal/salud: valores de glucosa y presion no deben generar diagnosticos.
- Retencion: registrar comida es tedioso; se necesita busqueda rapida, favoritos y comidas repetidas.
- Costos: graficas e historial mal consultados pueden disparar lecturas en Firestore.
- App Store: suscripciones, eliminacion de cuenta, privacidad y claims de salud son zonas sensibles.
- Producto: principiantes necesitan simplicidad, no una app saturada de metricas.

## 2. Nombre recomendado

Nombre recomendado: **FitPlan MX** para MVP.

Alternativas comerciales:
- FitPlan Latino
- VivaFit MX
- PulsoFit
- NutriFit Diario

FitPlan MX comunica plan, fitness y mercado local. Para expansion LatAm se puede mantener la marca y cambiar el subtitulo.

## 3. Propuesta de valor

"Tu plan simple para comer mejor, entrenar y registrar tu progreso de salud en un solo lugar, en espanol y pensado para la vida diaria en Mexico."

Promesa aceptable: organizacion, seguimiento y claridad.

Promesas a evitar: curar diabetes, controlar hipertension, bajar X kilos garantizados, reemplazar nutriologo o medico.

## 4. Publico objetivo

Primario:
- Personas de 18 a 45 anos en Mexico/LatAm.
- Principiantes o intermedios tempranos.
- Buscan bajar grasa, ganar musculo o mantener peso.
- Quieren registrar comida, peso, entrenamiento y habitos sin complejidad clinica.

Secundario:
- Usuarios con glucosa o presion que quieren llevar bitacora personal.
- Entrenadores y nutriologos en una etapa futura.
- Gimnasios locales y programas corporativos de bienestar.

## 5. Stack tecnico

Desarrollo principal:
- Windows, Visual Studio Code, Flutter, Dart, Git y GitHub.

Mobile:
- Flutter con arquitectura Feature-First + Clean Architecture.
- GetX para navegacion/controladores en esta base existente.
- fl_chart para graficas.

Backend:
- Firebase Auth.
- Cloud Firestore.
- Firebase Storage.
- Firebase Analytics.
- Firebase Crashlytics.
- Firebase Cloud Messaging.

Pagos:
- MVP comercial: RevenueCat para abstraer App Store y Google Play.
- iOS puro: StoreKit 2 como base, normalmente gestionado por RevenueCat.

Publicacion iOS:
- macOS, Xcode, App Store Connect y Apple Developer Program.

Diseno:
- Figma.
- Tema oscuro fitness: negro, verde, blanco y grises.

## 6. Arquitectura

Patron: Feature-First + Clean Architecture.

Reglas:
- `presentation` contiene paginas, widgets y controllers.
- `domain` contiene entidades, repositorios abstractos y casos de uso.
- `data` contiene modelos, datasources y repositorios concretos.
- Solo `data` conoce Firebase.
- Widgets no importan Firebase.
- Controllers exponen estados: loading, success, empty, error.
- Los modelos convierten hacia entidades.

## 7. Estructura de carpetas

La estructura objetivo esta documentada en `docs/architecture.md`. La app ya vive en `fitplan_mx/` e incluye `android/`, `ios/`, `lib/`, `assets/`, `test/` y `docs/`.

## 8. Modulos

- Auth: registro, login, recuperacion, logout, Apple Sign In futuro.
- Onboarding: objetivo, cuerpo, salud, entrenamiento y disclaimer.
- Dashboard: resumen diario y ultimos registros.
- Nutrition: comidas, alimentos, favoritos, calculos y historial.
- Workout: rutina, ejercicios, series, reps, peso, historial.
- Health Tracking: glucosa, presion, agua, sueno y bienestar.
- Progress: peso, medidas, fotos opcionales, graficas y adherencia.
- Premium: paywall, beneficios, restaurar compras y control de acceso.
- Profile: datos, objetivos, privacidad y suscripcion.
- Settings: tema, notificaciones, terminos, disclaimer, exportar y eliminar cuenta.

## 9. Lista de pantallas

- WelcomePage
- LoginPage
- RegisterPage
- ForgotPasswordPage
- OnboardingGoalPage
- OnboardingBodyDataPage
- OnboardingHealthPage
- OnboardingTrainingPage
- DashboardPage
- NutritionPage
- AddMealPage
- FoodSearchPage
- WorkoutPage
- WorkoutDetailPage
- ExerciseDetailPage
- HealthPage
- AddGlucosePage
- AddBloodPressurePage
- AddWaterPage
- AddSleepPage
- ProgressPage
- AddProgressPage
- PremiumPage
- ProfilePage
- SettingsPage
- PrivacyPolicyPage
- HealthDisclaimerPage

## 10. Flujo completo del usuario

1. Usuario abre app.
2. Ve bienvenida y disclaimer general.
3. Crea cuenta o inicia sesion.
4. Completa onboarding: objetivo, cuerpo, entrenamiento y condiciones relevantes.
5. Acepta disclaimer de salud.
6. App calcula calorias y macros iniciales.
7. Entra al dashboard.
8. Registra comida, entrenamiento, peso, glucosa o presion.
9. Dashboard consulta datos del dia y ultimos registros.
10. Historial muestra rangos por fecha con paginacion.
11. Premium desbloquea historial completo, graficas avanzadas, PDF e integraciones futuras.
12. Perfil permite editar datos, exportar datos o eliminar cuenta.

## 11. Diseno UX/UI

Principios:
- Primer pantalla util, no landing ornamental.
- Bottom nav con Inicio, Comidas, Rutinas, Salud, Progreso y Perfil.
- Botones grandes para acciones frecuentes.
- Tarjetas compactas con datos reales.
- Graficas simples y legibles.
- Lenguaje preventivo, no clinico.
- Cada registro importante debe tener crear, listar, editar o eliminar segun el modulo.

## 12. Modelo freemium

Gratis:
- Auth.
- Onboarding.
- Dashboard basico.
- Registro basico de comidas.
- Rutina basica.
- Registro basico de peso.
- Registro limitado de glucosa y presion.
- Historial corto.

Premium:
- Historial completo.
- Graficas avanzadas.
- Reportes PDF.
- Recomendaciones inteligentes.
- Recordatorios avanzados.
- Planes personalizados.
- Comparativas semanales.
- Integracion futura con Apple Health.
- Sin anuncios.

## 13. Como vender la app

Canales:
- TikTok con recetas, rutinas cortas y casos de uso.
- Instagram Reels.
- Gimnasios locales.
- Nutriologos y entrenadores.
- App Store Optimization.
- Landing page.
- Referidos.

Clientes futuros:
- Usuarios individuales.
- Gimnasios.
- Entrenadores.
- Nutriologos.
- Empresas.
- Universidades.
- Programas de bienestar.

## 14. Roadmap

Fase 1: MVP funcional
- Auth, onboarding, dashboard, comidas, rutinas, peso, glucosa, presion e historial basico.

Fase 2: Comercial
- Premium, graficas, notificaciones, recetas, rutinas avanzadas y reportes.

Fase 3: Escalable
- Apple Health, wearables, IA, PDF, panel web y coaches.

Fase 4: Plataforma
- Gimnasios, nutriologos, empresas, marketplace, BigQuery y Cloud Functions.

