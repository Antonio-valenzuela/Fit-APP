'use strict'

const express = require('express')
const router = express.Router()
const { authenticateToken } = require('../middleware/auth')
const {
  calculateSessionMuscleVolume,
  getHeatmap,
  detectImbalances,
  recommendNextMuscleGroup
} = require('../services/muscleService')

router.get('/heatmap', authenticateToken, async (req, res) => {
  try {
    const { heatmap, days } = await getHeatmap(req.user.id, req.query.days)
    res.json({ ok: true, heatmap, days })
  } catch (err) {
    console.error('[muscles/heatmap]', err)
    res.status(500).json({ error: 'Error al obtener mapa muscular' })
  }
})

router.get('/imbalances', authenticateToken, async (req, res) => {
  try {
    const imbalances = await detectImbalances(req.user.id)
    res.json({ ok: true, imbalances, count: imbalances.length })
  } catch (err) {
    console.error('[muscles/imbalances]', err)
    res.status(500).json({ error: 'Error al detectar desbalances musculares' })
  }
})

router.get('/recommend', authenticateToken, async (req, res) => {
  try {
    const recommendation = await recommendNextMuscleGroup(req.user.id)
    res.json({ ok: true, recommendation })
  } catch (err) {
    console.error('[muscles/recommend]', err)
    res.status(500).json({ error: 'Error al generar recomendacion muscular' })
  }
})

router.post('/session', authenticateToken, async (req, res) => {
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
    console.error('[muscles/session]', err)
    res.status(500).json({ error: 'Error al procesar sesion muscular' })
  }
})

module.exports = router
