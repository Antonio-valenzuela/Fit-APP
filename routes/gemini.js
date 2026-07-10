'use strict'

const express = require('express')
const rateLimit = require('express-rate-limit')
const { authenticateToken } = require('../middleware/auth')
const {
  NutritionValidationError,
  buildGeminiRequest,
  parseNutritionEnvelope
} = require('../services/geminiNutrition')
const { GeminiProvider, ProviderRequestError } = require('../services/geminiProvider')

const router = express.Router()
const MAX_PREPARED_IMAGE_BYTES = 4 * 1024 * 1024
const DEFAULT_MODEL = process.env.GEMINI_MODEL || 'gemini-3.5-flash'

const geminiLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 20,
  message: { error: 'Demasiadas peticiones. Limite de 20 solicitudes por minuto excedido.' },
  standardHeaders: true,
  legacyHeaders: false
})

class VisionPayloadError extends Error {
  constructor(status, code, message) {
    super(message)
    this.name = 'VisionPayloadError'
    this.status = status
    this.code = code
  }
}

function requiredPositiveInteger(value, field) {
  if (!Number.isInteger(value) || value <= 0) {
    throw new VisionPayloadError(400, 'INVALID_REQUEST', `${field} debe ser un entero positivo.`)
  }
  return value
}

function validateVisionPayload(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) {
    throw new VisionPayloadError(400, 'INVALID_REQUEST', 'La solicitud de imagen no es valida.')
  }
  if (typeof body.requestId !== 'string' ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.requestId)) {
    throw new VisionPayloadError(400, 'INVALID_REQUEST_ID', 'requestId no es un UUID valido.')
  }
  if (body.mimeType !== 'image/jpeg') {
    throw new VisionPayloadError(415, 'UNSUPPORTED_IMAGE_TYPE', 'Solo se admite imagen JPEG preparada.')
  }
  if (typeof body.imageBase64 !== 'string' ||
      body.imageBase64.length === 0 ||
      body.imageBase64.length % 4 !== 0 ||
      !/^[A-Za-z0-9+/]+={0,2}$/.test(body.imageBase64)) {
    throw new VisionPayloadError(400, 'INVALID_IMAGE', 'La imagen codificada no es valida.')
  }
  const imageBytes = Buffer.from(body.imageBase64, 'base64')
  if (imageBytes.length === 0) {
    throw new VisionPayloadError(400, 'INVALID_IMAGE', 'La imagen preparada esta vacia.')
  }
  if (imageBytes.length > MAX_PREPARED_IMAGE_BYTES) {
    throw new VisionPayloadError(413, 'IMAGE_TOO_LARGE', 'La imagen preparada excede el limite permitido.')
  }
  if (body.preparedBytes !== imageBytes.length) {
    throw new VisionPayloadError(400, 'IMAGE_SIZE_MISMATCH', 'El peso declarado no coincide con la imagen preparada.')
  }
  const quality = requiredPositiveInteger(body.quality, 'quality')
  if (quality > 100) throw new VisionPayloadError(400, 'INVALID_REQUEST', 'quality debe estar entre 1 y 100.')

  return {
    requestId: body.requestId,
    mimeType: body.mimeType,
    imageBytes,
    originalBytes: requiredPositiveInteger(body.originalBytes, 'originalBytes'),
    preparedBytes: body.preparedBytes,
    originalWidth: requiredPositiveInteger(body.originalWidth, 'originalWidth'),
    originalHeight: requiredPositiveInteger(body.originalHeight, 'originalHeight'),
    preparedWidth: requiredPositiveInteger(body.preparedWidth, 'preparedWidth'),
    preparedHeight: requiredPositiveInteger(body.preparedHeight, 'preparedHeight'),
    quality
  }
}

function friendlyProviderError(error) {
  switch (error.code) {
    case 'PROVIDER_UNAVAILABLE':
    case 'CIRCUIT_OPEN':
      return 'Gemini esta temporalmente ocupado. Conservamos tus datos; intenta de nuevo o usa el registro manual/local.'
    case 'PROVIDER_TIMEOUT':
      return 'El analisis tardo demasiado. Conservamos tus datos para que puedas reintentar o registrar manualmente.'
    case 'PROVIDER_RATE_LIMIT':
      return 'Se alcanzo el limite temporal de analisis. Conservamos tus datos; intenta mas tarde.'
    case 'CONCURRENT_REQUEST':
      return 'Ya hay un analisis en curso. Espera su resultado o cancelalo antes de iniciar otro.'
    default:
      return 'No fue posible completar el analisis. Conservamos tus datos para reintentar o registrar manualmente.'
  }
}

function createVisionHandler({ provider, now = Date.now }) {
  return async function handleVision(req, res) {
    const startedAt = now()
    let validated
    let preprocessMs = 0
    try {
      validated = validateVisionPayload(req.body)
      const providerBody = buildGeminiRequest(validated.imageBytes.toString('base64'), validated.mimeType)
      const requestBytes = Buffer.byteLength(JSON.stringify(providerBody), 'utf8')
      preprocessMs = now() - startedAt
      const providerResult = await provider.execute({
        userId: String(req.user?.id || 'anonymous'),
        requestId: validated.requestId,
        providerBody,
        requestBytes,
        imageBytes: validated.preparedBytes
      })
      const parseStartedAt = now()
      const parsed = parseNutritionEnvelope(providerResult.data)
      const parseMs = now() - parseStartedAt
      return res.status(200).json({
        requestId: validated.requestId,
        modelVersion: parsed.modelVersion || provider.model,
        nutrition: parsed.nutrition,
        metrics: {
          preprocessMs,
          providerMs: providerResult.metrics.providerMs,
          parseMs,
          totalMs: now() - startedAt,
          requestBytes: providerResult.metrics.requestBytes,
          originalBytes: validated.originalBytes,
          preparedBytes: validated.preparedBytes,
          originalWidth: validated.originalWidth,
          originalHeight: validated.originalHeight,
          preparedWidth: validated.preparedWidth,
          preparedHeight: validated.preparedHeight,
          quality: validated.quality,
          attempt: providerResult.metrics.attempt,
          cacheHit: providerResult.metrics.cacheHit
        }
      })
    } catch (error) {
      if (error instanceof VisionPayloadError) {
        return res.status(error.status).json({ error: error.message, code: error.code, retryable: false })
      }
      if (error instanceof NutritionValidationError) {
        return res.status(422).json({
          error: 'Gemini devolvio un resultado que no se pudo validar. Tus datos se conservaron.',
          code: 'INVALID_NUTRITION_RESULT',
          retryable: false,
          requestId: validated?.requestId || null
        })
      }
      if (error instanceof ProviderRequestError) {
        if (error.retryAfterMs !== null) {
          res.set('Retry-After', String(Math.ceil(error.retryAfterMs / 1000)))
        }
        return res.status(error.status || 502).json({
          error: friendlyProviderError(error),
          code: error.code,
          retryable: error.retryable,
          requestId: validated?.requestId || null,
          attempt: error.attempt || 0
        })
      }
      console.error('[Gemini Vision Error]', {
        code: 'UNEXPECTED_VISION_ERROR',
        requestId: validated?.requestId || null
      })
      return res.status(500).json({
        error: 'No fue posible completar el analisis. Tus datos se conservaron.',
        code: 'VISION_ERROR',
        retryable: false,
        requestId: validated?.requestId || null
      })
    }
  }
}

async function handleChat(req, res) {
  const apiKey = process.env.GEMINI_API_KEY
  if (!apiKey || apiKey === 'MY_GEMINI_API_KEY' || apiKey.includes('placeholder')) {
    return res.status(500).json({ error: 'La API key de Gemini no esta configurada en el servidor backend.' })
  }
  const controller = new AbortController()
  const timeoutMs = Number(process.env.GEMINI_ATTEMPT_TIMEOUT_MS) || 20_000
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(DEFAULT_MODEL)}:generateContent`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-goog-api-key': apiKey },
        body: JSON.stringify(req.body),
        signal: controller.signal
      }
    )
    const data = await response.json()
    return res.status(response.status).json(data)
  } catch (error) {
    const isTimeout = error?.name === 'AbortError'
    console.error('[Gemini Chat Error]', { code: isTimeout ? 'PROVIDER_TIMEOUT' : 'PROVIDER_NETWORK' })
    return res.status(isTimeout ? 504 : 502).json({
      error: isTimeout ? 'La respuesta de Gemini tardo demasiado.' : 'No fue posible comunicarse con Gemini.'
    })
  } finally {
    clearTimeout(timeoutId)
  }
}

let sharedProvider
function getSharedProvider() {
  if (!sharedProvider) {
    sharedProvider = new GeminiProvider({
      apiKey: process.env.GEMINI_API_KEY,
      model: DEFAULT_MODEL,
      logger: console
    })
  }
  return sharedProvider
}

router.post('/chat', authenticateToken, geminiLimiter, handleChat)
router.post('/vision', authenticateToken, geminiLimiter, (req, res) => {
  const apiKey = process.env.GEMINI_API_KEY
  if (!apiKey || apiKey === 'MY_GEMINI_API_KEY' || apiKey.includes('placeholder')) {
    return res.status(500).json({ error: 'La API key de Gemini no esta configurada en el servidor backend.' })
  }
  return createVisionHandler({ provider: getSharedProvider() })(req, res)
})

router.testing = {
  VisionPayloadError,
  validateVisionPayload,
  createVisionHandler
}

module.exports = router
