# Catálogo de ejercicios — Fase 2

La base Room pasa de la versión 10 a la 11 mediante `MIGRATION_10_11` y conserva
las once tablas existentes. `TrainingCatalog` permanece como fallback; el
bootstrap es explícito y todavía no está conectado a la interfaz.

## Requisitos de equipo

Cada fila de `exercise_equipment_requirements` es acumulativa: todas las filas
del mismo `catalogId` son necesarias. Las alternativas no se codifican como
filas separadas. `BODY_WEIGHT` puede ser `primaryEquipmentType`, pero nunca se
inserta como requisito.

## Importación

La primera ejecución importa el asset compactado y registra procedencia de los
1,324 registros externos más los 27 ejercicios `FITPLAN_BUILT_IN`. Una ejecución
con la misma versión, checksum y conteo es `NO_OP`; una versión, checksum o
conteo diferente se aborta de forma controlada. No hay reconciliación incremental
ni `fallbackToDestructiveMigration`.

## Búsqueda

`ExerciseSearchContentEntity` es el único destino de escrituras normales de
búsqueda. Room genera los triggers del FTS4 external-content; el comando de
reconstrucción se reserva para mantenimiento. El manifiesto separado de alias
españoles cubre `press de banca`, `peso muerto`, `sentadilla`, `flexiones`,
`lagartijas`, `elevaciones laterales`, `curl de bíceps`, `dominadas`, `remo`,
`zancadas`, `abdominales` y `plancha` sin traducir masivamente el dataset.

La prueba con `MigrationTestHelper` y la prueba FTS están preparadas. La copia
real de la base v10 del Huawei aún requiere que el dispositivo esté conectado y
que se extraiga un artefacto local; no se inventa ni se sustituye por una base
de producción.
