'use strict'

const express = require('express')
const router = express.Router()
const { authenticateToken, ensureAuthenticatedPrincipal } = require('../middleware/auth')
const { postAuthRateLimit } = require('../middleware/rateLimits')
const {
  calculateSessionMuscleVolume,
  getHeatmap,
  detectImbalances,
  recommendNextMuscleGroup
} = require('../services/muscleService')

router.use(authenticateToken, postAuthRateLimit, ensureAuthenticatedPrincipal)

function reportRouteError(req, event) {
  req.app?.locals?.securityLogger?.error?.({ event, requestId: req.id })
}

router.get('/heatmap', async (req, res) => {
  try {
    const { heatmap, days } = await getHeatmap(req.user.id, req.query.days)
    res.json({ ok: true, heatmap, days })
  } catch (err) {
    reportRouteError(req, 'muscles.heatmap.error')
    res.status(500).json({ error: 'Error al obtener mapa muscular' })
  }
})

router.get('/imbalances', async (req, res) => {
  try {
    const imbalances = await detectImbalances(req.user.id)
    res.json({ ok: true, imbalances, count: imbalances.length })
  } catch (err) {
    reportRouteError(req, 'muscles.imbalances.error')
    res.status(500).json({ error: 'Error al detectar desbalances musculares' })
  }
})

router.get('/recommend', async (req, res) => {
  try {
    const recommendation = await recommendNextMuscleGroup(req.user.id)
    res.json({ ok: true, recommendation })
  } catch (err) {
    reportRouteError(req, 'muscles.recommend.error')
    res.status(500).json({ error: 'Error al generar recomendacion muscular' })
  }
})

router.post('/session', async (req, res) => {
  const { session_date: sessionDate, exercises } = req.body

  if (!Array.isArray(exercises) || exercises.length === 0) {
    return res.status(400).json({ error: 'Se requiere una lista de ejercicios' })
  }

  try {
    const volumes = await calculateSessionMuscleVolume(
      req.user.id,
      sessionDate || new Date().toISOString().slice(0, 10),
      exercises
    )
    res.json({ ok: true, muscle_volumes: volumes })
  } catch (err) {
    reportRouteError(req, 'muscles.session.error')
    res.status(500).json({ error: 'Error al procesar sesion muscular' })
  }
})

module.exports = router
