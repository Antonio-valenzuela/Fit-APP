'use strict'

const { rateLimit } = require('express-rate-limit')
const { MySqlRateLimitStore } = require('../services/mysqlRateLimitStore')

function limitedResponse(req, res, logger, scope) {
  logger.audit?.('rate_limit.exceeded', {
    scope,
    requestId: req.id,
    method: req.method,
    path: req.originalUrl?.split('?')[0]
  })
  return res.status(429).json({ error: 'Too Many Requests' })
}

function canonicalEndpoint(req) {
  let pathname = `${req.baseUrl || ''}${req.path || ''}`
  try {
    pathname = decodeURIComponent(pathname)
  } catch {}
  pathname = pathname.toLowerCase().replace(/\/{2,}/g, '/').replace(/\/$/, '') || '/'
  return `${req.method.toUpperCase()}:${pathname}`
}

function storeFor(config, database, namespace) {
  return config.rateLimitStore === 'mysql'
    ? new MySqlRateLimitStore({ database, namespace })
    : undefined
}

function createRateLimiters(config, logger, database) {
  const preAuth = rateLimit({
    windowMs: config.preAuthRateLimitWindowMs ?? 60_000,
    max: config.preAuthRateLimitMax ?? 120,
    standardHeaders: true,
    legacyHeaders: false,
    handler: (req, res) => limitedResponse(req, res, logger, 'ip'),
    store: storeFor(config, database, 'pre_auth')
  })

  const userEndpoint = rateLimit({
    windowMs: config.userRateLimitWindowMs ?? 60_000,
    max: config.userRateLimitMax ?? 30,
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: req => `${req.auth.subject}:${canonicalEndpoint(req)}`,
    handler: (req, res) => limitedResponse(req, res, logger, 'user_endpoint'),
    store: storeFor(config, database, 'user_endpoint')
  })

  const geminiUserEndpoint = rateLimit({
    windowMs: config.geminiUserRateLimitWindowMs ?? 60_000,
    max: config.geminiUserRateLimitMax ?? 10,
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: req => `${req.auth.subject}:${canonicalEndpoint(req)}`,
    handler: (req, res) => limitedResponse(req, res, logger, 'gemini_user_endpoint'),
    store: storeFor(config, database, 'gemini_user_endpoint')
  })

  return Object.freeze({ preAuth, userEndpoint, geminiUserEndpoint })
}

function postAuthRateLimit(req, res, next) {
  const rateLimiters = req.app?.locals?.rateLimiters
  const limiter = req.baseUrl === '/api/gemini'
    ? rateLimiters?.geminiUserEndpoint
    : rateLimiters?.userEndpoint
  if (!limiter) return next(new Error('Authenticated rate limiter unavailable'))
  return limiter(req, res, next)
}

module.exports = {
  canonicalEndpoint,
  createRateLimiters,
  postAuthRateLimit
}
