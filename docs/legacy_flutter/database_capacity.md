# Cantidad de personas que aguanta la base de datos

Firestore puede soportar desde cientos hasta muchos miles de usuarios activos si las consultas estan bien disenadas. El limite real no es "numero de usuarios", sino lecturas, escrituras, almacenamiento, indices y patrones de acceso diarios.

En MVP puede soportar cientos o miles de usuarios si:
- cada consulta filtra por `uid`
- el historial consulta por rango de fecha
- se usa paginacion
- se evitan offsets
- se usan indices compuestos `uid + date`
- no se recalculan dashboards leyendo todo el historial

## Escenarios

Con 100 usuarios:
- Riesgo bajo.
- Puede operar incluso cerca del free tier si el uso es moderado.

Con 1,000 usuarios:
- Requiere billing activo y monitoreo.
- Las escrituras de comida, salud y entrenamiento ya son relevantes.

Con 10,000 usuarios:
- Requiere indices cuidados, agregados diarios y cache.
- Conviene Cloud Functions para resumenes.

Con 100,000 usuarios:
- Firestore sigue siendo viable, pero el costo y analitica requieren diseno serio.
- BigQuery se vuelve conveniente para analitica.
- Algunas cargas agregadas podrian ir a SQL o data warehouse.

## Limites relevantes

Segun documentacion oficial de Firebase, el free tier de Cloud Firestore incluye 1 GiB almacenado, 50,000 lecturas por dia, 20,000 escrituras por dia y 20,000 deletes por dia. Firestore tambien tiene limite de 1 MiB por documento y limites de indices/reglas. Con billing, el patron de uso y el costo importan mas que el limite teorico.

## Colecciones que crecerian mas rapido

- `meal_logs`
- `water_logs`
- `workout_logs`
- `glucose_logs`
- `blood_pressure_logs`
- `progress_entries`
- `notifications`
- `audit_logs`

## Tabla estimada

| Usuarios activos | Registros diarios por usuario | Escrituras diarias aproximadas | Riesgo | Recomendacion |
|---:|---:|---:|---|---|
| 100 | 8 | 800 | Bajo | Free tier podria aguantar si las lecturas son moderadas. |
| 1,000 | 8 | 8,000 | Bajo/medio | Activar billing, indices y alertas de presupuesto. |
| 10,000 | 8 | 80,000 | Medio | Usar agregados diarios, paginacion y Cloud Functions. |
| 100,000 | 8 | 800,000 | Alto/costo | BigQuery, funciones, cache, particion logica y observabilidad. |

## Como reducir costos

- Dashboard con documentos agregados por dia: `daily_summaries/{uid_yyyy-mm-dd}`.
- Historial paginado con `limit(20)` y cursor.
- No usar `offset`.
- Evitar listeners realtime en historiales largos.
- Desactivar indices que no se consultan.
- Separar catalogos publicos de datos privados.
- Cache local para catalogos de alimentos y ejercicios.
- Cloud Functions para calculos repetidos.

## Consultas correctas

Para graficas:
- `where('uid', isEqualTo: uid)`
- `where('date', isGreaterThanOrEqualTo: start)`
- `where('date', isLessThanOrEqualTo: end)`
- `orderBy('date', descending: true)`
- `limit(n)`

No consultar toda la coleccion y filtrar en cliente.

## Archivar datos antiguos

Opciones:
- Mantener ultimos 12 meses en colecciones activas.
- Mover historicos a `archived_*`.
- Exportar a BigQuery para analitica.
- Usar TTL solo donde no afecte obligaciones de privacidad y producto.

## Cuando usar BigQuery

- Cohortes.
- Retencion.
- Analitica por modulo.
- Segmentacion.
- Reportes comerciales.
- Eventos masivos.

## Cuando usar Cloud Functions

- Eliminar cuenta y datos.
- Generar PDFs.
- Enviar notificaciones.
- Actualizar resumenes diarios.
- Verificar suscripciones.
- Auditoria.
- Calculos que no deben depender del cliente.

## Cuando usar SQL adicional

- Reportes relacionales complejos.
- Paneles para coaches con filtros avanzados.
- Marketplace con pagos, facturacion y permisos complejos.
- Necesidad de joins fuertes y transacciones multi-entidad mas controladas.

