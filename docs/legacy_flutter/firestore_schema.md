# Modelo Firestore

## Principio de seguridad

Todo dato de usuario debe tener `uid` y las reglas deben verificar `request.auth.uid == uid`. Para escalar, todas las consultas de historial deben filtrar por `uid` y rango de `date`, con paginacion.

## Colecciones

### users
Perfil de cuenta y banderas globales.

```json
{"uid":"abc","email":"user@mail.com","displayName":"Ana","createdAt":"serverTimestamp","updatedAt":"serverTimestamp","isPremium":false,"role":"user","acceptedHealthDisclaimer":true}
```

### user_profiles
Datos corporales, objetivo, actividad y condiciones opcionales.

```json
{"uid":"abc","age":30,"sex":"female","heightCm":165,"weightKg":72,"targetWeightKg":65,"goal":"fatLoss","activityLevel":"light","trainingLevel":"beginner","trainingDays":3,"trainingPlace":"home","dailyCaloriesGoal":1850,"proteinGoal":130,"carbsGoal":190,"fatGoal":55,"hasDiabetes":false,"hasHypertension":false,"hasInjuries":false,"createdAt":"serverTimestamp","updatedAt":"serverTimestamp"}
```

### foods
Catalogo publico de alimentos.

```json
{"id":"tortilla_maiz","name":"Tortilla de maiz","category":"Cereales","servingSize":1,"servingUnit":"pieza","calories":64,"protein":1.4,"carbs":13,"fats":0.7,"sodium":12,"sugar":0.2,"fiber":1.5,"country":"MX","isMexicanFood":true,"createdAt":"serverTimestamp"}
```

### meal_logs
Registro de comida por usuario.

```json
{"id":"m1","uid":"abc","date":"2026-05-01","mealType":"breakfast","foodId":"tortilla_maiz","foodName":"Tortilla de maiz","quantity":2,"servingUnit":"pieza","calories":128,"protein":2.8,"carbs":26,"fats":1.4,"sugar":0.4,"sodium":24,"notes":"Con huevo","createdAt":"serverTimestamp"}
```

### custom_meals
Comidas personalizadas y favoritas.

```json
{"id":"cm1","uid":"abc","name":"Bowl pollo arroz","ingredients":[{"foodName":"Pechuga","quantity":150}],"totalCalories":520,"totalProtein":42,"totalCarbs":55,"totalFats":14,"createdAt":"serverTimestamp"}
```

### exercises
Catalogo publico de ejercicios.

```json
{"id":"squat","name":"Sentadilla","muscleGroup":"Pierna","equipment":"bodyweight","level":"beginner","instructions":"Baja controlado y sube firme.","videoUrl":"","alternatives":["chair_squat"],"createdAt":"serverTimestamp"}
```

### workout_plans
Plan generado o asignado al usuario.

```json
{"id":"wp1","uid":"abc","goal":"fatLoss","level":"beginner","daysPerWeek":3,"place":"home","weeks":[{"week":1,"days":[{"day":1,"exerciseIds":["squat","pushup"]}]}],"createdAt":"serverTimestamp"}
```

### workout_logs
Bitacora de entrenamiento.

```json
{"id":"wl1","uid":"abc","date":"2026-05-01","workoutPlanId":"wp1","exerciseId":"squat","exerciseName":"Sentadilla","sets":3,"reps":12,"weightKg":0,"completed":true,"notes":"","createdAt":"serverTimestamp"}
```

### progress_entries
Peso, medidas y foto opcional.

```json
{"id":"p1","uid":"abc","date":"2026-05-01","weightKg":72,"waistCm":88,"chestCm":96,"hipCm":102,"armCm":31,"legCm":55,"notes":"Inicio","photoUrl":"","createdAt":"serverTimestamp"}
```

### glucose_logs
Bitacora de glucosa.

```json
{"id":"g1","uid":"abc","glucoseMgDl":105,"measurementType":"fasting","date":"2026-05-01","time":"08:00","relatedMealId":null,"notes":"","symptoms":[],"createdAt":"serverTimestamp"}
```

### blood_pressure_logs
Bitacora de presion arterial.

```json
{"id":"bp1","uid":"abc","systolic":118,"diastolic":76,"pulse":70,"date":"2026-05-01","time":"08:10","position":"sentado","arm":"izquierdo","notes":"","createdAt":"serverTimestamp"}
```

### water_logs
Agua consumida.

```json
{"id":"w1","uid":"abc","amountMl":500,"date":"2026-05-01","time":"12:00","createdAt":"serverTimestamp"}
```

### sleep_logs
Sueno.

```json
{"id":"s1","uid":"abc","sleepHours":7.5,"sleepQuality":"good","date":"2026-05-01","notes":"","createdAt":"serverTimestamp"}
```

### wellness_logs
Estado general.

```json
{"id":"we1","uid":"abc","mood":"calm","energyLevel":4,"stressLevel":2,"notes":"","date":"2026-05-01","createdAt":"serverTimestamp"}
```

### subscriptions
Estado de suscripcion. Debe escribirse desde servidor o flujo confiable.

```json
{"uid":"abc","status":"active","platform":"ios","productId":"fitplan_premium_monthly","purchaseDate":"2026-05-01","expirationDate":"2026-06-01","provider":"revenuecat","updatedAt":"serverTimestamp"}
```

### notifications
Recordatorios programados.

```json
{"id":"n1","uid":"abc","type":"meal","title":"Registra tu comida","body":"Agrega tu comida para mantener tu resumen al dia.","scheduledAt":"2026-05-01T14:00:00Z","sent":false,"createdAt":"serverTimestamp"}
```

### audit_logs
Eventos de auditoria. Preferible escritura desde Cloud Functions.

```json
{"id":"a1","uid":"abc","action":"create","module":"meal_logs","metadata":{"docId":"m1"},"createdAt":"serverTimestamp"}
```

## Indices recomendados

Compuestos:
- `meal_logs`: `uid ASC, date DESC, createdAt DESC`
- `workout_logs`: `uid ASC, date DESC, createdAt DESC`
- `progress_entries`: `uid ASC, date DESC`
- `glucose_logs`: `uid ASC, date DESC, createdAt DESC`
- `blood_pressure_logs`: `uid ASC, date DESC, createdAt DESC`
- `water_logs`: `uid ASC, date DESC`
- `sleep_logs`: `uid ASC, date DESC`
- `wellness_logs`: `uid ASC, date DESC`
- `notifications`: `uid ASC, scheduledAt ASC, sent ASC`
- `audit_logs`: `uid ASC, createdAt DESC`

## Datos que deben protegerse mas

- glucosa
- presion arterial
- peso y medidas
- fotos de progreso
- condiciones declaradas
- email y perfil
- reportes exportados
- tokens de notificacion
- informacion de suscripcion

## Evitar que un usuario lea datos de otro

- Reglas por documento: `resource.data.uid == request.auth.uid`.
- En creacion: `request.resource.data.uid == request.auth.uid`.
- No usar colecciones publicas para registros privados.
- No guardar UID sensible en nombres de documentos publicos.
- Para catalogos publicos (`foods`, `exercises`) permitir lectura y bloquear escritura cliente.

## Eliminar cuenta y datos

Flujo recomendado:
1. Usuario confirma eliminacion en Settings.
2. Reautenticacion si Firebase lo requiere.
3. Cloud Function callable borra subdatos por `uid`.
4. Borra archivos en Storage bajo `users/{uid}`.
5. Borra `users/{uid}`, `user_profiles/{uid}`, `subscriptions/{uid}`.
6. Elimina cuenta de Firebase Auth.
7. Registra auditoria no personal si legalmente procede.

## Exportar datos

MVP:
- Export JSON local desde app para registros del usuario.

Comercial:
- Cloud Function genera PDF/CSV.
- Archivo temporal en Storage `exports/{uid}` con expiracion.
- Link firmado con vencimiento.

