'use strict'

const express = require('express')
const router = express.Router()
const { authenticateToken, ensureAuthenticatedPrincipal } = require('../middleware/auth')
const { postAuthRateLimit } = require('../middleware/rateLimits')
const { normalizeHealthData, calculateReadiness } = require('../utils/healthNormalizer')

const ALLOWED_PLATFORMS = ['android_hc', 'ios_hk', 'huawei_hms']
const VALID_CONSENT_STATUS = ['granted', 'partial', 'denied', 'revoked']
const VALID_METRICS = ['steps', 'heart_rate', 'sleep', 'weight', 'calories_burned', 'spo2', 'hrv']

router.use(authenticateToken, postAuthRateLimit, ensureAuthenticatedPrincipal)

function reportRouteError(req, event) {
  req.app?.locals?.securityLogger?.error?.({ event, requestId: req.id })
}

router.post('/sync', async (req, res) => {
  const { platform, data } = req.body
  const userId = req.user.id

  if (!ALLOWED_PLATFORMS.includes(platform)) {
    return res.status(400).json({ error: 'Plataforma invalida' })
  }
  if (!data || typeof data !== 'object') {
    return res.status(400).json({ error: 'Datos de salud requeridos' })
  }

  try {
    const database = req.app.locals.database
    const outcome = await database.transaction(async tx => {
      const consentRows = await queryRows(
        tx,
        `SELECT status, denied_types
         FROM consent_state
         WHERE user_id = ? AND platform = ?
         FOR UPDATE`,
        [userId, platform]
      )
      const consent = consentRows[0]
      if (!consent || !['granted', 'partial'].includes(consent.status)) {
        return { status: 403, error: 'Consentimiento requerido' }
      }
      const deniedTypes = consent.status === 'partial' ? parseDeniedTypes(consent.denied_types) : []
      const records = normalizeHealthData(platform, data)
        .filter(record => !deniedTypes.includes(record.metric_type))
      if (records.length === 0) {
        return { status: 400, error: 'No hay datos validos para sincronizar' }
      }
      for (const record of records) {
        await tx.query(
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
      }
      await tx.query(
        `INSERT INTO consent_audit (user_id, platform, action, context)
         VALUES (?, ?, ?, 'sync_authorized')`,
        [userId, platform, consent.status]
      )
      return { records, consent }
    })
    if (outcome.error) return res.status(outcome.status).json({ error: outcome.error })

    req.app?.locals?.securityLogger?.audit?.('health.sync.authorized', {
      requestId: req.id,
      entity: platform,
      operation: outcome.consent.status
    })

    res.json({ ok: true, synced: outcome.records.length, platform })
  } catch (err) {
    reportRouteError(req, 'health.sync.error')
    res.status(500).json({ error: 'Error al sincronizar datos de salud' })
  }
})

router.get('/summary', async (req, res) => {
  const userId = req.user.id
  const days = Math.min(Number.parseInt(req.query.days, 10) || 7, 90)

  try {
    const rows = await queryRows(
      req.app.locals.database,
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
    reportRouteError(req, 'health.summary.error')
    res.status(500).json({ error: 'Error al obtener resumen de salud' })
  }
})

router.get('/readiness', async (req, res) => {
  const userId = req.user.id

  try {
    const rows = await queryRows(
      req.app.locals.database,
      `SELECT metric_type, value
       FROM health_records
       WHERE user_id = ?
         AND date = CURDATE()`,
      [userId]
    )

    const metrics = {}
    for (const row of rows) metrics[row.metric_type] = Number.parseFloat(row.value)

    const avgRows = await queryRows(
      req.app.locals.database,
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
    reportRouteError(req, 'health.readiness.error')
    res.status(500).json({ error: 'Error al calcular readiness' })
  }
})

router.post('/consent', async (req, res) => {
  const { platform, status, denied_types = [] } = req.body
  const userId = req.user.id

  if (!ALLOWED_PLATFORMS.includes(platform)) {
    return res.status(400).json({ error: 'Plataforma invalida' })
  }
  if (!VALID_CONSENT_STATUS.includes(status)) {
    return res.status(400).json({ error: 'Estado invalido' })
  }
  const normalizedDeniedTypes = validateDeniedTypes(status, denied_types)
  if (!normalizedDeniedTypes) {
    return res.status(400).json({ error: 'Tipos denegados invalidos' })
  }

  try {
    await req.app.locals.database.transaction(async tx => {
      await tx.query(
        `INSERT INTO consent_state
           (user_id, platform, status, denied_types, granted_at)
         VALUES (?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE
           status = VALUES(status),
           denied_types = VALUES(denied_types),
           granted_at = IF(VALUES(status) = 'granted', NOW(), granted_at),
           updated_at = NOW()`,
        [userId, platform, status, JSON.stringify(normalizedDeniedTypes), status === 'granted' ? new Date() : null]
      )
      await tx.query(
        `INSERT INTO consent_audit
           (user_id, platform, action, context)
         VALUES (?, ?, ?, 'consent_update')`,
        [userId, platform, status]
      )
    })
    req.app?.locals?.securityLogger?.audit?.('health.consent.changed', {
      requestId: req.id,
      entity: platform,
      operation: status
    })

    res.json({ ok: true, platform, status })
  } catch (err) {
    reportRouteError(req, 'health.consent_update.error')
    res.status(500).json({ error: 'Error al guardar consentimiento' })
  }
})

router.get('/consent', async (req, res) => {
  const userId = req.user.id
  try {
    const rows = await queryRows(
      req.app.locals.database,
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
    reportRouteError(req, 'health.consent_read.error')
    res.status(500).json({ error: 'Error al leer consentimientos' })
  }
})

router.post('/manual', async (req, res) => {
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
    await req.app.locals.database.query(
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
    reportRouteError(req, 'health.manual.error')
    res.status(500).json({ error: 'Error al guardar dato manual' })
  }
})

async function queryRows(database, sql, params) {
  const result = await database.query(sql, params)
  return Array.isArray(result) && Array.isArray(result[0]) ? result[0] : result
}

function parseDeniedTypes(value) {
  if (Array.isArray(value)) return value.filter(item => VALID_METRICS.includes(item))
  if (typeof value !== 'string' || !value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.filter(item => VALID_METRICS.includes(item)) : []
  } catch {
    return []
  }
}

function validateDeniedTypes(status, value) {
  if (!Array.isArray(value) || value.some(item => !VALID_METRICS.includes(item))) return null
  const normalized = [...new Set(value)]
  if (status === 'partial') return normalized.length > 0 && normalized.length < VALID_METRICS.length ? normalized : null
  return normalized.length === 0 ? normalized : null
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
