'use strict'

const express = require('express')
const cors = require('cors')
const helmet = require('helmet')
const db = require('../db')
const { registerApiRoutes } = require('../routes')
const { correlationId, createRequestLogger } = require('../middleware/requestContext')
const { createRateLimiters } = require('../middleware/rateLimits')
const { createGoogleTokenVerifier } = require('../services/googleIdentity')

const GLOBAL_JSON_LIMIT_BYTES = 256 * 1024
const IMAGE_JSON_PATHS = new Set([
  '/api/gemini/vision',
  '/api/gemini/benchmark/vision'
])

function corsOptions(config) {
  const allowed = new Set(config.corsAllowedOrigins)
  return {
    origin(origin, callback) {
      if (origin === undefined || allowed.has(origin)) return callback(null, true)
      const error = new Error('CORS origin denied')
      error.code = 'CORS_NOT_ALLOWED'
      error.status = 403
      return callback(error)
    },
    methods: ['GET', 'POST', 'OPTIONS'],
    allowedHeaders: ['Authorization', 'Content-Type', 'X-Correlation-ID'],
    exposedHeaders: ['X-Correlation-ID', 'Retry-After', 'RateLimit', 'RateLimit-Policy'],
    credentials: false,
    optionsSuccessStatus: 204
  }
}

function isImageJsonPath(req) {
  return req.method === 'POST' && IMAGE_JSON_PATHS.has(req.path)
}

function createApp({ securityConfig, securityLogger, database = db, googleTokenVerifier }) {
  if (!securityConfig) throw new TypeError('securityConfig is required')
  if (!securityLogger) throw new TypeError('securityLogger is required')
  if (!database?.query || !database?.transaction) throw new TypeError('database is required')

  const app = express()
  app.disable('x-powered-by')
  app.set('trust proxy', securityConfig.trustProxyHops)
  app.locals.securityConfig = securityConfig
  app.locals.securityLogger = securityLogger
  app.locals.database = database
  app.locals.googleTokenVerifier = googleTokenVerifier || (
    securityConfig.googleOAuthClientId
      ? createGoogleTokenVerifier(securityConfig.googleOAuthClientId)
      : null
  )
  app.locals.rateLimiters = createRateLimiters(securityConfig, securityLogger, database)
  db.configure({ logger: securityLogger })

  app.use(correlationId)
  app.use(createRequestLogger(securityLogger))
  app.use(helmet({ strictTransportSecurity: false }))
  const hsts = helmet.strictTransportSecurity({
    maxAge: 31_536_000,
    includeSubDomains: true
  })
  app.use((req, res, next) => {
    res.set('Permissions-Policy', 'camera=(), microphone=(), geolocation=()')
    if (securityConfig.nodeEnv === 'production' && req.secure) return hsts(req, res, next)
    return next()
  })
  app.use(cors(corsOptions(securityConfig)))
  app.use(app.locals.rateLimiters.preAuth)
  app.use('/api', (req, res, next) => {
    res.set('Cache-Control', 'private, no-store')
    next()
  })
  app.use((req, res, next) => {
    if (['POST', 'PUT', 'PATCH'].includes(req.method) && !req.is('application/json')) {
      return res.status(415).json({ error: 'Unsupported Media Type' })
    }
    return next()
  })

  const smallJsonParser = express.json({ limit: GLOBAL_JSON_LIMIT_BYTES, strict: true })
  app.use((req, res, next) => isImageJsonPath(req) ? next() : smallJsonParser(req, res, next))

  registerApiRoutes(app)

  app.get('/', (req, res) => {
    res.json({ ok: true, message: 'FitPlan MX Backend API Proxy is running.' })
  })
  app.get('/health', (req, res) => {
    res.json({ status: 'UP', timestamp: new Date().toISOString() })
  })

  app.use((req, res) => res.status(404).json({ error: 'Not Found' }))
  app.use((err, req, res, next) => {
    if (res.headersSent) return next(err)
    if (err?.type === 'entity.too.large' || err?.status === 413) {
      return res.status(413).json({ error: 'Payload Too Large' })
    }
    if (err?.code === 'CORS_NOT_ALLOWED') {
      return res.status(403).json({ error: 'Forbidden' })
    }
    if (err instanceof SyntaxError && err?.status === 400) {
      return res.status(400).json({ error: 'Bad Request' })
    }
    securityLogger.error?.({
      event: 'http.error',
      requestId: req.id,
      errorType: err?.name || 'Error'
    })
    return res.status(500).json({ error: 'Internal Server Error' })
  })

  return app
}

module.exports = {
  GLOBAL_JSON_LIMIT_BYTES,
  createApp
}
