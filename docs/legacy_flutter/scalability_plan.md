# Potencial de escalabilidad

## Nivel 1: MVP

- Usuarios individuales.
- Registro manual.
- Firebase basico.
- Rutinas simples.
- Alimentacion simple.

Preparacion tecnica:
- Clean Architecture.
- Firestore por `uid + date`.
- Catalogos publicos cacheables.
- Disclaimer y privacidad desde el primer dia.

## Nivel 2: App comercial

- Premium.
- Reportes.
- Historial completo.
- Notificaciones.
- Recomendaciones.
- Integracion con Apple Health.

Preparacion:
- RevenueCat.
- Cloud Functions.
- Resumenes diarios.
- Exportacion PDF.
- Feature flags.

## Nivel 3: Plataforma

- Panel para nutriologos.
- Panel para entrenadores.
- Gimnasios.
- Retos grupales.
- Programas corporativos.
- Reportes para coaches.
- Marketplace de planes.

Preparacion:
- Roles y permisos.
- Relaciones coach-cliente.
- Consentimiento explicito para compartir datos.
- Auditoria.
- Web dashboard.

## Nivel 4: Expansion

- Android.
- Web dashboard.
- Wearables.
- IA.
- Teleconsulta.
- Exportacion PDF.
- Laboratorios.
- Planes por institucion.

## Escalabilidad tecnica

Firestore escala bien para registros por usuario si se evitan hotspots y consultas globales. Para cargas agregadas, usar Cloud Functions y BigQuery.

## Escalabilidad comercial

El modelo freemium permite adquisicion barata; premium se justifica con reportes, historiales, recomendaciones y ahorro de tiempo.

## Escalabilidad institucional

Requiere consentimiento, roles, auditoria, contratos y mayor control de privacidad.

## Escalabilidad de datos

Separar:
- datos operativos en Firestore
- archivos en Storage
- analitica en BigQuery
- agregados de dashboard en documentos resumen

## Escalabilidad de monetizacion

- Suscripcion individual.
- Plan familiar futuro.
- Licencias para gimnasios.
- Licencias para empresas.
- Marketplace con comision.

## Riesgos

- Costos por lecturas repetidas.
- Mal manejo de datos sensibles.
- Rechazo App Store por claims medicos.
- Churn por friccion al registrar comidas.
- Complejidad de planes personalizados.

## Preparacion desde arquitectura

- Separar domain de Firebase.
- Usar repositorios por feature.
- Crear modelos versionables.
- Usar `createdAt`, `updatedAt`, `date` y `uid` consistentemente.
- No depender de strings dispersos.
- Mantener docs de reglas e indices.

