'use strict'

const express = require('express')
const { authenticateToken, ensureAuthenticatedPrincipal, requireRole } = require('../middleware/auth')
const { postAuthRateLimit } = require('../middleware/rateLimits')
const {
  NutritionValidationError,
  buildGeminiRequest,
  buildGeminiTextRequest,
  parseNutritionEnvelope
} = require('../services/geminiNutrition')
const { GeminiProvider, ProviderRequestError } = require('../services/geminiProvider')

const router = express.Router()
const MAX_PREPARED_IMAGE_BYTES = 4 * 1024 * 1024
const DEFAULT_MODEL = 'gemini-3.5-flash'
const BENCHMARK_MODELS = Object.freeze([
  'gemini-3.5-flash',
  'gemini-3.1-flash-lite',
  'gemini-2.5-flash-lite'
])
const imageJsonParser = express.json({ limit: '6mb', strict: true })

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

function requireRequestId(value) {
  if (typeof value !== 'string' ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)) {
    throw new VisionPayloadError(400, 'INVALID_REQUEST_ID', 'requestId no es un UUID valido.')
  }
  return value
}

function validateVisionPayload(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) {
    throw new VisionPayloadError(400, 'INVALID_REQUEST', 'La solicitud de imagen no es valida.')
  }
  const requestId = requireRequestId(body.requestId)
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
    requestId,
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

function validateBenchmarkOptions(body) {
  if (!BENCHMARK_MODELS.includes(body?.model)) {
    throw new VisionPayloadError(400, 'UNSUPPORTED_BENCHMARK_MODEL', 'El modelo solicitado no pertenece a la lista controlada.')
  }
  if (!['minimal', 'medium'].includes(body?.thinkingLevel)) {
    throw new VisionPayloadError(400, 'UNSUPPORTED_THINKING_LEVEL', 'El nivel de thinking solicitado no pertenece a la lista controlada.')
  }
  return { model: body.model, thinkingLevel: body.thinkingLevel }
}

function validateTextBenchmarkPayload(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) {
    throw new VisionPayloadError(400, 'INVALID_REQUEST', 'La solicitud de texto no es valida.')
  }
  const requestId = requireRequestId(body.requestId)
  if (typeof body.description !== 'string' || !body.description.trim() || body.description.length > 1000) {
    throw new VisionPayloadError(400, 'INVALID_TEXT_DESCRIPTION', 'La descripcion debe contener entre 1 y 1000 caracteres.')
  }
  return { requestId, description: body.description.trim(), ...validateBenchmarkOptions(body) }
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

const requireBenchmarkAccess = requireRole('benchmark')

function createVisionHandler({
  provider,
  providerFactory = () => provider,
  allowModelOverride = false,
  now = Date.now
}) {
  return async function handleVision(req, res) {
    const startedAt = now()
    let validated
    let preprocessMs = 0
    try {
      const requestedModel = req.body?.model
      const requestedThinking = req.body?.thinkingLevel
      if (!allowModelOverride && (requestedModel !== undefined || requestedThinking !== undefined)) {
        throw new VisionPayloadError(400, 'BENCHMARK_OVERRIDE_NOT_ALLOWED', 'Los ajustes de benchmark no estan permitidos en este endpoint.')
      }
      if (allowModelOverride) validateBenchmarkOptions(req.body)
      const activeProvider = allowModelOverride ? providerFactory(requestedModel) : provider
      const thinkingLevel = allowModelOverride ? requestedThinking : 'minimal'
      validated = validateVisionPayload(req.body)
      const providerBody = buildGeminiRequest(
        validated.imageBytes.toString('base64'),
        validated.mimeType,
        thinkingLevel
      )
      const requestBytes = Buffer.byteLength(JSON.stringify(providerBody), 'utf8')
      preprocessMs = now() - startedAt
      const providerResult = await activeProvider.execute({
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
        modelVersion: parsed.modelVersion || activeProvider.model,
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
          code: error.code === 'INVALID_PROVIDER_JSON' ? 'INVALID_PROVIDER_JSON' : 'INVALID_NUTRITION_RESULT',
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
      req.app?.locals?.securityLogger?.error?.({
        event: 'gemini.vision.error',
        code: 'UNEXPECTED_VISION_ERROR',
        requestId: req.id
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

function createTextBenchmarkHandler({ providerFactory, now = Date.now }) {
  return async function handleTextBenchmark(req, res) {
    const startedAt = now()
    let validated
    try {
      validated = validateTextBenchmarkPayload(req.body)
      const provider = providerFactory(validated.model)
      const providerBody = buildGeminiTextRequest(validated.description, validated.thinkingLevel)
      const requestBytes = Buffer.byteLength(JSON.stringify(providerBody), 'utf8')
      const providerResult = await provider.execute({
        userId: String(req.user?.id || 'anonymous'),
        requestId: validated.requestId,
        providerBody,
        requestBytes,
        imageBytes: 0
      })
      const parsed = parseNutritionEnvelope(providerResult.data)
      return res.status(200).json({
        requestId: validated.requestId,
        modelVersion: parsed.modelVersion || provider.model,
        nutrition: parsed.nutrition,
        metrics: {
          providerMs: providerResult.metrics.providerMs,
          totalMs: now() - startedAt,
          requestBytes: providerResult.metrics.requestBytes,
          imageBytes: 0,
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
          error: 'Gemini devolvio un resultado que no se pudo validar.',
          code: error.code === 'INVALID_PROVIDER_JSON' ? 'INVALID_PROVIDER_JSON' : 'INVALID_NUTRITION_RESULT',
          retryable: false,
          requestId: validated?.requestId || null
        })
      }
      if (error instanceof ProviderRequestError) {
        if (error.retryAfterMs !== null) res.set('Retry-After', String(Math.ceil(error.retryAfterMs / 1000)))
        return res.status(error.status || 502).json({
          error: friendlyProviderError(error),
          code: error.code,
          retryable: error.retryable,
          requestId: validated?.requestId || null,
          attempt: error.attempt || 0
        })
      }
      req.app?.locals?.securityLogger?.error?.({
        event: 'gemini.text_benchmark.error',
        code: 'UNEXPECTED_TEXT_BENCHMARK_ERROR',
        requestId: req.id
      })
      return res.status(500).json({
        error: 'No fue posible completar el benchmark de texto.',
        code: 'TEXT_BENCHMARK_ERROR',
        retryable: false,
        requestId: validated?.requestId || null
      })
    }
  }
}

async function handleChat(req, res) {
  const config = req.app.locals.securityConfig
  const apiKey = config.geminiApiKey
  const controller = new AbortController()
  const timeoutMs = config.geminiAttemptTimeoutMs ?? 20_000
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs)
  try {
    const response = await fetch(
      `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(config.geminiModel || DEFAULT_MODEL)}:generateContent`,
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
    req.app?.locals?.securityLogger?.warn?.({
      event: 'gemini.chat.error',
      requestId: req.id,
      code: isTimeout ? 'PROVIDER_TIMEOUT' : 'PROVIDER_NETWORK'
    })
    return res.status(isTimeout ? 504 : 502).json({
      error: isTimeout ? 'La respuesta de Gemini tardo demasiado.' : 'No fue posible comunicarse con Gemini.'
    })
  } finally {
    clearTimeout(timeoutId)
  }
}

const sharedProviders = new Map()
function getSharedProvider(apiKey, model = DEFAULT_MODEL, attemptTimeoutMs = 20_000, logger) {
  if (!sharedProviders.has(model)) {
    sharedProviders.set(model, new GeminiProvider({
      apiKey,
      model,
      logger,
      attemptTimeoutMs
    }))
  }
  return sharedProviders.get(model)
}

router.use(authenticateToken, postAuthRateLimit, ensureAuthenticatedPrincipal)

router.post('/chat', handleChat)
router.post('/vision', imageJsonParser, (req, res) => {
  const config = req.app.locals.securityConfig
  return createVisionHandler({
    provider: getSharedProvider(
      config.geminiApiKey,
      config.geminiModel,
      config.geminiAttemptTimeoutMs,
      req.app.locals.securityLogger
    )
  })(req, res)
})
router.post('/benchmark/vision', requireBenchmarkAccess, imageJsonParser, (req, res) => {
  const config = req.app.locals.securityConfig
  return createVisionHandler({
    provider: getSharedProvider(
      config.geminiApiKey,
      config.geminiModel,
      config.geminiAttemptTimeoutMs,
      req.app.locals.securityLogger
    ),
    providerFactory: model => getSharedProvider(
      config.geminiApiKey,
      model,
      config.geminiAttemptTimeoutMs,
      req.app.locals.securityLogger
    ),
    allowModelOverride: true
  })(req, res)
})
router.post('/benchmark/text', requireBenchmarkAccess, (req, res) => {
  const config = req.app.locals.securityConfig
  return createTextBenchmarkHandler({
    providerFactory: model => getSharedProvider(
      config.geminiApiKey,
      model,
      config.geminiAttemptTimeoutMs,
      req.app.locals.securityLogger
    )
  })(req, res)
})

router.testing = {
  VisionPayloadError,
  BENCHMARK_MODELS,
  validateVisionPayload,
  validateTextBenchmarkPayload,
  requireBenchmarkAccess,
  createVisionHandler,
  createTextBenchmarkHandler
}

module.exports = router
