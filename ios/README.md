# MacroBar iOS — Fase 1

Esta carpeta añade una app nativa SwiftUI y una extensión WidgetKit al repositorio FitPlan MX sin alterar el código Android.

## Qué incluye

- App de prueba en español con el botón Probar App Group.
- Widget pequeño y mediano que intenta leer el dato escrito por la app.
- ios/project.yml: manifiesto XcodeGen para generar el proyecto en GitHub Actions.
- builder.json en la raíz: indica a ios-builder que el proyecto está en ios/, esquema MacroBar y firma desactivada.

## Entorno

- Deployment target: iOS 17.0.
- Dispositivo objetivo: iPhone 16 Pro con iOS 27.2.
- Apple tenía iOS 27.2 en beta y Xcode 27.2 en beta en la fecha de preparación. El workflow estándar de ios-builder utiliza Xcode estable; esta prueba usa APIs de WidgetKit disponibles desde iOS 17 y no depende de funciones exclusivas de iOS 27.2.
- La compilación CI se deja sin firma. La instalación y re-firma se intentará desde MobAI.

## Prueba de aceptación

1. Compilar con builder ios build --unsigned.
2. Instalar el IPA con MobAI en el iPhone.
3. Mantener pulsada la pantalla de inicio, añadir el widget MacroBar y probar sus tamaños pequeño y mediano.
4. Abrir la app y pulsar Probar App Group.
5. Verificar que el mensaje escrito desde la app aparece en el widget después de que iOS refresque su timeline.

Limitación importante: una cuenta Apple gratuita puede no permitir el entitlement de App Groups en el perfil de provisioning o al re-firmar. Esta fase está diseñada precisamente para comprobarlo, no para asumir que funciona. Si la re-firma falla o el widget no ve el contenedor, registraremos el error exacto antes de decidir el siguiente paso. No se incluye ninguna API key ni secreto en esta fase.

## Antes de la primera compilación

El workflow de ios-builder está en .github/workflows/ios-build.yml. Revisar el PR y confirmar que el workflow ya esté presente en la rama por defecto; la acción workflow_dispatch se resuelve desde esa rama.
