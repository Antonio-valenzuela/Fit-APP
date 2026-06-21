'use strict'

const db = require('../db')

const ANTAGONIST_PAIRS = [
  { name: 'Empuje / jalon', a: 'pecho', b: 'espalda', idealRatio: 1.0 },
  { name: 'Cuadriceps / femorales', a: 'cuadriceps', b: 'femorales', idealRatio: 1.5 },
  { name: 'Biceps / triceps', a: 'biceps', b: 'triceps', idealRatio: 1.0 },
  { name: 'Hombros / espalda', a: 'hombros', b: 'espalda', idealRatio: 0.7 },
  { name: 'Gluteos / cuadriceps', a: 'gluteos', b: 'cuadriceps', idealRatio: 0.9 }
]

const ALL_MUSCLES = [
  'pecho', 'espalda', 'hombros', 'biceps', 'triceps',
  'cuadriceps', 'femorales', 'gluteos', 'pantorrillas',
  'abdomen', 'antebrazos', 'aductores', 'abductores'
]

async function calculateSessionMuscleVolume(userId, sessionDate, exercises) {
  const muscleVolumes = {}

  for (const exercise of exercises) {
    const name = normalizeExerciseName(exercise.name)
    const maps = await getExerciseMuscleMap(name)
    const sets = positiveNumber(exercise.sets)
    const reps = positiveNumber(exercise.reps)
    const weight = positiveNumber(exercise.weight_kg ?? exercise.weightKg, 1)
    const rawVolume = sets * reps * weight

    for (const mappedMuscle of maps) {
      const activation = positiveNumber(mappedMuscle.activation_pct, 100) / 100
      const adjustedVolume = rawVolume * activation
      const muscle = mappedMuscle.muscle_group

      if (!muscleVolumes[muscle]) {
        muscleVolumes[muscle] = { sets: 0, reps: 0, volume: 0 }
      }

      muscleVolumes[muscle].sets += sets
      muscleVolumes[muscle].reps += sets * reps
      muscleVolumes[muscle].volume += adjustedVolume
    }
  }

  const maxVolume = Math.max(...Object.values(muscleVolumes).map(item => item.volume), 1)
  const upserts = Object.entries(muscleVolumes).map(([muscle, data]) => {
    const score = Math.max(0, Math.min(100, Math.round((data.volume / maxVolume) * 100)))
    return db.query(
      `INSERT INTO session_muscle_volume
         (user_id, session_date, muscle_group, total_sets, total_reps, total_volume_kg, intensity_score)
       VALUES (?, ?, ?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE
         total_sets = VALUES(total_sets),
         total_reps = VALUES(total_reps),
         total_volume_kg = VALUES(total_volume_kg),
         intensity_score = VALUES(intensity_score),
         updated_at = NOW()`,
      [userId, sessionDate, muscle, data.sets, data.reps, data.volume.toFixed(2), score]
    )
  })

  await Promise.all(upserts)
  return muscleVolumes
}

async function getHeatmap(userId, days = 7) {
  const safeDays = Math.max(1, Math.min(Number.parseInt(days, 10) || 7, 90))
  const rows = await queryRows(
    `SELECT muscle_group,
            MAX(intensity_score) AS peak_intensity,
            AVG(intensity_score) AS avg_intensity,
            SUM(total_volume_kg) AS total_volume,
            MAX(session_date) AS last_trained,
            COUNT(DISTINCT session_date) AS sessions
     FROM session_muscle_volume
     WHERE user_id = ?
       AND session_date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
     GROUP BY muscle_group`,
    [userId, safeDays]
  )

  const maxPeak = Math.max(...rows.map(row => Number.parseFloat(row.peak_intensity) || 0), 1)
  const heatmap = {}

  for (const row of rows) {
    heatmap[row.muscle_group] = {
      intensity: Math.round(((Number.parseFloat(row.peak_intensity) || 0) / maxPeak) * 100),
      avg: Math.round(Number.parseFloat(row.avg_intensity) || 0),
      volume: Number.parseFloat(row.total_volume || 0).toFixed(1),
      lastTrained: row.last_trained,
      sessions: Number.parseInt(row.sessions, 10) || 0
    }
  }

  return { heatmap, days: safeDays }
}

async function detectImbalances(userId) {
  const rows = await queryRows(
    `SELECT muscle_group, SUM(total_volume_kg) AS total_vol
     FROM session_muscle_volume
     WHERE user_id = ?
       AND session_date >= DATE_SUB(CURDATE(), INTERVAL 28 DAY)
     GROUP BY muscle_group`,
    [userId]
  )

  const volumes = {}
  for (const row of rows) volumes[row.muscle_group] = Number.parseFloat(row.total_vol) || 0

  const warnings = []
  for (const pair of ANTAGONIST_PAIRS) {
    const volA = volumes[pair.a] || 0
    const volB = volumes[pair.b] || 0
    if (volA === 0 && volB === 0) continue

    const ratio = volB > 0 ? volA / volB : 99
    const deviation = Math.abs(ratio - pair.idealRatio) / pair.idealRatio

    if (deviation > 0.3) {
      const weak = ratio > pair.idealRatio ? pair.b : pair.a
      const strong = ratio > pair.idealRatio ? pair.a : pair.b
      warnings.push({
        pair: pair.name,
        weak_muscle: weak,
        strong_muscle: strong,
        ratio: Number.parseFloat(ratio.toFixed(2)),
        ideal: pair.idealRatio,
        severity: deviation > 0.6 ? 'high' : 'medium',
        recommendation: `Agrega mas trabajo de ${weak}. Tu balance actual favorece ${strong}.`
      })
    }
  }

  return warnings
}

async function recommendNextMuscleGroup(userId) {
  const [imbalances, recentRows] = await Promise.all([
    detectImbalances(userId),
    queryRows(
      `SELECT muscle_group, MAX(session_date) AS last_trained
       FROM session_muscle_volume
       WHERE user_id = ?
       GROUP BY muscle_group`,
      [userId]
    )
  ])

  const lastTrained = {}
  for (const row of recentRows) lastTrained[row.muscle_group] = row.last_trained

  if (imbalances.length > 0) {
    const urgent = [...imbalances].sort((a, b) => severityRank(b.severity) - severityRank(a.severity))[0]
    return {
      muscle: urgent.weak_muscle,
      reason: urgent.recommendation,
      urgency: urgent.severity
    }
  }

  let recommended = 'espalda'
  let maxDays = -1
  const today = new Date()

  for (const muscle of ALL_MUSCLES) {
    const last = lastTrained[muscle]
    const days = last ? Math.floor((today - new Date(last)) / 86400000) : 999
    if (days > maxDays) {
      maxDays = days
      recommended = muscle
    }
  }

  return {
    muscle: recommended,
    reason: maxDays === 999
      ? `Aun no tienes registros recientes de ${recommended}.`
      : `Llevas ${maxDays} dias sin entrenar ${recommended}.`,
    urgency: 'low'
  }
}

async function getExerciseMuscleMap(exerciseName) {
  const rows = await queryRows(
    `SELECT muscle_group, activation_pct, is_primary
     FROM exercise_muscle_map
     WHERE exercise_name = ?`,
    [exerciseName]
  )

  if (rows.length > 0) return rows

  console.warn(`[muscleService] Ejercicio sin mapeo: ${exerciseName}. Se usa abdomen como fallback.`)
  await db.query(
    `INSERT IGNORE INTO exercise_muscle_map
       (exercise_name, muscle_group, activation_pct, is_primary)
     VALUES (?, 'abdomen', 35, FALSE)`,
    [exerciseName]
  )
  return [{ muscle_group: 'abdomen', activation_pct: 35, is_primary: false }]
}

function normalizeExerciseName(name) {
  return String(name || 'Entrenamiento general').trim() || 'Entrenamiento general'
}

function positiveNumber(value, fallback = 0) {
  const parsed = Number.parseFloat(value)
  if (Number.isNaN(parsed) || parsed < 0) return fallback
  return parsed
}

function severityRank(severity) {
  return severity === 'high' ? 2 : severity === 'medium' ? 1 : 0
}

async function queryRows(sql, params) {
  const result = await db.query(sql, params)
  return Array.isArray(result) && Array.isArray(result[0]) ? result[0] : result
}

module.exports = {
  calculateSessionMuscleVolume,
  getHeatmap,
  detectImbalances,
  recommendNextMuscleGroup
}
