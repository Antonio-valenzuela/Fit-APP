'use strict'

const express = require('express')
const router = express.Router()
const db = require('../db')
const { authenticateToken } = require('../middleware/auth')
const { normalizeHealthData, calculateReadiness } = require('../utils/healthNormalizer')

const ALLOWED_PLATFORMS = ['android_hc', 'ios_hk', 'huawei_hms']
const VALID_CONSENT_STATUS = ['granted', 'partial', 'denied', 'revoked']
const VALID_METRICS = ['steps', 'heart_rate', 'sleep', 'weight', 'calories_burned', 'spo2', 'hrv']

router.post('/sync', authenticateToken, async (req, res) => {
  const { platform, data } = req.body
  const userId = req.user.id

  if (!ALLOWED_PLATFORMS.includes(platform)) {
    return res.status(400).json({ error: 'Plataforma invalida' })
  }
  if (!data || typeof data !== 'object') {
    return res.status(400).json({ error: 'Datos de salud requeridos' })
  }

  try {
    const records = normalizeHealthData(platform, data)
    if (records.length === 0) {
      return res.status(400).json({ error: 'No hay datos validos para sincronizar' })
    }

    await Promise.all(records.map(record =>
      db.query(
        `INSERT INTO health_records
           (user_id, date, metric_type, value, unit, source, raw_data)
         VALUES (?, ?, ?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE
           value = VALUES(value),
           source = VALUES(source),
           raw_data = VALUES(raw_data),
           updated_at = NOW()`,
        [userId, record.date, record.metric_type, record.value, record.unit, platform, JSON.stringify(record.raw)]
      )
    ))

    await db.query(
      `INSERT INTO consent_state (user_id, platform, status, granted_at)
       VALUES (?, ?, 'granted', NOW())
       ON DUPLICATE KEY UPDATE status = 'granted', granted_at = NOW(), updated_at = NOW()`,
      [userId, platform]
    )

    await db.query(
      `INSERT INTO consent_audit (user_id, platform, action, context, ip_address, user_agent)
       VALUES (?, ?, 'granted', 'sync_success', ?, ?)`,
      [userId, platform, req.ip, req.headers['user-agent'] || '']
    )

    res.json({ ok: true, synced: records.length, platform })
  } catch (err) {
    console.error('[health/sync]', err)
    res.status(500).json({ error: 'Error al sincronizar datos de salud' })
  }
})

router.get('/summary', authenticateToken, async (req, res) => {
  const userId = req.user.id
  const days = Math.min(Number.parseInt(req.query.days, 10) || 7, 90)

  try {
    const rows = await queryRows(
      `SELECT
         metric_type,
         DATE(date) AS day,
         AVG(value) AS avg_val,
         MAX(value) AS max_val,
         MIN(value) AS min_val,
         source
       FROM health_records
       WHERE user_id = ?
         AND date >= DATE_SUB(CURDATE(), INTERVAL ? DAY)
       GROUP BY metric_type, DATE(date), source
       ORDER BY date DESC`,
      [userId, days]
    )

    const summary = {}
    for (const row of rows) {
      if (!summary[row.metric_type]) summary[row.metric_type] = []
      summary[row.metric_type].push({
        day: row.day,
        avg: Number.parseFloat(row.avg_val).toFixed(2),
        max: Number.parseFloat(row.max_val).toFixed(2),
        min: Number.parseFloat(row.min_val).toFixed(2),
        source: row.source
      })
    }

    res.json({ ok: true, days, summary })
  } catch (err) {
    console.error('[health/summary]', err)
    res.status(500).json({ error: 'Error al obtener resumen de salud' })
  }
})

router.get('/readiness', authenticateToken, async (req, res) => {
  const userId = req.user.id

  try {
    const rows = await queryRows(
      `SELECT metric_type, value
       FROM health_records
       WHERE user_id = ?
         AND date = CURDATE()`,
      [userId]
    )

    const metrics = {}
    for (const row of rows) metrics[row.metric_type] = Number.parseFloat(row.value)

    const avgRows = await queryRows(
      `SELECT AVG(value) AS avg_hr
       FROM health_records
       WHERE user_id = ?
         AND metric_type = 'heart_rate'
         AND date >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)`,
      [userId]
    )
    const avgHR = avgRows[0]?.avg_hr ? Number.parseFloat(avgRows[0].avg_hr) : null

    const score = calculateReadiness({
      sleepHours: metrics.sleep,
      heartRate: metrics.heart_rate,
      avgHeartRate: avgHR
    })

    const response = readinessCopy(score)
    res.json({ ok: true, score, ...response, metrics })
  } catch (err) {
    console.error('[health/readiness]', err)
    res.status(500).json({ error: 'Error al calcular readiness' })
  }
})

router.post('/consent', authenticateToken, async (req, res) => {
  const { platform, status, denied_types = [] } = req.body
  const userId = req.user.id

  if (!ALLOWED_PLATFORMS.includes(platform)) {
    return res.status(400).json({ error: 'Plataforma invalida' })
  }
  if (!VALID_CONSENT_STATUS.includes(status)) {
    return res.status(400).json({ error: 'Estado invalido' })
  }

  try {
    await db.query(
      `INSERT INTO consent_state
         (user_id, platform, status, denied_types, granted_at)
       VALUES (?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE
         status = VALUES(status),
         denied_types = VALUES(denied_types),
         granted_at = IF(VALUES(status) = 'granted', NOW(), granted_at),
         updated_at = NOW()`,
      [userId, platform, status, JSON.stringify(denied_types), status === 'granted' ? new Date() : null]
    )

    await db.query(
      `INSERT INTO consent_audit
         (user_id, platform, action, context, ip_address, user_agent)
       VALUES (?, ?, ?, 'consent_update', ?, ?)`,
      [userId, platform, status === 'revoked' ? 'revoked' : status, req.ip, req.headers['user-agent'] || '']
    )

    res.json({ ok: true, platform, status })
  } catch (err) {
    console.error('[health/consent]', err)
    res.status(500).json({ error: 'Error al guardar consentimiento' })
  }
})

router.get('/consent', authenticateToken, async (req, res) => {
  const userId = req.user.id
  try {
    const rows = await queryRows(
      `SELECT platform, status, denied_types, granted_at, updated_at
       FROM consent_state
       WHERE user_id = ?`,
      [userId]
    )

    const consent = {}
    for (const platform of ALLOWED_PLATFORMS) consent[platform] = { status: 'not_asked' }
    for (const row of rows) {
      consent[row.platform] = {
        status: row.status,
        denied_types: row.denied_types ? JSON.parse(row.denied_types) : [],
        granted_at: row.granted_at,
        updated_at: row.updated_at
      }
    }

    res.json({ ok: true, consent })
  } catch (err) {
    console.error('[health/consent GET]', err)
    res.status(500).json({ error: 'Error al leer consentimientos' })
  }
})

router.post('/manual', authenticateToken, async (req, res) => {
  const userId = req.user.id
  const { metric_type, value, unit, date } = req.body

  if (!VALID_METRICS.includes(metric_type)) {
    return res.status(400).json({ error: 'Metrica invalida' })
  }
  if (Number.isNaN(Number.parseFloat(value))) {
    return res.status(400).json({ error: 'Valor numerico requerido' })
  }

  const recordDate = date || new Date().toISOString().slice(0, 10)

  try {
    await db.query(
      `INSERT INTO health_records
         (user_id, date, metric_type, value, unit, source)
       VALUES (?, ?, ?, ?, ?, 'manual')
       ON DUPLICATE KEY UPDATE
         value = VALUES(value),
         source = 'manual',
         updated_at = NOW()`,
      [userId, recordDate, metric_type, Number.parseFloat(value), unit || '']
    )
    res.json({ ok: true, metric_type, value, date: recordDate })
  } catch (err) {
    console.error('[health/manual]', err)
    res.status(500).json({ error: 'Error al guardar dato manual' })
  }
})

async function queryRows(sql, params) {
  const result = await db.query(sql, params)
  return Array.isArray(result) && Array.isArray(result[0]) ? result[0] : result
}

function readinessCopy(score) {
  if (score >= 75) {
    return { label: 'Alto', advice: 'Dia ideal para intensidad alta si te sientes bien.' }
  }
  if (score >= 45) {
    return { label: 'Medio', advice: 'Sesion moderada recomendada. Enfocate en tecnica.' }
  }
  return { label: 'Bajo', advice: 'Descanso activo o movilidad. Tu cuerpo necesita recuperarse.' }
}

module.exports = router
