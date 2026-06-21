'use strict'

const PLATFORM_MAP = {
  android_hc: {
    steps: { type: 'steps', unit: 'count' },
    heart_rate: { type: 'heart_rate', unit: 'bpm' },
    sleep_hours: { type: 'sleep', unit: 'hours' },
    weight_kg: { type: 'weight', unit: 'kg' },
    calories: { type: 'calories_burned', unit: 'kcal' },
    spo2: { type: 'spo2', unit: '%' },
    hrv_ms: { type: 'hrv', unit: 'ms' }
  },
  ios_hk: {
    stepCount: { type: 'steps', unit: 'count' },
    heartRate: { type: 'heart_rate', unit: 'bpm' },
    sleepHours: { type: 'sleep', unit: 'hours' },
    bodyMass: { type: 'weight', unit: 'kg' },
    activeEnergy: { type: 'calories_burned', unit: 'kcal' },
    oxygenSat: { type: 'spo2', unit: '%' },
    heartRateVar: { type: 'hrv', unit: 'ms' }
  },
  huawei_hms: {
    STEP_SUM: { type: 'steps', unit: 'count' },
    HEART_RATE_BPM: { type: 'heart_rate', unit: 'bpm' },
    SLEEP_SESSION: { type: 'sleep', unit: 'hours' },
    BODY_WEIGHT: { type: 'weight', unit: 'kg' },
    CALORIES_TOTAL: { type: 'calories_burned', unit: 'kcal' }
  }
}

function normalizeHealthData(platform, data) {
  const mapping = PLATFORM_MAP[platform]
  if (!mapping) throw new Error(`Plataforma desconocida: ${platform}`)

  const date = data.date || new Date().toISOString().slice(0, 10)
  const records = []

  for (const [key, meta] of Object.entries(mapping)) {
    const value = data[key]
    if (value !== undefined && value !== null && !Number.isNaN(Number.parseFloat(value))) {
      records.push({
        date,
        metric_type: meta.type,
        value: Number.parseFloat(value),
        unit: meta.unit,
        raw: { [key]: value }
      })
    }
  }

  return records
}

function calculateReadiness({ sleepHours, rpe, heartRate, avgHeartRate }) {
  let score = 100

  if (sleepHours !== undefined) {
    if (sleepHours < 5) score -= 30
    else if (sleepHours < 6) score -= 20
    else if (sleepHours < 7) score -= 10
  }

  if (rpe !== undefined) {
    if (rpe >= 9) score -= 25
    else if (rpe >= 8) score -= 15
    else if (rpe >= 7) score -= 5
  }

  if (heartRate && avgHeartRate) {
    const diff = heartRate - avgHeartRate
    if (diff > 10) score -= 20
    else if (diff > 5) score -= 10
  }

  return Math.max(0, Math.min(100, score))
}

module.exports = { normalizeHealthData, calculateReadiness }
