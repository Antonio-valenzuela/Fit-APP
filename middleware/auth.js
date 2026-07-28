'use strict'

const jwt = require('jsonwebtoken')

function audit(req, event, fields) {
  req.app?.locals?.securityLogger?.audit?.(event, fields)
}

function unauthorized(req, res, reason) {
  audit(req, 'auth.failure', { reason })
  return res.status(401).json({ error: 'Unauthorized' })
}

function authenticateToken(req, res, next) {
  const authHeader = req.headers?.authorization
  const match = typeof authHeader === 'string' && authHeader.match(/^Bearer\s+(\S+)$/i)
  const token = match?.[1]

  if (!token) {
    return unauthorized(req, res, 'missing_bearer_token')
  }

  const config = req.app?.locals?.securityConfig
  if (!config) return next(new Error('Security configuration unavailable'))

  try {
    const claims = jwt.verify(token, config.jwtSecret, {
      algorithms: config.jwtAlgorithms,
      issuer: config.jwtIssuer,
      audience: config.jwtAudience
    })
    if (typeof claims !== 'object' || typeof claims.sub !== 'string' || !claims.sub.trim() || claims.sub !== claims.sub.trim() || claims.sub.length > 255 || !Number.isFinite(claims.exp)) {
      return unauthorized(req, res, 'invalid_required_claims')
    }
    const roles = Array.isArray(claims.roles)
      ? [...new Set(claims.roles.filter(role => typeof role === 'string' && role.trim()).map(role => role.trim()))]
      : []
    const subject = claims.sub
    req.auth = Object.freeze({ subject, roles: Object.freeze(roles) })
    req.user = Object.freeze({ id: subject, roles: req.auth.roles })
    audit(req, 'auth.success')
    return next()
  } catch (error) {
    return unauthorized(req, res, error?.name === 'TokenExpiredError' ? 'token_expired' : 'token_invalid')
  }
}

async function ensureAuthenticatedPrincipal(req, res, next) {
  const database = req.app?.locals?.database
  if (!database?.query || !req.auth?.subject) {
    return next(new Error('Authenticated principal persistence unavailable'))
  }
  try {
    await database.query(
      'INSERT IGNORE INTO auth_principals (subject) VALUES (?)',
      [req.auth.subject]
    )
    return next()
  } catch (error) {
    return next(error)
  }
}

function requireRole(requiredRole) {
  if (typeof requiredRole !== 'string' || !requiredRole.trim()) {
    throw new TypeError('requiredRole must be a non-empty string')
  }
  return function authorizeRole(req, res, next) {
    if (!req.auth?.roles?.includes(requiredRole)) {
      audit(req, 'authz.denied', { requiredRole })
      return res.status(403).json({ error: 'Forbidden' })
    }
    audit(req, 'authz.granted', { requiredRole })
    return next()
  }
}

module.exports = {
  authenticateToken,
  ensureAuthenticatedPrincipal,
  requireRole
}
