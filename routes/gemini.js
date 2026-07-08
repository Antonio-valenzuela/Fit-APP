'use strict'

const express = require('express')
const router = express.Router()
const { authenticateToken } = require('../middleware/auth')
const rateLimit = require('express-rate-limit')

const geminiLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 20, // limit to 20 requests per minute
  message: { error: 'Demasiadas peticiones. Limite de 20 solicitudes por minuto excedido.' },
  standardHeaders: true,
  legacyHeaders: false
})

const GEMINI_URL = 'https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent'

async function handleProxy(req, res) {
  const apiKey = process.env.GEMINI_API_KEY
  if (!apiKey || apiKey === 'MY_GEMINI_API_KEY' || apiKey.includes('placeholder')) {
    return res.status(500).json({ error: 'La API key de Gemini no esta configurada en el servidor backend.' })
  }

  const timeoutMs = Number(process.env.GEMINI_TIMEOUT_MS) || 60000
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs)

  try {
    const response = await fetch(GEMINI_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-goog-api-key': apiKey
      },
      body: JSON.stringify(req.body),
      signal: controller.signal
    })

    clearTimeout(timeoutId)

    const data = await response.json()
    res.status(response.status).json(data)
  } catch (err) {
    clearTimeout(timeoutId)
    const isTimeout = err.name === 'AbortError'
    const message = isTimeout ? 'Peticion abortada por timeout del servidor.' : err.message

    // Log the error securely without logging the sensitive body contents
    console.error('[Gemini Proxy Error]', {
      message,
      status: isTimeout ? 504 : 500,
      path: req.path
    })

    res.status(isTimeout ? 504 : 500).json({
      error: 'Error al comunicarse con Gemini.',
      detail: message
    })
  }
}

router.post('/chat', authenticateToken, geminiLimiter, handleProxy)
router.post('/vision', authenticateToken, geminiLimiter, handleProxy)

module.exports = router
