# Arquitectura

## Enfoque

FitPlan MX usa Feature-First + Clean Architecture.

Cada feature debe seguir:

```text
features/<feature>/
  data/
    datasources/
    models/
    repositories/
  domain/
    entities/
    repositories/
    usecases/
  presentation/
    controllers/
    pages/
    widgets/
```

## Capas

`presentation`
- Flutter, GetX, paginas, widgets y controllers.
- No importa Firebase.
- No ejecuta queries directas.
- Convierte inputs del usuario a llamadas de caso de uso.

`domain`
- Dart puro.
- Entidades, contratos y reglas de negocio.
- No depende de Flutter ni Firebase.

`data`
- Modelos serializables.
- Datasources Firebase.
- Repositorios concretos.
- Unica capa que conoce Cloud Firestore, Auth, Storage o RevenueCat SDK si aplica.

## Carpetas principales

`app/`
- Configuracion global, tema, router y ambiente.

`core/`
- Constantes, errores, servicios transversales, storage local, utilidades y widgets base.

`shared/`
- Modelos compartidos, widgets compartidos y extensions.

`features/`
- Cada modulo funcional aislado por feature.

`assets/`
- Imagenes, iconos, animaciones y datasets semilla.

`docs/`
- Documentacion tecnica, producto, privacidad, App Store y escalabilidad.

`test/`
- Unit, widget e integration tests.

## Flujo de dependencia

```text
presentation -> domain
data -> domain
app/core -> coordina inyeccion y servicios
```

No se permite:
- `presentation -> firebase_*`
- `domain -> flutter`
- `domain -> firebase_*`
- widgets con logica de negocio pesada
- `main.dart` con configuracion de features

## Estados de controllers

Estado minimo:
- `initial`
- `loading`
- `success`
- `empty`
- `error`

Para formularios:
- validacion local
- loading durante envio
- error visible
- confirmacion al guardar

## Calculos de negocio

Calorias:
- BMR Mifflin-St Jeor.
- TDEE por nivel de actividad.
- deficit moderado para bajar grasa.
- superavit moderado para ganar musculo.
- proteina por kg segun objetivo.
- grasas minimas por kg.
- carbs como calorias restantes.

Salud:
- glucosa y presion solo con mensajes generales.
- tendencias por fecha.
- promedios semanales.
- fuera de rango habitual no equivale a diagnostico.

Entrenamiento:
- volumen = series * reps * peso.
- adherencia = sesiones completadas / sesiones planificadas.
- rutina por objetivo, nivel, dias y lugar.

