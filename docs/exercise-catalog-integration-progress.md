# Progreso de integración del catálogo de ejercicios

Última actualización: 2026-07-16

Este registro separa evidencia ejecutada de trabajo pendiente. No se realizan
operaciones Git ni se modifica la base de datos de un dispositivo real.

## Estado de línea base disponible

### Objetivo

Obtener una referencia local antes de continuar con los bloques seguros y no
atribuir al catálogo fallos preexistentes.

### Evidencia ejecutada

| Comando | Resultado | Evidencia |
|---|---|---|
| `./gradlew.bat :app:testDebugUnitTest --no-daemon --console=plain` | APROBADO | 142 pruebas, 0 fallos, 0 errores, 0 omitidas; XML en `app/build/test-results/testDebugUnitTest/`; duración Gradle 1m56s. |
| `./gradlew.bat :app:lintDebug` | APROBADO con advertencias | 0 errores, 136 advertencias reportadas en el informe lint existente. |
| `./gradlew.bat :app:assembleDebug` | APROBADO | Artefacto debug generado antes de los últimos cambios de alias/triggers; requiere repetición para evidencia final del estado actual. |
| `./gradlew.bat :app:compileReleaseKotlin` | APROBADO | Compilación Kotlin release previa a los últimos cambios; requiere repetición si se necesita evidencia final de release. |
| `connectedDebugAndroidTest` | NO EJECUTABLE | `adb devices -l` no muestra dispositivos; no existe XML instrumentado nuevo. |

No se dispone de una copia extraída de la base v10 del Huawei para la prueba
de actualización sobre datos reales. La limitación es externa al código.

## Bloque Fase 1B — importador y alias

### Objetivo

Mantener el contrato determinista, nullable y sin multimedia, y completar el
manifiesto explícito de alias españoles sin traducir masivamente el dataset.

### Archivos modificados

* `tools/exercise-catalog-import/spanish-aliases.json`
* `app/src/main/assets/exercise_catalog/spanish_aliases.json`
* `app/src/main/java/com/example/data/model/ExerciseCatalogEntities.kt`
* `tools/exercise-catalog-import/import.test.mjs`
* `app/src/test/java/com/example/data/local/ExerciseCatalogImportPolicyTest.kt`

Alias mínimos cubiertos: `press de banca`, `peso muerto`, `sentadilla`,
`flexiones`, `lagartijas`, `elevaciones laterales`, `curl de bíceps`,
`dominadas`, `remo`, `zancadas`, `abdominales` y `plancha`.

### Riesgos y controles

* Los alias son referencias de búsqueda; no crean traducciones en los campos
  del catálogo.
* `flexiones` y `lagartijas` pueden apuntar al mismo canónico; el bootstrap
  concatena todos los alias del mismo canónico en el contenido external de FTS.
* `BODY_WEIGHT` sigue fuera de la tabla de requisitos físicos.

## Bloque Fase 2 — Room v11

### Objetivo

Preservar las 11 tablas existentes, crear el esquema v11 no destructivo,
mantener procedencia y preparar importación idempotente sin conectar todavía
la UI.

### Corrección aplicada

`MIGRATION_10_11` ahora incluye los cuatro triggers de sincronización FTS4
external-content que Room exporta en `app/schemas/.../11.json`. Son parte del
DDL generado por Room y no una segunda estrategia de búsqueda.

### Estado

* Entidades v11, DAO, esquema exportado, bootstrap transaccional e idempotencia:
  implementados.
* FTS escribe mediante `ExerciseSearchContentEntity`; la reconstrucción es solo
  mantenimiento.
* Migración en dispositivo Huawei: pendiente por ausencia de dispositivo y
  copia de base real.
* Consultas de casa mantienen rutas separadas para sin equipo y con equipo; la
  capa de dominio debe enrutar listas vacías a la primera ruta y nunca enlazar
  una consulta de equipo con una lista vacía.

## Bloque Fase 3A — repositorio y dominio aislados

### Objetivo

Exponer detalle, exploración, búsqueda, filtros, compatibilidad de ubicación y
fallback de idioma sin sustituir aún `TrainingCatalog`, las rutinas ni la UI.

### Archivos modificados

* `app/src/main/java/com/example/data/model/ExerciseCatalogDomain.kt`
* `app/src/main/java/com/example/data/repository/ExerciseCatalogRepository.kt`
* `app/src/main/java/com/example/di/ExerciseCatalogModule.kt`
* `app/src/test/java/com/example/data/repository/ExerciseCatalogRepositoryTest.kt`
* `app/src/main/java/com/example/data/local/ExerciseCatalogBootstrapper.kt`

El repositorio enruta casa sin equipo a una consulta separada; solo enlaza la
consulta con equipo cuando el conjunto disponible no está vacío. Sanitiza
texto FTS eliminando puntuación, normalizando acentos y usando términos de
prefijo. La selección de idioma prefiere español y usa inglés como respaldo,
sin copiar valores en el catálogo.

El binding Hilt queda disponible como singleton, pero no invoca el bootstrap ni
reemplaza consumidores actuales.

### Pruebas ejecutadas

| Comando | Resultado | Evidencia |
|---|---|---|
| `node --test tools/exercise-catalog-import/import.test.mjs` | APROBADO | 17/17, 0 fallos, 0 omitidas; 265.8 ms. |
| `./gradlew.bat :app:testDebugUnitTest --tests com.example.data.local.ExerciseCatalogImportPolicyTest ...` | APROBADO | 4/4, 0 fallos, 0 omitidas; 2m55s con compilación incremental. |
| `./gradlew.bat :app:testDebugUnitTest --tests com.example.data.repository.ExerciseCatalogRepositoryTest --no-configuration-cache --max-workers=1 ...` | APROBADO | 3/3, 0 fallos, 0 omitidas; 49s de ejecución Gradle. |
| `./gradlew.bat :app:compileDebugAndroidTestKotlin --no-configuration-cache --max-workers=1 ...` | APROBADO | Compilación instrumentada completa; 0 errores. Se observó una excepción AWT/KSP posterior al build, sin fallo de Gradle. |
| `./gradlew.bat :app:testDebugUnitTest --no-configuration-cache --max-workers=1 ...` | APROBADO | 145 pruebas, 0 fallos, 0 errores, 0 omitidas; Gradle 54s, tiempo agregado de suites 17.336s. XML agregado en `app/build/test-results/testDebugUnitTest/`. |
| `./gradlew.bat :app:assembleDebug --no-configuration-cache --max-workers=1 ...` | APROBADO | APK debug generado; 1m35s. |
| `./gradlew.bat :app:assembleRelease --no-configuration-cache --max-workers=1 ...` | APROBADO técnicamente, no publicable | R8 terminó y `mapping.txt` existe (75,430,936 bytes); APK generado es `app-release-unsigned.apk` (110,542,142 bytes). No se generó AAB. |
| `./gradlew.bat :app:bundleRelease --no-configuration-cache --max-workers=1 ...` | APROBADO técnicamente, no publicable | AAB generado en `app/build/outputs/bundle/release/app-release.aab` (53,167,253 bytes), pero `jarsigner -verify -strict` indica `jar is unsigned`. R8 terminó tras el timeout del proceso envolvente y el artefacto quedó disponible. |
| `./gradlew.bat :app:lintDebug --no-configuration-cache --max-workers=1 ...` | APROBADO con advertencias | 0 errores, 136 warnings; 3m28s. |
| `./gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache --max-workers=1 ...` | BLOQUEADO POR ENTORNO | Gradle terminó con `DeviceException: No connected devices!` después de compilar y empaquetar el APK de tests; no se generaron XML de ejecución. |

### Riesgos

* Los filtros combinados se aplican sobre la página devuelta por Room; antes de
  Paging/UI se debe decidir si se agregan consultas SQL combinadas para no
  filtrar después de paginar.
* La prueba sintética de migración/FTS ya pasa en Android 10/API 29. La prueba
  adicional sobre una copia v10 real del Huawei continúa pendiente.

## Actualización consolidada — Android 10, UI limitada y seguridad

Esta sección reemplaza las conclusiones operativas anteriores cuando exista
alguna contradicción. No se realizaron llamadas a Gemini.

### Clasificación de los seis fallos históricos

Fuente: `test-results/instrumented/connected-full-2026-07-15.xml`, AVD API 36,
36 pruebas, 29 aprobadas, 6 fallidas, 1 omitida, 0 errores, 248.796 s. La línea
base anterior ya documentaba las mismas clases antes de conectar la UI del
catálogo, aunque no se conservó un XML pre-catálogo por método.

| Prueba | Mensaje y stack relevante | Relación y causa raíz | Clasificación |
|---|---|---|---|
| `com.example.CompleteWorkoutInstrumentedTest#completesSavesShowsHistoryAndRejectsDuplicateSave` | `No node found ... TestTag = 'start_routine_hamstrings_glutes'`; `scrollToTag(...kt:117)`, método línea 77 | No toca Room/repositorio. El fixture omitía `Nivel`; el fallback `Principiante` excluía la rutina `Intermedio`. Se corrigió solo el dato explícito del escenario y pasa en API 29. | `prueba obsoleta` |
| `com.example.GeminiUiLifecycleInstrumentedTest#consentCanBeCancelledWithoutSendingAndRequestRemainsRetryable` | `Expected exactly '1' node ... TestTag = 'bottom_nav_nutrition'`; `launchNutritionDialog(...kt:61)` | Falla antes de entrar a Gemini. El fixture solo limpia preferencias y no fija `UserEntity.trainingDiscipline=GYM`; otra disciplina oculta Nutrición. No toca catálogo. | `fallo independiente` |
| `com.example.GeminiUiLifecycleInstrumentedTest#repeatedConsentCancellationNeverStartsARequest` | Mismo mensaje y stack en `launchNutritionDialog(...kt:61)` | Misma falta de aislamiento de disciplina; ninguna solicitud remota se inicia. | `fallo independiente` |
| `com.example.GeminiUiLifecycleInstrumentedTest#preparedConsentSurvivesRecreationAndBackgroundReturnWithoutSending` | Mismo mensaje y stack en `launchNutritionDialog(...kt:61)` | Misma falta de aislamiento de disciplina; no toca Room ni repositorio. | `fallo independiente` |
| `com.example.GpsPermissionsInstrumentedTest#a_deniedAndUserFixedPermissionRemainsRecoverable` | `El diálogo no expuso un botón de denegación identificable`; `denyPermissionDialog(...kt:176)`, método línea 68 | El helper depende de IDs/textos del permission controller de API 36 y además fija `user-fixed`; las otras tres pruebas GPS pasan. No toca catálogo. | `problema del emulador` |
| `com.example.HomeVsGymInstrumentedTest#gymProfileSelectsGymAndShowsGymRoutineEvenAtThirtyMinutes` | `No node found ... TestTag = 'routine_card_hamstrings_glutes'`; `scrollToRoutine(...kt:110)`, método línea 67 | Relevante para Home/Gym legado, no para Room. El fixture conservaba un objetivo sin `Nivel`; el fallback `Principiante` excluía la rutina `Intermedio`. El XML histórico reproduce el fallo y el XML consolidado post-corrección pasa en API 29; no se conservó XML focal API 36 post-corrección. | `prueba obsoleta` |

Ningún stack entra en `AppDatabase`, migración, bootstrap, repositorio o UI del
catálogo. No hay evidencia de regresión del catálogo en esos seis fallos.

### Validación Android 10 / API 29

Se creó `FitPlan_API_29` sin eliminar `Medium_Phone_API_36`. La ejecución
consolidada usa `emulator-5554`, Android 10/API 29, y se conserva en
`test-results/instrumented/api29-catalog-consolidated-2026-07-15.xml`.

Resultado consolidado: **10/10 aprobadas**, 0 fallos, 0 errores, 0 omitidas,
76.282 s. Incluye:

* migración 10→11 con las 11 tablas y datos centinela conservados;
* foreign keys, índices y los cuatro triggers FTS external-content;
* FTS insert/update/delete/rebuild;
* bootstrap inicial, cierre/reapertura de Room y segundo bootstrap `NO_OP`;
* rollback transaccional inducido;
* doce alias españoles, acentos y texto FTS malformado;
* casa sin equipo con peso corporal;
* casa con mancuernas;
* requisito acumulativo mancuerna+banco, excluido si falta el banco;
* gimnasio y filtro explícito `ANY`;
* Home/Gym con cierre y relanzamiento de actividad;
* smoke de UI con feature flag desactivada y activada;
* búsqueda `press de banca`, filtro casa, búsqueda `flexiones` y detalle;
* guardado/historial y prevención de duplicado en entrenamiento.

Después se instaló el APK debug, se ejecutó `am force-stop` y se reabrió la
actividad resuelta. La reapertura fue `Status: ok`, `LaunchState: COLD`, 98 ms;
el proceso y `MainActivity` quedaron activos y no aparecieron coincidencias de
crash/ANR. Esta medición usa instalación limpia y no se presenta como prueba de
migración ni de persistencia histórica. La transcripción se conserva en
`test-results/instrumented/api29-force-stop-2026-07-15.txt`.

### Home/Gym

La preferencia se persiste actualmente en `UserEntity.goal` como metadato
`Lugar: Casa|Gimnasio|Ambos`. Valores ausentes, antiguos o desconocidos se
normalizan a `Ambos`/`ANY`. Las reglas verificadas son:

* `HOME`: `HOME_COMPATIBLE` o `BOTH`, con todos los equipos requeridos;
* casa sin equipo incluye `BODY_WEIGHT`, que no es requisito físico;
* `GYM`: `GYM_COMPATIBLE` o `BOTH`;
* `ANY`: sin predicado de ubicación;
* la selección sobrevive al cierre y relanzamiento de la actividad.

### UI limitada

La entrada al catálogo depende de `feature_exercise_catalog_enabled`, que por
defecto está desactivada. Con flag inactiva, la entrada no se muestra. Con flag
activa están conectados únicamente catálogo, búsqueda, filtros básicos y
detalle. `TrainingCatalog` continúa como fallback y las rutinas existentes no
se reemplazan. No se conectó Gemini, no se inició Room v12 y los cinco registros
`REVIEW` no existen como canónicos activos ni aparecen en resultados.

### Seguridad independiente de producción

`BACKEND_API_KEY` está excluida del `BuildConfig` debug y release. No existen
lecturas de esa propiedad ni encabezados bearer en `GeminiService`. La frontera
`ControlledBackendAiProvider` no acepta credenciales del cliente; la
implementación predeterminada no abre red, usa FitCore local para consejos y
devuelve un fallo recuperable para fotos hasta disponer de autenticación segura
en un backend controlado.

Las pruebas focales de seguridad pasan: 3 de `GeminiServiceSecurityTest` y 2 de
`SecretScanningTest`. Los fallos de escaneo ya no imprimen el valor detectado.
`assembleRelease` y `bundleRelease` finalizan automáticamente con escaneo de su
artefacto, y `verifyReleaseArtifactsHaveNoSecrets` construye ambos. El escaneo
descomprime APK/AAB, revisa DEX/recursos en vistas de texto de 8 y 16 bits,
detecta el valor configurado y su Base64, además de patrones genéricos, y solo
reporta archivo/entrada/tipo. Ambos artefactos pasaron.

### Estado release

* APK: `app/build/outputs/apk/release/app-release-unsigned.apk`, 110,542,142 bytes.
* AAB: `app/build/outputs/bundle/release/app-release.aab`, 53,207,713 bytes.
* R8/lint vital: build aprobado, sin errores; `mapping.txt` existe y mide
  76,024,192 bytes.
* `apksigner`: APK no verifica (`Missing META-INF/MANIFEST.MF`).
* `jarsigner`: AAB reporta `jar is unsigned`.

### Decisiones independientes

**Catálogo: APTO PARA UI LIMITADA.** La decisión se basa exclusivamente en
Room v11, API 29, repositorio, Home/Gym y los smoke tests con flag activa e
inactiva. No autoriza reemplazar rutinas existentes ni conectar Gemini.

**Producción: NO APTO PARA PUBLICACIÓN.** La clave dejó de estar en el cliente,
pero los artefactos no tienen firma de producción, falta probar migración sobre
una copia v10 real del Huawei y no se ha repetido la regresión instrumentada
global después de los últimos cambios. Los fallos GPS/Gemini independientes no
bloquean el catálogo local, pero sí deben resolverse o aceptarse formalmente
antes de una publicación.

## Prompt maestro estático — Checkpoint Bloque 1 (2026-07-15)

### Alcance vigente

Este checkpoint inicia el plan sin Git, pruebas ni dispositivos. La evidencia
histórica se conserva, pero en este ciclo no se ejecutaron pruebas unitarias,
Node ni instrumentadas y no se usó ADB, AVD o Huawei. Un intento perteneciente
al plan anterior fue interrumpido y no se incorpora como evidencia nueva. Solo
se permiten revisión estática, lint y compilación.

### Línea base permitida

| Comando | Resultado | Evidencia |
|---|---|---|
| `./gradlew.bat :app:lintDebug --no-daemon --console=plain` | COMPILADO / LINT COMPLETADO | `BUILD SUCCESSFUL` en 3m23s; 0 errores y 128 warnings en `app/build/reports/lint-results-debug.xml` y `.html`. No ejecuta pruebas. |
| `./gradlew.bat :app:assembleDebug --no-daemon --console=plain` | COMPILADO | `BUILD SUCCESSFUL` en 29s; las 43 tareas estaban `UP-TO-DATE`. No constituye validación funcional. |

APK de línea base: `app/build/outputs/apk/debug/app-debug.apk`, 134,431,247
bytes, fecha 2026-07-15 15:45:15 -06:00, SHA-256
`56058a7e5226c19aa09cc4dc58ae58dac28165e4b410332a60972c6f1e52dcc6`.
No se instaló ni abrió.

### Inventario de build y funciones

* Build types: `debug` y `release`; no hay staging ni flavors.
* Debug solo define `DEBUG_HITBOXES=true`; release usa R8 y
  `DEBUG_HITBOXES=false`.
* Debug y release comparten `applicationId`; no existe `applicationIdSuffix`.
* Feature flag del catálogo: propiedad `exerciseCatalogEnabled`, persistida como
  `feature_exercise_catalog_enabled`, con default `false` para ambos build types.
  No hay override por build ni toggle de producción, por lo que el catálogo
  nuevo queda oculto en una instalación normal actual.
* `TrainingCatalog` sigue siendo la fuente de rutinas y fallback del catálogo.
* Google Play Billing existe; RevenueCat no existe. La ruta Premium puede abrirse
  desde Ajustes, pero no hay pantallas separadas para Coach avanzado, Reportes
  avanzados o Plan adaptativo: son texto comercial, no destinos implementados.

### Hallazgo crítico Premium

La decisión efectiva vive en `PremiumAccessPolicy` y
`FitPlanViewModel._isPremiumActive`. El estado actual no está aislado por build:

* `FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING = true` vive en `main`;
* `isPremiumActive` devuelve `forceUnlock || isDebugBuild || storedPremium`;
* por tanto, el bypass también concede Premium al simular release;
* el test existente incluso codifica como esperado ese comportamiento inseguro;
* los callbacks de Billing comparten `premium_unlocked`, pero no pueden revocar
  visualmente el acceso mientras el override global siga activo.

Esto es un bloqueo del Bloque 2 y de release. La corrección deberá usar una sola
fuente de entitlement y un `BuildConfig.PREMIUM_TEST_MODE` `true` solo en debug y
forzosamente `false` en release, sin escribir compras falsas ni reemplazar
Billing.

### Catálogo, Room y Casa/Gimnasio

Revisión estática confirmada:

* `AppDatabase` sigue en v11, `exportSchema=true`, con las siete entidades del
  catálogo y la cadena de migraciones hasta 10→11.
* No aparece `fallbackToDestructiveMigration`.
* `MIGRATION_10_11` contiene content/FTS4 y cuatro triggers.
* Bootstrap es transaccional y fail-closed ante versión/hash/conteo distinto.
* Solo `ACTIVE` crea canónicos; `MERGED` referencia canónicos y los cinco
  `REVIEW` permanecen con `catalogId=null`.
* El workout continúa usando los IDs originales de los 27 ejercicios y nueve
  rutinas de `TrainingCatalog`.
* HOME/GYM/ANY y la bifurcación HOME sin equipo/con equipo existen en DAO y
  repositorio.

Riesgos encontrados para Bloques 3/4:

1. Los 27 built-ins no reciben filas de requisitos al bootstrap. Como HOME sin
   equipo solo comprueba `exercise_equipment_requirements`, puede incluir
   ejercicios internos de máquina o banco.
2. La UI HOME solo ofrece mancuernas y banco; no representa todos los equipos
   normalizados del catálogo.
3. El repositorio pagina antes de ciertos filtros en memoria e ignora músculos
   secundarios/requisitos en algunos caminos, pudiendo producir páginas vacías
   o falsos negativos.
4. `Lugar` persiste dentro de `UserEntity.goal`, pero la selección manual de
   equipo del workout y catálogo solo vive en memoria. El fragmento `Equipo:`
   guardado por onboarding no tiene consumidor efectivo y puede quedar obsoleto.

### IA, red y seguridad observada

* `GeminiService()` usa por defecto `DisabledControlledBackendAiProvider` y no
  tiene transporte remoto de producción.
* Consejos FitCore son reglas locales; OCR y preparación de foto son locales.
* Identificación/macros desde foto no funcionan sin backend y devuelven error
  recuperable por código.
* El backend conserva defaults inseguros conocidos para `BACKEND_API_KEY` y
  `JWT_SECRET` si faltan variables. Es un riesgo crítico de despliegue que el
  escáner Android actual no detecta.
* Secrets Gradle solo excluye `BACKEND_API_KEY`; una futura `GEMINI_API_KEY` o
  `JWT_SECRET` colocada en la `.env` compartida podría entrar a BuildConfig.
* El escáner binario actual cubre release APK/AAB, pero todavía no puede
  ejecutarse manualmente sobre debug como exige el Bloque 7.

### Pruebas existentes inspeccionadas, no ejecutadas

Se revisaron las suites unitarias e instrumentadas y la evidencia histórica.
Los XML previos continúan siendo históricos; no se actualizan sus decisiones en
este ciclo. Siguen sin existir XML posteriores que cierren la suite global para
los tres casos Gemini y el caso GPS. Ninguna afirmación de este checkpoint debe
interpretarse como prueba runtime.

### Archivos modificados en el Bloque 1

* `docs/exercise-catalog-integration-progress.md`: agregado este checkpoint.
* Salidas generadas por Gradle bajo `app/build/`: informes lint y confirmación
  del APK debug. No se modificó código de producción.

### Siguiente bloque

Bloque 2: reemplazar el override Premium global por entitlement central con
`PREMIUM_TEST_MODE=true` en debug y `false` en release, conservar Billing real,
añadir las pruebas requeridas sin ejecutarlas y hacer revisión estática de
nulabilidad, persistencia y rutas. No se continúa hasta el siguiente turno.

## Prompt maestro estático — Checkpoint Bloque 2 (2026-07-15)

### Resultado y prioridad de seguridad

Se eliminó `FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING`. La bandera estaba declarada
como constante global `true` en
`app/src/main/java/com/example/data/model/PremiumAccessPolicy.kt`, dentro del
source set `main`; además, la decisión anterior concedía acceso por
`forceUnlock || isDebugBuild || storedPremium`. Por ello el mismo código podía
desbloquear Premium fuera de una variante QA y el test anterior exigía ese
comportamiento inseguro.

La sustitución aplica dos defensas:

* `app/build.gradle.kts` genera `PREMIUM_TEST_MODE=true` en `debug`;
* el mismo archivo genera `PREMIUM_TEST_MODE=false` explícito en `release`;
* la fuente central solo activa QA mediante
  `BuildConfig.DEBUG && BuildConfig.PREMIUM_TEST_MODE`.

Aunque una edición futura configurara erróneamente la segunda bandera, una
release mantiene `BuildConfig.DEBUG=false` y no puede activar el bypass por esa
ruta. No se generó release en este bloque; la comprobación del artefacto release
minificado sigue reservada para los Bloques 7/8.

### Fuente de entitlement y persistencia

`PremiumEntitlementProvider` es ahora la regla pura y única:

```text
Premium efectivo = Premium QA de la variante OR entitlement real de Billing
```

`PremiumAccessPolicy` contiene la instancia configurada por build y es el punto
usado por `FitPlanViewModel`, `GymViewModel`, Dashboard y Nutrición. El bypass
solo cambia el resultado de acceso en memoria. No escribe preferencias, Room,
compras, recibos, productos ni historial.

Se retiraron `patchPremiumAccess`, `storedPremiumFromLocalPatch` y el botón de
Developer Settings que compartía la clave `premium_unlocked` con Billing. Ahora:

* `prefs.setPremiumUnlocked(enabled)` solo se llama desde
  `setPremiumEntitlementFromBilling`;
* `UserEntity.isPremium` refleja `enabled`, el entitlement real, no el acceso QA;
* onboarding también guarda únicamente `prefs.isPremiumUnlocked()`;
* purgar datos deja el entitlement real en `false`, pero debug recupera acceso
  QA por configuración al recrear el ViewModel;
* una release decide entre Free/Premium usando solo el valor real persistido.

Google Play Billing no se eliminó ni se reemplazó. En modo QA la pantalla no
llama `billingManager.start`, no muestra el botón de compra y explica que no
existe compra o suscripción. Fuera de QA conserva el flujo real anterior.

### UI y funciones Premium revisadas por código

* La ruta explícita `premium` sigue registrada y Ajustes conserva
  `settings_open_premium`; no existe un redirect nuevo al paywall.
* `PremiumScreen` muestra el indicador `Premium de prueba (QA)` únicamente
  cuando la fuente central, protegida por `BuildConfig.DEBUG`, está activa.
* La pantalla del paywall continúa abriendo explícitamente para revisar su UI.
* El gate real encontrado, escaneo de comida, usa la fuente central. En debug
  permite abrir el flujo aunque la feature remota siga desactivada; el proveedor
  remoto predeterminado continúa fallando de forma recuperable, conserva los
  datos y mantiene la estimación local/FitCore cuando aplica.
* Coach, reportes avanzados y plan adaptativo siguen siendo texto comercial o
  widgets no renderizados, no destinos funcionales separados. No se inventaron
  pantallas Premium nuevas dentro de este bloque.
* Progreso, mapa muscular, entrenamiento, perfil y bottom bar consumen el estado
  efectivo o lo usan visualmente; no se encontró un segundo escritor de
  entitlement ni una mutación de rutinas o historial desde el bypass.

Todo lo anterior fue revisado estáticamente. No fue validado en runtime.

### Pruebas agregadas o corregidas — no ejecutadas

Se reescribió `PremiumAccessPolicyTest` para cubrir:

* debug QA concede acceso sin entitlement real;
* release no concede acceso sin proveedor real;
* entitlement real sigue funcionando con QA desactivado;
* una instancia nueva tras reinicio conserva QA sin persistencia falsa;
* decisión offline;
* función Premium remota desactivada en QA;
* producción exige entitlement y feature disponible.

Se agregó `PremiumBuildTypeContractTest`, que falla si:

* debug deja de declarar `PREMIUM_TEST_MODE=true`;
* release deja de declarar `PREMIUM_TEST_MODE=false`;
* se elimina la defensa `BuildConfig.DEBUG && PREMIUM_TEST_MODE`;
* reaparece `FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING` bajo `src/main`;
* reaparece el patch local o más de un escritor ViewModel de
  `premium_unlocked`.

`PhysicalScreenTraversalInstrumentedTest` ahora exige la pantalla Premium, el
indicador QA y ausencia del botón de compra en el recorrido explícito desde
Ajustes. Las pruebas existentes de `GeminiServiceSecurityTest` ya expresan el
fallo recuperable y FitCore local.

Pruebas ejecutadas en este bloque: **0**. No se ejecutaron ni compilaron tareas
de test, no se generaron XML nuevos y no se usó ADB, AVD, Huawei ni otro
dispositivo. Los archivos de prueba quedaron solo revisados por lectura, como
exige el plan vigente.

### Compilación permitida

| Comando | Resultado | Alcance |
|---|---|---|
| `./gradlew.bat :app:assembleDebug --no-daemon --console=plain` | `BUILD SUCCESSFUL` en 2m20s; 43 tareas, 6 ejecutadas y 37 up-to-date | Compilación de producción debug; no ejecuta pruebas. |

APK resultante: `app/build/outputs/apk/debug/app-debug.apk`, 134,431,369 bytes,
fecha 2026-07-15 21:16:12 -06:00, SHA-256
`dad3e750aff24a91b311964babb0e25d47549f5a8629685e8768f3f869062c26`.
No se instaló ni abrió.

Persisten warnings conocidos de targets de anotación Kotlin y un icono
deprecated. Después de `BUILD SUCCESSFUL`, KSP/AWT imprimió una excepción tardía
al cerrar (`ApplicationManager.getApplication() == null`); el proceso terminó
con código 0 y escribió el APK. Se registra como warning de tooling, no como
prueba funcional ni como fallo de compilación.

### Diagnóstico backend fail-closed — sin implementación

Se revisó en modo solo lectura el fallback solicitado. El riesgo es **crítico**
si el backend es público:

* si falta o está vacía `BACKEND_API_KEY`, el middleware acepta un default
  estático versionado;
* si falta o está vacío `JWT_SECRET`, verifica con otro default versionado;
* ambas rutas fallan abiertas de forma independiente y configurar solo una no
  corrige la otra;
* el servidor arranca y `/health` permanece verde porque la lectura insegura
  ocurre al procesar peticiones;
* 14 rutas de health, muscles y Gemini quedan detrás de este middleware;
* el acceso de benchmark depende de un identificador administrativo fijo en las
  claims, no del mecanismo de autenticación.

No se imprimieron los valores de las credenciales. El cambio mínimo de código es
pequeño: retirar los dos defaults, validar configuración antes de montar rutas o
escuchar, rechazar vacíos/placeholders y distinguir el tipo de autenticación.
Se estiman unas 16–28 líneas de runtime en `middleware/auth.js` e `index.js`.
Incluyendo pruebas, endurecimiento del guard de benchmark y documentación, el
alcance recomendado es medio: 6–9 archivos y aproximadamente 100–180 líneas.
No requiere Room, Gradle ni cambios Android.

El rollout sí toca infraestructura/configuración: hay que decidir si JWT tiene
un emisor legítimo, provisionar y rotar secretos fuera del repositorio, aceptar
que el proceso no arranque sin ellos y actualizar runners de benchmark. Además,
`JWT_SECRET` no debe añadirse a la `.env` compartida que consume Secrets Gradle
sin separarla o excluirla del `BuildConfig`. Por requerir provisión/rotación de
infraestructura, no se implementó nada del backend en este bloque.

### Archivos modificados

Premium/build:

* `app/build.gradle.kts`
* `app/src/main/java/com/example/data/model/PremiumAccessPolicy.kt`
* `app/src/main/java/com/example/ui/viewmodel/FitPlanViewModel.kt`
* `app/src/main/java/com/example/features/gym/ui/GymViewModel.kt`
* `app/src/main/java/com/example/ui/screens/NutritionScreen.kt`
* `app/src/main/java/com/example/ui/screens/DeveloperSettingsScreen.kt`
* `app/src/main/java/com/example/ui/screens/PremiumScreen.kt`

Pruebas no ejecutadas:

* `app/src/test/java/com/example/data/model/PremiumAccessPolicyTest.kt`
* `app/src/test/java/com/example/data/model/PremiumBuildTypeContractTest.kt`
* `app/src/androidTest/java/com/example/PhysicalScreenTraversalInstrumentedTest.kt`

Documentación:

* `docs/exercise-catalog-integration-progress.md`

No se modificaron Room, catálogo, DAO, repositorios de catálogo, rutinas,
entidades, migraciones, backend ni infraestructura.

### Riesgos y siguiente bloque

* Las pruebas nuevas no tienen evidencia de ejecución ni compilación de sus
  source sets; deben ejecutarse en un ciclo que lo autorice.
* No existe evidencia de artefacto release posterior a este cambio; la garantía
  actual es estructural por configuración y doble condición. El escaneo del APK
  y AAB release corresponde a Bloques 7/8.
* La app debug está compilada, pero Premium QA sigue pendiente de validación
  manual por Yahir en el celular.
* El backend público continúa fail-open hasta autorizar y coordinar su cambio de
  infraestructura.

Siguiente bloque previsto: Bloque 3, activación del catálogo Room v11 por build
type. **No se inicia dentro de este checkpoint.**

## Prompt maestro estático — Checkpoint Bloque 3 (2026-07-16)

### Decisión del bloque

**Bloque 3 implementado y compilado para debug. Bloque 4 no iniciado.** La
evidencia de este checkpoint es de revisión de fuente, compilación de producción
debug e inspección del APK. No es evidencia de ejecución en Android.

### Feature flag y artefacto real

La activación del catálogo quedó protegida por tipo de build:

* `EXERCISE_CATALOG_ENABLED=true` en `debug`;
* `EXERCISE_CATALOG_ENABLED=false` en `release`;
* `ExerciseCatalogFeaturePolicy` exige que el build lo permita antes de aplicar
  un override local. Una preferencia antigua en `true` no puede activar una
  release;
* en debug, la ausencia de preferencia activa el catálogo por defecto y el
  override local permite hacer smoke de flag encendida/apagada.

La comprobación no se limitó al Gradle: `apkanalyzer dex code --class
com.example.BuildConfig` sobre el APK final mostró `BUILD_TYPE="debug"` y
`EXERCISE_CATALOG_ENABLED:Z = true`. El mismo APK contiene las tres assets de
`assets/exercise_catalog/` y los tags de pantalla/filtros inspeccionados en DEX.
No se generó release en este bloque, por lo que su valor `false` tiene evidencia
estructural, no binaria.

### Catálogo, búsqueda, filtros y detalle

Se conectaron únicamente las superficies permitidas: pantalla de catálogo,
búsqueda, filtros básicos y detalle. `TrainingCatalog` sigue siendo fallback;
no se reemplazaron rutinas, no se conectó Gemini y Room permanece en v11 sin
`fallbackToDestructiveMigration`.

Cambios principales:

* búsqueda FTS en español/inglés y alias frecuentes conservada;
* filtros por músculo primario o secundario, región corporal y equipo primario
  o requerido aplicados en SQL antes de `LIMIT` en las ocho rutas;
* HOME/GYM/ANY conserva las consultas separadas HOME sin equipo/con equipo;
* orden estable por `normalizedName, catalogId`;
* la UI solicita hasta 2,000 filas para que los 1,310 canónicos importables de
  esta versión no queden truncados a la primera página de 40;
* “Limpiar filtros” restablece ubicación a ANY, músculo, región, equipo y equipo
  disponible;
* detalle con fallback de idioma, instrucciones, músculos, región, equipo,
  requisitos físicos, compatibilidad y placeholder legal propio sin multimedia;
* no se muestran IDs técnicos ni estados de importación.

El fallback ante fallo de bootstrap/Room captura el error, registra solo su tipo
y usa los 27 ejercicios de `TrainingCatalog` sin cerrar la app. Ahora también
respeta búsqueda, alias frecuentes, músculo, región, equipo, ubicación y
requisitos acumulativos: “banco y mancuernas” exige ambos. El detalle legacy ya
no afirma falsamente que no hay requisitos y muestra compatibilidad legible.

### ACTIVE, MERGED y REVIEW

Lectura directa y en memoria del asset compacto final:

| Estado | Registros | Tratamiento |
|---|---:|---|
| `ACTIVE` | 1,310 | Propuestos como canónicos activos por bootstrap. |
| `MERGED` | 9 | Solo mapping a referencia canónica; no fila duplicada. |
| `REVIEW` | 5 | Sin canónico activo ni aparición en búsqueda/UI. |

Los cinco REVIEW son `0697`, `0716`, `1403`, `1496` y `1766`. El bootstrap
filtra explícitamente `ACTIVE` al crear canónicos; `MERGED` solo usa la
referencia propuesta. El manifest mantiene 1,324 registros y checksum
`9a075a3bec6d196e8de3485f3e1152a94f3b05b7792ea68eab893f5e1d60297d`.

### Pruebas fuente y revisiones — no ejecutadas

Se agregaron o ajustaron contratos para:

* flag debug/release y hard gate de preferencia;
* paso de filtros al SQL antes de paginación;
* fallback de idioma;
* requisitos acumulativos y filtros del fallback;
* alias español `flexiones` en fallback;
* límite UI suficiente para el catálogo actual;
* smoke instrumentado mediante `testTag` para flag apagada/encendida, filtros,
  búsqueda, detalle, placeholder y reapertura.

Dos revisiones independientes de fuente terminaron en PASS después de corregir
cuatro brechas: clear no restablecía ubicación, filtros fallback incompletos,
catálogo truncado a 40 y requisitos/compatibilidad engañosos en detalle legacy.

Pruebas ejecutadas: **0**. No se ejecutaron ni compilaron tareas de unit test o
androidTest, no se generaron XML nuevos y no se usó ADB, AVD, Huawei ni otro
dispositivo. Por la restricción expresa, no existe observación RED/GREEN ni
evidencia runtime de estas pruebas en este checkpoint.

### Compilación debug permitida

| Comando final | Resultado | Alcance |
|---|---|---|
| `./gradlew.bat :app:assembleDebug --no-daemon --console=plain` | `BUILD SUCCESSFUL` en 2m22s; 43 tareas, 8 ejecutadas y 35 up-to-date | Código de producción debug; no compila ni ejecuta tests. |

APK final: `app/build/outputs/apk/debug/app-debug.apk`, 134,431,369 bytes
(128.20 MiB), fecha 2026-07-16 12:57:30 -06:00, SHA-256
`37994BC2A430801AD173BB2E6A7B39473465DD80D0148FE3BD9871DA33210F62`.
No se instaló ni abrió.

Persisten warnings ya conocidos: targets futuros de anotaciones Kotlin, icono
`DirectionsRun` deprecated y Moshi Kapt deprecated. Después de `BUILD
SUCCESSFUL`, KSP/AWT volvió a imprimir una excepción tardía al cerrar; el
proceso terminó con código 0 y el APK final fue escrito e inspeccionado. Se
registra como warning de tooling, no como prueba funcional.

### Archivos del Bloque 3

Producción/configuración:

* `app/build.gradle.kts`
* `app/src/main/java/com/example/data/model/FeatureSettings.kt`
* `app/src/main/java/com/example/data/local/PreferencesManager.kt`
* `app/src/main/java/com/example/data/local/ExerciseCatalogDao.kt`
* `app/src/main/java/com/example/data/repository/ExerciseCatalogRepository.kt`
* `app/src/main/java/com/example/ui/catalog/ExerciseCatalogViewModel.kt`
* `app/src/main/java/com/example/ui/catalog/ExerciseCatalogScreen.kt`

Pruebas fuente no ejecutadas:

* `app/src/test/java/com/example/data/model/ExerciseCatalogFeaturePolicyTest.kt`
* `app/src/test/java/com/example/data/model/ExerciseCatalogFeatureContractTest.kt`
* `app/src/test/java/com/example/data/repository/ExerciseCatalogRepositoryTest.kt`
* `app/src/test/java/com/example/ui/catalog/TrainingCatalogFallbackPolicyTest.kt`
* `app/src/androidTest/java/com/example/ExerciseCatalogUiInstrumentedTest.kt`

Documentación: `docs/exercise-catalog-integration-progress.md`.

### Riesgos pendientes y parada

* La compilación confirma el código de producción, pero las fuentes de prueba
  siguen sin compilarse ni ejecutarse por restricción.
* Falta validar visualmente carga, scroll, estados y detalle en Android real.
* Persistencia y semántica completa Casa/Gimnasio/equipo pertenecen al Bloque 4.
* No existe todavía evidencia binaria de release con la flag apagada.

Siguiente bloque previsto: Bloque 4. **No se inicia dentro de este checkpoint.**

## Prompt maestro estático — Checkpoint Bloque 4 (2026-07-16)

### Decisión y alcance

**Checkpoint del Bloque 4 cerrado en el alcance permitido. Bloque 5 no
iniciado.** Se corrigieron por código las reglas Casa/Gimnasio/equipo, la
persistencia de `Lugar` y la carrera de carga inicial. La evidencia de este
checkpoint es revisión de fuente, contratos de prueba no ejecutados,
compilación de producción debug e inspección del APK; no es evidencia de
ejecución en Android.

No se usaron Git, ADB, AVD, Huawei, red ni Gemini. No se ejecutó ni compiló
ningún source set de pruebas. Room permanece en versión 11, sin migración nueva
y sin `fallbackToDestructiveMigration`.

### Causa del hallazgo HOME sin equipo

Los 27 ejercicios internos ya existían como canónicos Room y mappings
`FITPLAN_BUILT_IN`, pero no tenían una política auditable que distinguiera
alternativas de requisitos acumulativos. Las consultas HOME interpretaban la
ausencia de filas de requisitos como “no necesita equipo”, de modo que HOME sin
equipo admitía los 27, incluidas `leg_curl` y `leg_extension` de máquina.

Se agregó un registro Kotlin inmutable para exactamente los mismos 27 IDs, con:

* equipo principal;
* alternativas `anyOf` (OR);
* requisitos `allOf` (todos necesarios);
* compatibilidad `BOTH` para los 27; el equipo decide su viabilidad en casa.

`BODY_WEIGHT` nunca se inserta como requisito físico. Solo `allOf` genera filas
en `exercise_equipment_requirements`; las alternativas no se falsean como
requisitos acumulativos.

Resultados esperados por el registro y los contratos fuente:

| Selección | Built-ins compatibles |
|---|---:|
| Casa sin equipo | 7 |
| Casa con mancuernas | 17 |
| Casa con mancuernas y banco | 19 |
| Gimnasio | 27 |
| Ambos/ANY | 27 |

Los siete sin equipo son `squat`, `lunges`, `push_up`, `plank`, `crunch`,
`leg_raise` y `russian_twist`. `bulgarian_squat` no entra con solo mancuerna y
sí con mancuerna+banco; `hip_thrust` exige barra+banco. Estos conteos fueron
revisados estáticamente y quedaron protegidos por pruebas fuente, pero no se
observaron en runtime en este bloque.

### Consultas, filtros y fallback

Las cuatro rutas HOME distinguen built-ins mediante su mapping de procedencia:

* HOME sin equipo permite built-ins solo si el registro los declara
  compatibles, y para externos exige `BODY_WEIGHT` sin requisitos;
* HOME con equipo permite built-ins según el mismo registro, y para externos
  conserva la regla acumulativa `NOT EXISTS ... NOT IN`;
* la lista de equipo disponible nunca se enlaza vacía a la consulta con
  `NOT IN`.

Las ocho rutas `browse/search` de ANY, HOME sin/con equipo y GYM también
respetan alternativas OR de los built-ins al filtrar por equipo. Por ejemplo,
`flyes` puede encontrarse por `CABLE` y `bench_press` por `DUMBBELL`, aunque el
equipo alternativo no se almacene como requisito acumulativo. Un sentinel
imposible y explícito evita cualquier `IN` vacío.

El bootstrap usa el registro tanto en IMPORT como en NO_OP. En NO_OP reconcilia
de forma idempotente equipo principal, compatibilidad y requisitos seguros sin
cambiar manifest, versión, IDs ni esquema. `TrainingCatalog` permanece como
fallback y ahora consulta exactamente el mismo registro; se eliminó su
heurística divergente basada en analizar textos como “y” u “o”.

Lectura directa del asset fijado: 319 registros ACTIVE tienen equipo
`BODY_WEIGHT` y ninguno contiene `equipment_requirements`; por tanto, la regla
que excluye requisitos para peso corporal no oculta requisitos adicionales en
este dataset.

### Preferencia Lugar y persistencia

Se conservó el formato existente dentro de `UserEntity.goal`:

```text
Lugar: Casa
Lugar: Gimnasio
Lugar: Ambos
```

El lector y escritor comparten normalización: valores ausentes, vacíos,
heredados o desconocidos se convierten en `Ambos`; la escritura elimina
duplicados `Lugar` sin perder nivel, días, meta ni restricciones.

Cambios funcionales:

* “Editar perfil” en Ajustes abre el editor real de Casa/Gimnasio/Ambos;
* “Actualizar mis datos” en Perfil navega a Ajustes y ya no reinicia onboarding
  con valores predeterminados;
* el guardado de Ajustes lee el usuario Room actual, aplica `copy`, conserva
  `uid`, email, entitlement real, consentimiento, disciplina y actividad;
* si cambian edad, peso, altura o sexo, recalcula objetivos nutricionales; si
  solo cambia Lugar, conserva los objetivos existentes;
* el diálogo permanece en estado “Guardando…” y evita doble submit/dismiss;
  solo cierra y confirma éxito después de que `repository.saveUser` termina;
* errores mantienen el diálogo abierto y muestran un mensaje recuperable;
* la pantalla de catálogo recibe `Lugar` nullable durante carga fría y no fija
  un `Ambos` transitorio antes de que Room entregue el usuario real.

### Contratos fuente y revisiones — no ejecutados

Se agregaron o ampliaron contratos para:

* registro exacto de 27 IDs, conjuntos 7/17/19 y OR/ALL;
* `BODY_WEIGHT` sin requisito y `pull_up=PULL_UP_BAR`;
* las ocho rutas browse/search, filtros por alternativas y sentinel;
* HOME sin equipo excluyendo máquinas, HOME con equipo, GYM y ANY;
* reparación built-in durante NO_OP;
* fallback usando el mismo registro;
* normalización, duplicados y transición Casa→Gimnasio→Casa;
* carga fría y reapertura;
* recorrido Perfil→Ajustes→Editar→Casa→Guardar, lectura de Room, conservación
  de campos centinela, recreación de Activity y catálogo nuevamente en HOME.

Pruebas ejecutadas: **0**. Pruebas compiladas: **0**. No hay XML nuevo,
dispositivo ni duración de suite. La restricción impide presentar estos
contratos como aprobados en ejecución.

La primera revisión de especificación encontró que el filtro por equipo no
incluía alternativas OR; se corrigieron las ocho consultas y la repetición dio
PASS. La primera revisión de calidad encontró que Ajustes confirmaba antes del
guardado asíncrono; se implementó la ruta dedicada y la repetición dio PASS.
Ambos PASS son exclusivamente estáticos.

### Compilación debug y APK recalculado

| Comando | Resultado | Alcance |
|---|---|---|
| `./gradlew.bat :app:assembleDebug --no-daemon --console=plain` | `BUILD SUCCESSFUL` en 4m31s; 43 tareas, 8 ejecutadas y 35 up-to-date | Producción debug; no compila ni ejecuta tests. |

APK escrito y medido directamente después del build:

```text
Ruta: C:\Users\yahir\Desktop\FIT-APP MAS ACTUALIDADA\app\build\outputs\apk\debug\app-debug.apk
Bytes: 134431679
MiB: 128.20
Fecha: 2026-07-16T15:11:59.7152097-06:00
SHA-256: 502BBE89F03440548965FC722D21AFF818C3793FD1E89204647C526466B5D2E0
```

No se instaló ni abrió.

Warnings de compilación: cambio futuro del target por defecto de anotaciones
Kotlin, `DirectionsRun` deprecated y codegen Kapt de Moshi deprecated.

### Aclaración KSP/AWT de Bloque 2

La “excepción tardía KSP/AWT” es una carrera de cierre del runtime IntelliJ
sombreado dentro de KSP. En el hilo `AWT-EventQueue-0`, un callback de
`BinaryFileTypeDecompilers` llama `FileDocumentManager.getInstance()` cuando
`ApplicationManager.getApplication()` ya es null. No proviene de la lógica de
FitPlan ni de Room.

No quedó resuelta. Se reprodujo con el mismo stack en:

* Bloque 2: `C:\Users\yahir\.gradle\daemon\9.4.1\daemon-12508.out.log`, líneas
  144–159; después consta `BUILD SUCCESSFUL` y `ReturnResult: Success`;
* Bloque 3: `C:\Users\yahir\.gradle\daemon\9.4.1\daemon-10364.out.log`, líneas
  144–159; después consta `BUILD SUCCESSFUL` y `ReturnResult: Success`;
* Bloque 4: `C:\Users\yahir\.gradle\daemon\9.4.1\daemon-19492.out.log`, línea
  144; `BUILD SUCCESSFUL` en línea 236 y `ReturnResult: Success` en línea 245.

Por tanto, fue y sigue siendo **no bloqueante para estos builds observados**,
pero eso no equivale a corregida. El daemon del Bloque 4 también registró una
excepción de shutdown hook al limpiar su registro después de devolver Success;
no cambió el código de salida 0 ni el APK escrito.

### Aclaración del tamaño APK de Bloque 3

El tamaño de Bloque 3 sí fue recalculado directamente con `Get-Item` y no se
copió del checkpoint anterior:

* Bloque 2: 134,431,369 bytes, fecha 2026-07-15 21:16:12 -06:00, SHA-256
  `DAD3E750AFF24A91B311964BABB0E25D47549F5A8629685E8768F3F869062C26`;
* Bloque 3: 134,431,369 bytes, fecha 2026-07-16 12:57:30 -06:00, SHA-256
  `37994BC2A430801AD173BB2E6A7B39473465DD80D0148FE3BD9871DA33210F62`.

La coincidencia de bytes fue real, pero las fechas y hashes diferentes prueban
que eran binarios distintos. Como control adicional, el APK actual de Bloque 4
mide 134,431,679 bytes y tiene otro hash, ambos recalculados desde el archivo.

### Archivos del Bloque 4

Producción:

* `app/src/main/java/com/example/data/model/BuiltInExerciseEquipmentProfiles.kt`
* `app/src/main/java/com/example/data/model/Entities.kt`
* `app/src/main/java/com/example/data/local/ExerciseCatalogDao.kt`
* `app/src/main/java/com/example/data/local/ExerciseCatalogBootstrapper.kt`
* `app/src/main/java/com/example/data/repository/ExerciseCatalogRepository.kt`
* `app/src/main/java/com/example/ui/catalog/ExerciseCatalogViewModel.kt`
* `app/src/main/java/com/example/ui/catalog/ExerciseCatalogScreen.kt`
* `app/src/main/java/com/example/ui/screens/Screens.kt`
* `app/src/main/java/com/example/ui/screens/ProfileScreen.kt`
* `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`
* `app/src/main/java/com/example/ui/viewmodel/FitPlanViewModel.kt`

Pruebas fuente no ejecutadas:

* `app/src/test/java/com/example/data/model/BuiltInExerciseEquipmentProfilesTest.kt`
* `app/src/test/java/com/example/data/model/TrainingPlanRulesTest.kt`
* `app/src/test/java/com/example/data/repository/ExerciseCatalogRepositoryTest.kt`
* `app/src/test/java/com/example/ui/catalog/TrainingCatalogFallbackPolicyTest.kt`
* `app/src/androidTest/java/com/example/ExerciseCatalogBootstrapInstrumentedTest.kt`
* `app/src/androidTest/java/com/example/ExerciseCatalogRoomMigrationInstrumentedTest.kt`
* `app/src/androidTest/java/com/example/ExerciseCatalogUiInstrumentedTest.kt`

Documentación: `docs/exercise-catalog-integration-progress.md`.

### Riesgos pendientes y parada

* Falta ejecutar/compilar las pruebas nuevas en un ciclo que lo autorice.
* Falta validar en Android real los resultados, los cambios de Lugar y la
  supervivencia a cierre/force-stop.
* El APK debug fue compilado, pero no instalado ni probado en el Huawei.
* La excepción tardía KSP/AWT sigue pendiente del tooling.

**Bloque 5 no iniciado.**

## Prompt maestro estático — Checkpoint Bloque 5 (2026-07-16)

### Alcance y lenguaje de evidencia

Bloque 5 revisado exclusivamente por lectura de código, pendiente de validación
en ejecución. No se usaron Git, Gradle, tests, ADB, AVD, Huawei, red ni Gemini.
No se modificó producción dentro de este bloque.

Tres revisiones independientes cubrieron entrenamiento/historial, navegación y
superficies UI, y plataforma/Room/Premium. No se encontró P0 ni corrupción
directa atribuible al Bloque 4 sobre `workout_logs`, navegación, consentimiento
Gemini, tema, Premium o la migración v10→v11.

### Seis fallos históricos

* Las fixtures actuales de entrenamiento completo y Home/Gym ya fijan `Nivel`,
  `Lugar` y disciplina `GYM`. Los lectores de un usuario histórico sin esos
  segmentos siguen siendo no-null: `Nivel → Principiante`, `Lugar → Ambos` y
  `Días → 3`.
* Las tres fixtures de `GeminiUiLifecycleInstrumentedTest` todavía no limpian ni
  siembran `UserEntity.trainingDiscipline=GYM`; dependen del contenido previo de
  Room para que exista `bottom_nav_nutrition`. `CriticalNavigationInstrumentedTest`
  y `ControlledStressInstrumentedTest` conservan la misma fragilidad.
* El fallo GPS API 36 sigue clasificado como defecto del arnés: la prueba marca
  permisos `user-fixed` y después exige un diálogo/IDs/textos del permission
  controller; su cleanup no restaura flags/permisos. La app usa
  `RequestMultiplePermissions` y vuelve a `NEEDS_PERMISSION` al denegarse.

Nada de lo anterior se reejecutó en este bloque.

### Entrenamiento, historial y Home/Gym

Confirmaciones por código:

* `MainActivity` mantiene un `FitPlanViewModel` raíz para perfil/Ajustes, pero
  Workout usa un `GymViewModel` propio por entrada de navegación. Comparten la
  misma Room singleton y sus DAO, no la misma instancia de ViewModel.
* `updateProfileFromSettings` reemplaza únicamente la fila `users` mediante
  `current.copy`; `workout_logs` es una tabla independiente. El escritor de
  Lugar solo reemplaza segmentos `Lugar`, por lo que el Bloque 4 no borra
  historial.
* El doble toque de guardar queda bloqueado dentro de la composición porque la
  primera llamada pone `activeRoutine=null` y la segunda retorna.

Riesgos encontrados, todos pendientes de ejecución:

* **P1 preexistente:** `finishWorkout()` limpia rutina, contador e inputs justo
  después de iniciar `registerWorkout()`, antes de confirmar la escritura Room.
  Un fallo/cierre inmediato puede perder la sesión; `state.error` se asigna pero
  Workout no lo muestra.
* **P1 preexistente:** rutina activa, índice, series, temporizador, repeticiones,
  carga y RPE viven en `remember`, no en `rememberSaveable`, ViewModel ni Room;
  no sobreviven recreación o muerte del proceso.
* **P1 preexistente:** el historial enumera todos los ejercicios planeados con
  sets/reps sugeridos y replica el último peso/RPE; no conserva lo realmente
  ejecutado por serie.
* **P2 preexistente:** la deduplicación no es idempotencia de persistencia. El ID
  es autogenerado y el DAO no tiene una clave única de sesión; dos invocaciones
  directas de `registerWorkout()` insertan dos filas.
* **P2 preexistente:** historial, check-in automático y gamificación no son una
  transacción única; un fallo intermedio puede dejar efectos derivados
  inconsistentes. Borrar historial no revierte esos derivados.
* **P2 preexistente:** `GymUiState` inicia con `user=null/isLoading=true`, pero
  Workout representa temporalmente `Principiante/Sin equipo` y permite acción
  antes de recibir el perfil real.
* **P2 del camino añadido en Bloque 4:** los updates de perfil, disciplina,
  objetivo y billing usan read-copy-replace sin mutex/transacción. Dos updates
  simultáneos de `users` son last-write-wins. No afecta directamente
  `workout_logs`, pero puede perder una preferencia concurrente.
* **P3:** varios callbacks de Workout dependen de `activeRoutine!!`; una acción
  semántica obsoleta después de finalizar sería riesgosa.

### Navegación, nutrición, mapa y GPS

* Consentimiento Gemini continúa explícito: sin checkbox/request válida no hay
  envío; cancelar no inicia análisis y conserva la request para reintento.
* No se encontró corrupción directa de rutas por los Bloques 2–4.
* **P1 preexistente GPS:** `lastLocation/getCurrentLocation` one-shot no conserva
  el `CancellationTokenSource`. Un callback tardío después de pause/finish puede
  entrar a `acceptLocationPoint()` y devolver el estado a `TRACKING`.
* **P2 preexistente mapa muscular:** la pantalla no representa `isLoading` ni
  `error`; ambos parecen un mapa vacío. `hasWorkoutData` mira todo el historial,
  pero el mapa solo usa 28 días, así que historial antiguo puede producir vacío
  sin estado explicativo.

### Ajustes, tema, Premium y Room

Revisado por código, pendiente de ejecución:

* tema se inicializa desde preferencias, actualiza su `StateFlow` y MainActivity
  resuelve `system/light/dark`; un valor corrupto cae a tema del sistema;
* Premium QA sigue siendo una capacidad en memoria
  `BuildConfig.DEBUG && BuildConfig.PREMIUM_TEST_MODE`; no crea compra ni
  suscripción persistente;
* Gradle declara `PREMIUM_TEST_MODE=true` en debug y `false` en release;
* Room permanece en v11, registra `MIGRATION_10_11`, conserva las tablas
  anteriores, crea índices/FKs y los cuatro triggers FTS del esquema exportado;
* no existe `fallbackToDestructiveMigration`;
* bootstrap IMPORT y NO_OP están dentro de transacciones; NO_OP valida conteo y
  reconcilia los 27 built-ins de forma idempotente. Un cambio de versión aborta
  de forma controlada y la UI conserva `TrainingCatalog` como fallback.

### Decisión del checkpoint

No se declara ningún flujo probado ni aprobado. El Bloque 4 no muestra una
regresión evidente sobre historial por lectura de código, pero los P1/P2
anteriores son riesgos reales que Yahir debe considerar en la validación manual
y que impiden describir el comportamiento como estable.

Siguiente bloque: Bloque 6, checklist manual para Yahir. No se ejecuta en este
checkpoint.

## Prompt maestro estático — Checkpoint Bloque 6 (2026-07-16)

### Alcance

Este bloque prepara una guía de validación manual para que Yahir la ejecute en
el Huawei. Codex no abrió ADB, AVD, emulador ni dispositivo; no instaló ni
desinstaló la app, no borró datos y no ejecutó ningún punto del checklist.

Antes de comenzar, anotar la versión instalada y actualizar sobre la instalación
existente, sin desinstalar ni borrar datos. Conservar capturas y la hora exacta
si aparece un cierre, ANR o pérdida de datos.

### Checklist manual para el Huawei

- [ ] **Inicio:** abrir la app desde el launcher; confirmar que llega a Inicio
  sin cierre, pantalla bloqueada ni espera anormal.
- [ ] **Datos existentes:** confirmar antes de editar que perfil, historial,
  preferencias y cualquier dato previo continúan visibles.
- [ ] **Perfil:** abrir Perfil/Ajustes; revisar nombre, objetivo, nivel, días,
  Lugar y tema. Cambiar solo un valor inocuo, volver atrás y confirmar que la UI
  refleja el cambio.
- [ ] **Premium QA activo:** en la APK debug, confirmar que las capacidades
  Premium de prueba aparecen activas sin compra, suscripción ni llamada de red.
  No introducir credenciales ni completar pagos.
- [ ] **Catálogo visible:** abrir el catálogo nuevo y confirmar que carga
  ejercicios; no debe mostrar registros `REVIEW` ni quedar en estado vacío sin
  explicación.
- [ ] **Búsqueda «press de banca»:** escribir exactamente esa frase y comprobar
  que aparece el ejercicio esperado; abrirlo y volver a la lista.
- [ ] **Búsqueda «sentadilla»:** limpiar la búsqueda, escribir exactamente esa
  palabra y comprobar que aparecen resultados coherentes.
- [ ] **Filtro Casa:** seleccionar Casa. Sin equipo disponible deben permanecer
  ejercicios de peso corporal y no deben aparecer ejercicios que exijan
  máquinas. Si se declara equipo, verificar que cada resultado requiera solo
  equipo realmente disponible.
- [ ] **Filtro Gimnasio:** seleccionar Gimnasio y confirmar resultados
  `GYM_COMPATIBLE`/`BOTH`, incluidos ejercicios con equipo de gimnasio.
- [ ] **Detalle de ejercicio:** desde ambos filtros abrir un resultado y validar
  nombre con fallback de idioma, instrucciones, músculo, región, equipo y
  compatibilidad; volver sin perder búsqueda ni filtro.
- [ ] **Iniciar entrenamiento:** iniciar una rutina compatible con la selección
  vigente y comprobar que muestra el perfil/Lugar reales, no valores
  transitorios incorrectos.
- [ ] **Completar entrenamiento:** registrar al menos un ejercicio, completar la
  sesión y esperar una confirmación visible antes de salir de la pantalla.
- [ ] **Guardar historial:** abrir Progreso/Historial y confirmar una sola fila
  nueva para la sesión. Volver a abrir la sección y comprobar que no se duplicó.
- [ ] **Zona Premium:** abrir la zona Premium y recorrer sus entradas locales;
  confirmar que no solicita pagos reales ni abre rutas de red inseguras.
- [ ] **Función IA:** abrir el flujo de IA, verificar que pide consentimiento y
  que cancelar o usar fallback local no provoca crash. No efectuar una llamada
  real a Gemini durante este checklist.
- [ ] **Cerrar y reabrir:** cerrar la app, retirarla de recientes y abrirla otra
  vez. Confirmar que perfil, Lugar, tema, filtro/preferencia aplicable, catálogo
  y la sesión guardada siguen disponibles.
- [ ] **Preferencias y datos existentes:** repetir Inicio, Perfil e Historial y
  comprobar que el update no reemplazó datos anteriores ni la selección
  Casa/Gimnasio.
- [ ] **Ausencia de ANR:** durante todo el recorrido confirmar que Android no
  mostró «La aplicación no responde» y que no hubo bloqueos prolongados.
- [ ] **Incidentes:** si ocurre crash o ANR, no borrar datos; anotar pantalla,
  acción inmediatamente anterior, hora y si se reproduce al repetir la misma
  secuencia.

### Resultado del checkpoint

Checklist preparado: 18 verificaciones. Ejecutadas por Codex: 0. Aprobadas,
fallidas, crashes y ANR: no determinados hasta la validación manual de Yahir.

Siguiente bloque: Bloque 7, auditoría y endurecimiento del escáner de secretos.

## Prompt maestro estático — Checkpoint Bloque 7 (2026-07-16)

### Alcance

Se revisaron fuente, configuración y los APK/AAB que ya estaban en disco. No se
usaron Git, Gradle, tests, ADB, AVD, Huawei, red ni Gemini en este bloque. Los
artefactos existentes se trataron como evidencia histórica; el Bloque 8 debe
reemplazarlos mediante las tres compilaciones autorizadas.

### Escáner automático

Se cerró el hueco del Bloque 1 en `app/build.gradle.kts`:

* `assembleDebug` ahora finaliza en `scanDebugApkForSecrets` y revisa todos los
  APK de `outputs/apk/debug`;
* `assembleRelease` conserva el escaneo de APK release;
* `bundleRelease` conserva el escaneo del AAB release;
* `verifyAndroidArtifactsHaveNoSecrets` agrupa las tres construcciones cuando se
  invoque explícitamente;
* una sola función revisa cada entrada ZIP como ISO-8859-1, UTF-16LE y UTF-16BE;
* además del valor real y Base64 de variables sensibles de `.env`/entorno, busca
  `BACKEND_API_KEY`, claves `AIza…`, secretos `sk-…`, `Authorization: Bearer`,
  asignaciones de `api_key`/`client_secret`/tokens, PEM privados, JWT y URLs con
  credenciales;
* los mensajes solo exponen artefacto, entrada y categoría; nunca el valor.

No se ejecutó la tarea Gradle en este checkpoint. Su sintaxis y funcionamiento
quedan sujetos a las compilaciones del Bloque 8. El escáner cubre credenciales
de alta confianza, no toda posible ofuscación, cifrado o fragmentación.

### Escaneo de artefactos históricos sin Gradle

Un escaneo binario independiente, que incluyó valores sensibles configurados
sin imprimirlos, obtuvo `NO_SECRET_FINDINGS` para:

* `app-debug.apk` de 2026-07-16 15:11:59;
* `app-release-unsigned.apk` de 2026-07-15 15:31:44;
* `app-release.aab` de 2026-07-15 15:31:57.

Esto significa que no se hallaron valores/firmas de alta confianza, no que los
release históricos sean utilizables. La cadena literal genérica `api_key`
aparece en 6 entradas del debug, 5 del APK release y 6 del AAB, principalmente
DEX/librerías nativas de SDKs empaquetados (entre ellas MapLibre). No apareció
una asignación con valor ni coincidencia `AIza`; por tanto se registra como
nombre técnico de terceros y no como credencial. Eliminar ese nombre literal
exigiría retirar o sustituir dependencias y está fuera del alcance de seguridad
de este bloque.

### Verificación binaria de Premium

* El APK debug histórico actual sí contiene `DEBUG=true`,
  `PREMIUM_TEST_MODE=true` y la política nueva sin
  `FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING`.
* El APK y AAB release existentes fueron generados antes del Bloque 2. La
  inspección DEX confirmó `FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING=true` dentro de
  ambos. Quedan **rechazados** y no pueden distribuirse ni reutilizarse.
* La fuente actual fija `PREMIUM_TEST_MODE=false` para release y añade la defensa
  `BuildConfig.DEBUG && BuildConfig.PREMIUM_TEST_MODE`, pero la confirmación
  binaria solo puede cerrarse después de regenerar los artefactos en Bloque 8.

`FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING` no existe en `src/main`. El modo Premium
QA es local: en debug no inicia Billing y el flujo Gemini usa
`DisabledControlledBackendAiProvider`, por lo que no se encontró una ruta de red
Premium/Gemini activa. El manifest debug sí permite cleartext globalmente; es
superficie de riesgo para un cliente futuro, aunque ninguna ruta Premium/IA
actual lo usa. Release no incorpora ese overlay.

### Hallazgos de seguridad pendientes

* **P1:** `PREMIUM_ENABLED=false` no bloquea la ruta Premium de Ajustes. En
  release, la pantalla puede iniciar Billing y permitir compra si Play devuelve
  una oferta. No se cambió porque el bloque solicitó verificación/escaneo, no un
  rediseño de producto.
* **P2:** la compra/restore concede entitlement desde el cliente y persiste un
  booleano sin validación del purchase token en backend.
* **P2:** `ProductAnalytics.track` usa `Log.d` sin guard de `BuildConfig.DEBUG`;
  puede registrar propiedades de salud/producto en release, aunque no imágenes,
  prompts completos ni credenciales.
* **P2:** debug permite HTTP cleartext globalmente.
* **Bloqueo de infraestructura:** el backend sigue fallando abierto si faltan
  `BACKEND_API_KEY` o `JWT_SECRET`. El cambio de runtime es pequeño, pero la
  provisión, rotación y política de arranque son infraestructura y permanecen
  pendientes de autorización explícita.

### Archivos modificados en Bloque 7

* `app/build.gradle.kts`: cobertura debug y ampliación del escáner.
* `docs/exercise-catalog-integration-progress.md`: evidencia y decisión.

### Decisión del checkpoint

Escáner debug: configurado, pendiente de validarse en la compilación. Artefactos
release históricos: rechazados por bypass Premium verdadero. No se afirma que
release fuerce el modo QA a `false` hasta verificar los binarios regenerados.

Siguiente bloque: Bloque 8, compilaciones finales y verificación de artefactos.

## Prompt maestro estático — Checkpoint Bloque 8 (2026-07-16)

### Compilaciones autorizadas

No se usaron Git, tests, ADB, AVD, Huawei, red ni Gemini. Las compilaciones se
ejecutaron en serie:

| Comando | Resultado | Duración | Evidencia |
|---|---|---:|---|
| `./gradlew.bat :app:assembleDebug --no-daemon --console=plain` | `BUILD SUCCESSFUL`, código 0; 44 tareas, 1 ejecutada y 43 up-to-date | 138.246 s | El APK ya estaba actualizado; `scanDebugApkForSecrets` se ejecutó y pasó. |
| `./gradlew.bat :app:assembleRelease --no-daemon --console=plain` | `BUILD SUCCESSFUL`, código 0; 54 tareas, 18 ejecutadas | 410.250 s | KSP/Kotlin, lintVital, R8, empaquetado y escáner APK pasaron. |
| `./gradlew.bat :app:bundleRelease --no-daemon --console=plain` | `BUILD SUCCESSFUL`, código 0; 57 tareas, 6 ejecutadas | 83.189 s | AAB nuevo; `scanReleaseBundleForSecrets` pasó. |

El intento inicial de debug fue cancelado por un timeout de 5 segundos del
runner antes de obtener resultado; se relanzó una sola vez con ventana correcta.
No se clasifica como fallo de Gradle ni de la app.

### APK debug final

* Ruta: `C:\Users\yahir\Desktop\FIT-APP MAS ACTUALIDADA\app\build\outputs\apk\debug\app-debug.apk`.
* Fecha del archivo: 2026-07-16 15:11:59 -06:00. `assembleDebug` confirmó que
  estaba up-to-date y ejecutó un escaneo fresco.
* Tamaño: 134,431,679 bytes (128.204 MiB).
* SHA-256: `502BBE89F03440548965FC722D21AFF818C3793FD1E89204647C526466B5D2E0`.
* Package: `com.aistudio.fitplanmx.app`.
* Version code: 1.
* Version name: 1.0.
* Firma: Android Debug, RSA 2048, certificado SHA-256
  `9926ac6692936e53f6b314a9416e1912408b6b8f9dcdd89e62c1ab8b887a5a08`.
* `apksigner verify --verbose --print-certs`: código 0; 1 firmante; APK
  Signature Scheme v2 verdadero.
* DEX: `DEBUG=true`, `PREMIUM_TEST_MODE=true`,
  `EXERCISE_CATALOG_ENABLED=true`; no contiene
  `FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING` en la política.
* Instalación y smoke test: pendientes; los hará Yahir manualmente en el Huawei.

### Release final

APK:

* `C:\Users\yahir\Desktop\FIT-APP MAS ACTUALIDADA\app\build\outputs\apk\release\app-release-unsigned.apk`;
* 110,558,526 bytes (105.437 MiB);
* SHA-256 `65B35AE3DD5F9DC34B14E921EC59A6D4CFF5EAB18EB2882037EAA46477DBAB9A`;
* `apksigner verify`: código 1, sin firma.

AAB:

* `C:\Users\yahir\Desktop\FIT-APP MAS ACTUALIDADA\app\build\outputs\bundle\release\app-release.aab`;
* 53,243,355 bytes (50.777 MiB);
* SHA-256 `1FE8B5424F4214B527FE6ACE13D50D52A0031192DCDB0E850F18D1E82B8F7DED`;
* `jarsigner` informa `jar is unsigned`; 0 entradas de firma y sin manifest JAR.

R8 terminó sin error y no generó `missing_rules.txt`. Se conserva:

* `C:\Users\yahir\Desktop\FIT-APP MAS ACTUALIDADA\app\build\outputs\mapping\release\mapping.txt`;
* 76,488,810 bytes;
* SHA-256 `B322F04931CB14A366E5C058C079A52222DAA88511F704BBD733BAADB0B70B79`.

La inspección DEX del APK y del AAB nuevos confirma:

* `BUILD_TYPE=release`;
* `DEBUG=false`;
* `PREMIUM_TEST_MODE=false`;
* `EXERCISE_CATALOG_ENABLED=false`;
* `PremiumEntitlementProvider` se construye con `false`;
* `FORCE_UNLOCK_ALL_FEATURES_FOR_TESTING` ya no existe.

### Seguridad final de artefactos

Los tres escáneres automáticos pasaron durante los builds. No encontraron
`BACKEND_API_KEY`, valor sensible configurado ni Base64, `AIza…`, `sk-…`,
`Authorization: Bearer`, asignación de `api_key`/`client_secret`/token, PEM,
JWT o URL con credenciales. No imprimieron secretos.

Permanece la salvedad ya documentada: la palabra genérica `api_key` aparece como
nombre técnico de SDK de terceros en 6 entradas del debug, 5 del APK release y
6 del AAB. No está asociada a valor y no coincide con ningún patrón de
credencial. Por precisión, se confirma ausencia de credenciales, no ausencia
literal de ese nombre técnico.

### Warnings de build

* AGP avisa que `android.newDsl=false` quedará obsoleto.
* Kotlin avisa del futuro cambio de target de anotaciones en el bootstrap y del
  icono `DirectionsRun` deprecated.
* El escáner invalida la configuration cache por capturar referencias del script;
  el build y el escaneo pasan, pero la cache se descarta.
* Después del éxito de `assembleRelease`, KSP/AWT volvió a imprimir la excepción
  tardía `ApplicationManager.getApplication() == null`. No está corregida; en
  esta ejecución ocurrió después de código 0 y no bloqueó R8 ni el artefacto.

### Decisión del Bloque 8

* Uso personal/QA: **APTO PARA INSTALAR (compilado, sin credenciales, pendiente
  de validación manual)**.
* Producción: **NO APTO PARA PUBLICACIÓN**. APK y AAB no tienen firma real y
  permanecen los bloqueos enumerados en el reporte consolidado.

## REPORTE FINAL OBLIGATORIO — Ciclo estático Bloques 1–8

Este resumen reemplaza cualquier estado histórico contradictorio de secciones
anteriores. No transforma evidencia estática o histórica en prueba runtime.

### Estado

* **Uso personal/QA: APTO PARA INSTALAR** — compilado, APK debug firmado y sin
  credenciales detectadas; instalación y validación manual pendientes.
* **Producción: NO APTO PARA PUBLICACIÓN** — release sin firma de producción,
  regresión runtime incompleta, migración real v10 no validada y bloqueos de
  backend/Billing todavía abiertos.

### Premium

Implementación: `PremiumAccessPolicy` centraliza el acceso mediante
`PremiumEntitlementProvider`:

```text
Premium efectivo = (BuildConfig.DEBUG && PREMIUM_TEST_MODE) OR entitlement real
```

Debug concede QA solo en memoria (`true`); no escribe una compra, recibo o
suscripción falsa y PremiumScreen no inicia Billing en test mode. Release fija
`false` y conserva el entitlement real de Google Play Billing. La desactivación
release está confirmada en Gradle y en DEX de APK/AAB.

Pantallas/superficies revisadas por código, pendientes de Huawei: entrada de
Premium en Ajustes, `PremiumScreen`/paywall explícito, escaneo IA de comida en
Nutrición, Dashboard, Progreso/mapa muscular, Entrenamiento, Perfil y navegación
inferior. No existen destinos funcionales separados para coach premium,
exportaciones o plan adaptativo; eran texto comercial/widgets no renderizados.

Pruebas runtime Premium en este ciclo: 0. Las pruebas fuente de política,
contrato de build y recorrido físico fueron revisadas pero no ejecutadas por la
restricción vigente. Riesgos: Ajustes aún puede abrir Billing en release aunque
`PREMIUM_ENABLED=false`, y el entitlement de compra se valida/persiste en cliente
sin verificación del purchase token en backend.

### Catálogo

* Flag debug: `EXERCISE_CATALOG_ENABLED=true`, confirmada en DEX.
* Flag release: `EXERCISE_CATALOG_ENABLED=false`, confirmada en APK/AAB.
* Fuente: commit `118e4bd6b14da6df0e36605d7169b65db18389a4`, solo texto MIT, sin
  imágenes/GIF/media IDs/URLs multimedia.
* Reporte actual: 1,324 mappings externos; 1,310 ACTIVE; 9 MERGED; 5 REVIEW;
  0 descartados; 27 internos; 1,337 canónicos activos totales.
* Los REVIEW `0697`, `0716`, `1403`, `1496` y `1766` permanecen fuera del
  catálogo activo.
* Búsquedas `press de banca` y `sentadilla`: alias/manifiesto y ruta FTS
  revisados por código; pendientes de ejecución manual.
* Filtros Casa sin equipo, Casa con equipo acumulativo, Gimnasio y sin
  preferencia: implementación y SQL revisados por código. Casa sin equipo ya no
  trata los built-ins de máquina como libres de requisitos.
* Fallback: `TrainingCatalog` permanece y se usa si la flag está apagada o el
  bootstrap no está listo; no se reemplazaron rutinas ni Gemini.

### Pruebas de este ciclo restringido

| Suite | Comando | Dispositivo/API | Aprobadas | Fallidas | Omitidas | Duración | XML/evidencia |
|---|---|---|---:|---:|---:|---:|---|
| Unitarias | No ejecutado por instrucción | Ninguno / N/A | 0 | 0 | 0 | 0 s | Sin XML nuevo |
| Instrumentadas | No ejecutado por instrucción | Ninguno / N/A | 0 | 0 | 0 | 0 s | Sin XML nuevo |
| AVD/emulador | Prohibido en este ciclo | Ninguno / N/A | 0 | 0 | 0 | 0 s | Sin evidencia nueva |
| Huawei/manual | Reservado para Yahir | Huawei MAR-LX3A / Android 10 pendiente | 0 | 0 | 0 | 0 s | Checklist del Bloque 6 |

Evidencia histórica de fases anteriores (por ejemplo 145 unitarias aprobadas y
pruebas API 29 focales) no se reutiliza como aprobación de los cambios finales.
Crashes y ANR finales no se determinaron porque no se abrió la app.

### APK debug

Ruta: `C:\Users\yahir\Desktop\FIT-APP MAS ACTUALIDADA\app\build\outputs\apk\debug\app-debug.apk`.

Tamaño 134,431,679 bytes; SHA-256
`502BBE89F03440548965FC722D21AFF818C3793FD1E89204647C526466B5D2E0`;
fecha 2026-07-16 15:11:59 -06:00; package `com.aistudio.fitplanmx.app`;
versionCode 1; versionName 1.0. Firma Android Debug RSA 2048; `apksigner verify`
código 0, esquema v2 y un firmante. Instalación y smoke test pendientes: los
hará Yahir manualmente.

### Release

* APK: `app-release-unsigned.apk`, 110,558,526 bytes, sin firma.
* AAB: `app-release.aab`, 53,243,355 bytes, sin firma.
* R8/lintVital: completados sin error fatal; no hay `missing_rules.txt`.
* Mapping: `app/build/outputs/mapping/release/mapping.txt`, 76,488,810 bytes.
* Escaneo: APK y AAB pasaron sin credenciales de alta confianza.
* Premium QA y catálogo: `false` verificado en ambos binarios release.
* Resultado: artefactos técnicos no publicables por ausencia de clave/firma real.

### Archivos modificados consolidados

Lista reconstruida desde los checkpoints, sin usar Git.

Premium:

* `app/build.gradle.kts`
* `app/src/main/java/com/example/data/model/PremiumAccessPolicy.kt`
* `app/src/main/java/com/example/ui/viewmodel/FitPlanViewModel.kt`
* `app/src/main/java/com/example/features/gym/ui/GymViewModel.kt`
* `app/src/main/java/com/example/ui/screens/NutritionScreen.kt`
* `app/src/main/java/com/example/ui/screens/DeveloperSettingsScreen.kt`
* `app/src/main/java/com/example/ui/screens/PremiumScreen.kt`
* `app/src/test/java/com/example/data/model/PremiumAccessPolicyTest.kt`
* `app/src/test/java/com/example/data/model/PremiumBuildTypeContractTest.kt`
* `app/src/androidTest/java/com/example/PhysicalScreenTraversalInstrumentedTest.kt`

Catálogo:

* `app/src/main/java/com/example/data/model/FeatureSettings.kt`
* `app/src/main/java/com/example/data/model/ExerciseCatalogDomain.kt`
* `app/src/main/java/com/example/data/model/BuiltInExerciseEquipmentProfiles.kt`
* `app/src/main/java/com/example/data/repository/ExerciseCatalogRepository.kt`
* `app/src/main/java/com/example/di/ExerciseCatalogModule.kt`
* `app/src/main/java/com/example/ui/catalog/ExerciseCatalogViewModel.kt`
* `app/src/main/java/com/example/ui/catalog/ExerciseCatalogScreen.kt`
* `app/src/main/assets/exercise_catalog/catalog_manifest.json`
* `app/src/main/assets/exercise_catalog/exercises.compact.json.gz`
* `app/src/main/assets/exercise_catalog/spanish_aliases.json`

Room:

* `app/src/main/java/com/example/data/model/ExerciseCatalogEntities.kt`
* `app/src/main/java/com/example/data/model/Entities.kt`
* `app/src/main/java/com/example/data/local/AppDatabase.kt`
* `app/src/main/java/com/example/data/local/ExerciseCatalogDao.kt`
* `app/src/main/java/com/example/data/local/ExerciseCatalogBootstrapper.kt`
* `app/schemas/com.example.data.local.AppDatabase/11.json`
* pruebas fuente de importación, repositorio, bootstrap, FTS y migración bajo
  `app/src/test` y `app/src/androidTest` (no ejecutadas en este ciclo).

UI/Home-Gym:

* `app/src/main/java/com/example/ui/screens/Screens.kt`
* `app/src/main/java/com/example/ui/screens/ProfileScreen.kt`
* `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`
* `app/src/main/java/com/example/data/local/PreferencesManager.kt`
* pruebas fuente de reglas, fallback y catálogo UI (no ejecutadas).

Seguridad:

* `app/build.gradle.kts`: escáner de debug/release/AAB.
* `app/src/main/java/com/example/data/api/GeminiService.kt`: proveedor remoto
  controlado desactivado por defecto.
* `app/src/test/java/com/example/data/api/GeminiServiceSecurityTest.kt` y
  `app/src/test/java/com/example/security/SecretScanningTest.kt` (no ejecutadas).

Importador/documentación:

* `tools/exercise-catalog-import/` (configuración, importador, resoluciones,
  alias, pruebas fuente y reportes auditables).
* `THIRD_PARTY_NOTICES.md`.
* `docs/exercise-catalog-integration-progress.md`.

Revisión estática/código: Bloques 5 y 6 no cambiaron producción; añadieron
hallazgos y el checklist al documento. En producción, Bloque 8 solo generó
salidas bajo `app/build/`; su evidencia también actualizó este documento.

### Pendientes reales

1. **Firma de producción:** provisionar keystore/upload key real fuera del
   proyecto y regenerar/verificar APK/AAB firmados.
2. **Migración real:** extraer una copia v10 del Huawei y demostrar conservación
   de centinelas, 11 tablas, FKs, índices y FTS tras 10→11.
3. **Regresión/manual Huawei:** ejecutar el checklist del Bloque 6, incluida
   instalación sobre datos existentes, catálogo, Home/Gym, entrenamiento,
   historial sin duplicado, persistencia, IA recuperable, crashes y ANR.
4. **Backend fail-closed:** eliminar defaults de `BACKEND_API_KEY` y
   `JWT_SECRET`, validar antes de escuchar y provisionar/rotar secretos. Sigue
   pendiente de autorización de Yahir porque toca infraestructura.
5. **Billing:** verificar purchase token en backend antes de conceder entitlement
   y evitar que la ruta Premium inicie compras si el producto está globalmente
   deshabilitado.
6. **Entrenamiento:** resolver los P1 de limpiar la sesión antes de confirmar
   Room, pérdida del entrenamiento activo y fidelidad de series realmente
   ejecutadas; validar idempotencia persistente.
7. **GPS:** cancelar callbacks one-shot tardíos para impedir reactivar una sesión
   pausada/finalizada.
8. **Logs release:** proteger/eliminar `ProductAnalytics Log.d` con datos de
   salud/producto.
9. **Tooling:** corregir la incompatibilidad de configuration cache del escáner
   y la excepción tardía KSP/AWT; hoy no bloquean los artefactos observados.

No se intentó migración histórica de APK porque no existe artefacto anterior
fiable: **No comprobada por falta de artefacto anterior**.

## Corrección del mapa muscular — Fase 1 (2026-07-16)

Alcance ejecutado por lectura de código y compilación, sin Git, pruebas,
emulador ni dispositivo:

* `Muscle` separa `ADDUCTORS` y `ABDUCTORS` de `QUADS`; los nombres heredados
  ya no comparten bucket y ambos grupos participan en `ALL_MUSCLES`.
* `BodyHeatMapCanvas` dibuja el trapecio con `TRAPS`, separado de los dos paths
  de `BACK`, y mantiene una zona táctil dedicada. La zona de trapecio se evalúa
  después de espalda para que gane en el solapamiento de los rectángulos.
* El tríceps posterior tiene geometrías izquierda/derecha propias; ya no reutiliza
  los paths de bíceps frontal.
* Se revisaron las seis reutilizaciones restantes: hombros, abductores y
  pantorrillas reutilizan únicamente la forma aproximada del mismo músculo en la
  vista opuesta. No queda una reutilización conocida entre músculos distintos.
* Los nuevos enum se clasifican como `UPPER_LEGS` en el bootstrap y en el filtro
  de región corporal, cerrando los `when` exhaustivos afectados.

Archivos de producción implicados:

* `app/src/main/java/com/example/data/model/Muscle.kt`
* `app/src/main/java/com/example/data/model/MuscleTrainingProfiles.kt`
* `app/src/main/java/com/example/ui/muscles/BodyHeatMapCanvas.kt`
* `app/src/main/java/com/example/data/local/ExerciseCatalogBootstrapper.kt`
* `app/src/main/java/com/example/ui/catalog/ExerciseCatalogViewModel.kt`

Verificación permitida:

* Comando: `.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1 --console=plain`.
* Resultado: `BUILD SUCCESSFUL in 5m 30s`.
* Tareas: 44 (`8 executed`, `36 up-to-date`).
* El escáner de secretos del APK debug pasó sin imprimir valores.
* Warnings no bloqueantes observados: destino futuro de anotaciones Kotlin,
  `DirectionsRun` deprecado, Moshi/Kapt deprecado y la tarea de escaneo no
  serializable por configuration cache.

No se ejecutaron pruebas unitarias o instrumentadas por restricción expresa. La
Fase 1 queda cerrada por revisión estática más compilación, no por validación
visual en dispositivo.

## Corrección del mapa muscular — Fase 2 (2026-07-16)

### Ruta catálogo → heatmap

Se implementó la alternativa aprobada sin migración:

1. `ExerciseTextParser` divide `exercisesText` primero por `;` o saltos de
   línea; no interpreta de forma general las comas como ejercicios.
2. Quita únicamente una prescripción reconocida y normaliza con la misma regla
   exacta del catálogo.
3. `MuscleMapViewModel` reúne todas las claves y realiza una sola resolución
   bulk por emisión del historial.
4. `ExerciseCatalogMuscleResolver` asegura el bootstrap, carga en bloque
   músculos principales/secundarios y construye un índice exacto en memoria.
5. Un fragmento resuelto usa el músculo principal con activación `1.0` y los
   secundarios distintos con `0.5`. `CARDIO`/`FULL_BODY`, que no tienen path
   visual, omiten ese bucket primario y conservan sus secundarios anatómicos.
6. Solo los fragmentos no resueltos pasan al mapa heredado de 14 términos. Las
   claves largas tienen prioridad para evitar el doble match de, por ejemplo,
   `sentadilla búlgara` y `sentadilla`.

Transformaciones verificadas por lectura de implementación:

* `Sentadilla 3x10 @60kg` → `sentadilla` → alias exacto `squat` → metadatos
  musculares de Room.
* `Press de banca: 3 series x 10 reps, Peso: 60, RPE: 8` →
  `press de banca` → alias exacto `bench_press` → metadatos de Room.
* Para historial antiguo como `press banca, remo`, primero se intenta el nombre
  completo; al no existir, se resuelven las dos partes individualmente y se
  permite mezclar catálogo y fallback heredado sin perder `remo`.
* Un nombre canónico que contiene coma conserva prioridad como nombre completo;
  las comas de `Peso`/`RPE` nunca se convierten en ejercicios.

El índice se carga bajo `Dispatchers.IO`. `ExerciseCatalogBootstrapper` ahora
serializa llamadas concurrentes con `Mutex`, evitando que Catálogo y Mapa
intenten el primer import al mismo tiempo. Un fallo del catálogo no se cachea;
se conserva el fallback local. `CancellationException` se relanza.

### Cobertura estática

Medición read-only sobre `tools/exercise-catalog-import/out/exercises.compact.json`:

* Total leído: `1,324`.
* Registros `ACTIVE`: `1,310`.
* Nombre utilizable: `1,310/1,310`.
* Nombres normalizados únicos: `1,310/1,310`; colisiones: `0`.
* Músculo principal válido: `1,310/1,310`; inválidos: `0`.
* Relaciones de músculos secundarios: `2,519`; inválidas: `0`.
* Primario `CARDIO`: `29`; sin músculos secundarios: `0`.

Resultado comparable: `1,310/1,310` ejercicios activos tienen metadatos para
resolver músculos mediante catálogo y `0/1,310` carecen de ese mapeo. Después
del bootstrap también entran los 27 ejercicios integrados (`1,337` canónicos
en total). Esto mide cobertura del catálogo con nombre canónico limpio; no
afirma que todo texto histórico arbitrario coincida. La cantidad real de logs
del usuario que caerían al fallback no se midió porque no se leyó la base del
dispositivo. La UI actual de rutinas tampoco permite todavía realizar los
1,310 externos, aunque la ruta ya puede resolverlos si su nombre canónico llega
al historial.

### Persistencia, esquema y archivos

No se agregó `catalogId` a `WorkoutLogEntity`; el historial permanece intacto.
`AppDatabase` continúa en versión `11`. No se modificaron entidades Room, DDL,
IDs de ejercicios/rutinas ni datos existentes; por tanto no existe migración
11→12 para este cambio.

Archivos de Fase 2:

* `app/src/main/java/com/example/features/musclemap/domain/ExerciseTextParser.kt`
* `app/src/main/java/com/example/features/musclemap/data/ExerciseCatalogMuscleResolver.kt`
* `app/src/main/java/com/example/features/musclemap/domain/repository/MuscleMapRepository.kt`
* `app/src/main/java/com/example/features/musclemap/data/repository/MuscleMapRepositoryImpl.kt`
* `app/src/main/java/com/example/di/DatabaseModule.kt`
* `app/src/main/java/com/example/data/local/ExerciseCatalogBootstrapper.kt`
* `app/src/main/java/com/example/ui/muscles/MuscleMapViewModel.kt`

### Verificación permitida

* Revisión independiente de especificación: aprobada después de limitar
  `routineName` exclusivamente a la selección del perfil fallback.
* Revisión independiente de calidad: aprobada después de conservar todas las
  partes de listas históricas por coma y mover bootstrap/índice a IO.
* Comando: `.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1 --console=plain`.
* La captura exterior alcanzó su timeout a los 10 minutos, pero el proceso
  continuó y el log real `daemon-30556.out.log` registró
  `BUILD SUCCESSFUL in 11m 14s`.
* Tareas: 44 (`9 executed`, `35 up-to-date`).
* `scanDebugApkForSecrets`: aprobado sin imprimir valores.
* APK actualizado: `app/build/outputs/apk/debug/app-debug.apk`, fecha
  `2026-07-16 19:28:15`, 134,431,679 bytes.

No se ejecutaron pruebas unitarias/instrumentadas, AVD ni dispositivo por
restricción expresa. La evidencia es revisión estática, medición del catálogo y
compilación debug.

### Fuera de alcance y riesgos conocidos

Permanecen explícitamente fuera de alcance:

1. Trazar contornos anatómicos precisos nuevos.
2. Sustituir los cuatro PNG que contienen zonas rojas pre-pintadas.
3. Corregir la deformación causada por `ContentScale.FillBounds`.

`MuscleHitboxConfig.kt` y `HitboxTransformer.kt` siguen sin activarse. Además,
el parser de volumen preexistente busca `series N`/`reps N`, mientras la UI
escribe `N series`/`N reps`; esto puede dejar series/repeticiones en sus defaults
y alterar la intensidad relativa, pero no el grupo muscular resuelto. Se deja
documentado porque corregir el cálculo de volumen no formó parte de estas dos
fases.

## Resultado final del bloque de mapa muscular

* Fase 1: mapeos cruzados confirmados corregidos y build debug aprobado.
* Fase 2: catálogo Room conectado al heatmap sin migración, cobertura estática
  `1,310/1,310` y build debug aprobado.
* Estado de evidencia: listo para validación visual/manual posterior; no probado
  en dispositivo en esta sesión.

## Corrección adicional del mapa muscular — Fase 0: auditoría de alineación (2026-07-16)

Esta fase fue exclusivamente de lectura. Se compararon las coordenadas
normalizadas de los 16 paths frontales y 15 posteriores de
`BodyHeatMapCanvas.kt` contra los cuatro PNG, usando el modelo masculino como
referencia principal y revisando el impacto de reutilizar las mismas geometrías
en el femenino. No se modificó código de producción ni se ejecutaron Gradle,
pruebas, ADB, emulador o dispositivo.

### Transformación común y diferencias de los assets

`AnatomyPanel` fuerza todos los modelos a `aspectRatio(0.49f)` y muestra el PNG
con `ContentScale.FillBounds`; el `Canvas` ocupa el mismo rectángulo. Los paths
y hit zones no reciben `BodyModel`, por lo que las coordenadas son idénticas en
masculino y femenino.

| PNG | Dimensiones | Aspecto nativo | Deformación horizontal al mostrarlo en 0.49 |
|---|---:|---:|---:|
| `body_male_front.png` | 682×1387 | 0.491709 | −0.35 %, casi imperceptible |
| `body_male_back.png` | 647×1386 | 0.466811 | +4.97 %, ensanchado |
| `body_female_front.png` | 634×1370 | 0.462774 | +5.88 %, ensanchado |
| `body_female_back.png` | 613×1367 | 0.448427 | +9.27 %, ensanchado |

`FillBounds` no desplaza por sí solo el Canvas respecto al raster, porque ambos
comparten el mismo rectángulo. Sí deforma la anatomía de tres assets y amplifica
el error de usar una sola geometría para cuerpos con proporciones distintas. El
posterior femenino es el caso más expuesto.

### Resultado región por región — Frente (16)

| Región | Resultado sobre masculino | Desplazamiento/escala | Femenino e hitZone |
|---|---|---|---|
| Hombro izquierdo | Razonable | Path sobre deltoides superior | También razonable; hitZone ~4–5 % hacia el torso y omite el borde lateral |
| Hombro derecho | Razonable | Espejo del izquierdo | Mismo desacople menor del hitZone |
| Pecho izquierdo | Razonable | Sobre el pectoral | Región correcta en femenino, aunque el contorno masculino no reproduce el volumen mamario; hitZone suficiente |
| Pecho derecho | Razonable | Espejo del izquierdo | Mismo resultado |
| Bíceps izquierdo | Incorrecto | Extremo inferior ~6–8 % demasiado bajo, rebasa el codo; centro ~3–4 % bajo | Persiste y derrama ~2–3 % lateralmente en femenino; hitZone comparte el exceso inferior |
| Bíceps derecho | Incorrecto | Espejo del izquierdo | Mismo defecto en femenino y hitZone |
| Antebrazo izquierdo | Incorrecto, confirmado | ~9–12 % hacia abajo; cubre antebrazo distal, muñeca y mano; además ~3–5 % hacia el torso en la mitad distal | Igual o más visible en femenino; hitZone está en la misma ubicación errónea |
| Antebrazo derecho | Incorrecto, confirmado | Espejo del izquierdo | Mismo defecto en femenino y hitZone |
| Abdomen | Razonable | Levemente ancho/bajo, pero permanece sobre recto abdominal y abdomen inferior | También razonable; hitZone centrado |
| Abductor izquierdo | Incorrecto | ~3–4 % demasiado lateral y ~8–11 % demasiado largo/bajo; alcanza rodilla o zona inferior | Más severo en femenino; hitZone conserva el exceso vertical y no coincide con el contorno |
| Abductor derecho | Incorrecto | Espejo del izquierdo | Mismo defecto |
| Aductores | Incorrecto | Empieza ~4–5 % tarde y termina ~9–12 % bajo la inserción medial de rodilla | Persiste en femenino; hitZone comparte el desplazamiento y exceso inferior |
| Cuádriceps izquierdo | Incorrecto | Tercio inferior atraviesa rodilla; borde inferior ~9–11 % bajo y centro ~5–6 % bajo | Persiste en femenino; hitZone también está bajo y ~3–4 % hacia el centro |
| Cuádriceps derecho | Incorrecto | Espejo del izquierdo | Mismo defecto |
| Pantorrilla izquierda | Incorrecto | ~5–7 % hacia abajo; empieza a media pantorrilla y termina en tobillo/pie | Persiste y es más ancha que la pierna femenina; hitZone comparte la posición baja |
| Pantorrilla derecha | Incorrecto | Espejo del izquierdo | Mismo defecto |

Conteo frontal de paths: **5/16 razonables** y **11/16 con problema real**.
Aunque los paths de hombros son razonables, sus dos hit zones requieren un
ajuste lateral menor.

### Resultado región por región — Espalda (15)

| Región | Resultado sobre masculino | Desplazamiento/escala | Femenino e hitZone |
|---|---|---|---|
| Hombro izquierdo | Razonable | Simplificación aceptable sobre deltoides posterior | Algo ancho en femenino, aún utilizable; hitZone ~4–5 % hacia dentro |
| Hombro derecho | Razonable | Espejo del izquierdo | Mismo desacople menor del hitZone |
| Dorsal izquierdo | Incorrecto | ~3–5 % demasiado exterior y ~4–6 % de exceso vertical arriba/abajo; invade fondo y cresta ilíaca | Peor en torso femenino estrecho, invasión exterior ~5–7 %; hitZone omite el ala exterior |
| Dorsal derecho | Incorrecto | Espejo del izquierdo | Mismo defecto |
| Trapecio | Incorrecto | Demasiado central y pesado abajo; faltan ~8–12 % hacia cada hombro y ~3–5 % hacia arriba | Persiste; hitZone no cubre el tercio inferior del path |
| Tríceps izquierdo | Incorrecto | Extremo inferior ~5–7 % bajo, entra al antebrazo; ~2–3 % exterior | Peor en brazo femenino estrecho; hitZone acompaña el error vertical |
| Tríceps derecho | Incorrecto | Espejo del izquierdo | Mismo defecto |
| Glúteo izquierdo | Incorrecto | ~5–7 % hacia abajo: empieza a mitad del glúteo y termina sobre femoral proximal | Mismo error vertical; hitZone omite ~6–7 % exterior |
| Glúteo derecho | Incorrecto | Espejo del izquierdo | Mismo defecto |
| Abductor izquierdo | Incorrecto | Reutiliza el path frontal: ~3–5 % exterior, demasiado largo y llega a rodilla | Sigue incorrecto en femenino; hitZone queda hacia dentro y recortado |
| Abductor derecho | Incorrecto | Espejo del izquierdo | Mismo defecto |
| Femoral izquierdo | Incorrecto | Inicio ~4–5 % bajo y final ~7–9 % bajo; omite origen proximal y cruza rodilla hacia pantorrilla | Igual o peor en femenino; hitZone conserva el desplazamiento |
| Femoral derecho | Incorrecto | Espejo del izquierdo | Mismo defecto |
| Pantorrilla izquierda | Incorrecto | ~7–10 % hacia abajo y ~2–3 % demasiado ancha; termina en Aquiles/talón | Peor horizontalmente en femenino; hitZone también está bajo y hacia dentro |
| Pantorrilla derecha | Incorrecto | Espejo del izquierdo | Mismo defecto |

Conteo posterior de paths: **2/15 razonables** y **13/15 con problema real**.
También aquí los hit zones de hombros tienen un desacople lateral menor.

### Conclusiones de alineación

Resultado global: **7/31 paths razonablemente alineados** y **24/31 con
desplazamiento o escala incorrectos**. Los defectos más severos son:

1. Antebrazos frontales sobre muñeca/mano.
2. Cuádriceps, aductores, abductores, femorales y pantorrillas prolongados hacia
   rodilla, pierna, tobillo o pie.
3. Glúteos posteriores desplazados hacia los femorales.
4. Abductores y pantorrillas posteriores reutilizando sin adaptación la
   geometría frontal.
5. Trapecio y dorsales con forma/escala incompatibles con el PNG posterior.

En las 24 regiones defectuosas el hitZone conserva al menos parte del error o
no coincide con la envolvente del path; cualquier corrección visual deberá
mover también la detección táctil. Además, los cuatro hombros necesitan ajuste
de hitZone aunque sus paths sean aceptables.

### Color pre-incrustado detectado en los PNG

La revisión de los archivos crudos amplía el hallazgo original:

* Frontal masculino: ambos cuádriceps.
* Frontal femenino: ambos cuádriceps.
* Posterior masculino: ambos glúteos **y ambos isquiotibiales/femorales**.
* Posterior femenino: ambos glúteos; los femorales no muestran el mismo gradiente
  intenso.

No se observó el gradiente falso de heatmap en torso, brazos o pantorrillas. El
problema es independiente de los datos: `Image` se dibuja antes del `Canvas` y
`drawMuscle` no pinta relleno con intensidad cero.

La alternativa de código viable para una fase posterior es la Opción A: una
neutralización opaca específica por modelo y vista antes del heatmap dinámico.
La Opción B por sí sola dejaría huecos o transparencia, porque no existe una
versión anatómica neutral debajo del PNG. Una máscara plana puede eliminar el
estado falso, pero no reconstruye la textura muscular tapada y exige contornos
cuidadosos para no dejar halos rojos.

### Estado al cerrar Fase 0

* Regiones corregidas en esta fase: **ninguna**, por la puerta de revisión
  solicitada antes de tocar código.
* Compilación: **no ejecutada**, porque no hubo cambio de producción que
  verificar y la Fase 0 prohibía corregir.
* Fase 1 y Fase 2 adicionales: pendientes de aprobación de alcance.
