'use strict'

const express = require('express')
const jwt = require('jsonwebtoken')

const router = express.Router()

function validTokenRequest(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) return false
  const keys = Object.keys(body)
  return keys.length === 1 && keys[0] === 'idToken' &&
    typeof body.idToken === 'string' && body.idToken.length > 0 && body.idToken.length <= 8192
}

router.post('/google', async (req, res) => {
  if (!validTokenRequest(req.body)) {
    return res.status(400).json({ error: 'Bad Request' })
  }

  const config = req.app.locals.securityConfig
  const verifier = req.app.locals.googleTokenVerifier
  const database = req.app.locals.database
  if (!verifier?.verify || !database?.transaction) {
    return res.status(503).json({ error: 'Service Unavailable' })
  }

  let identity
  try {
    identity = await verifier.verify(req.body.idToken)
    if (typeof identity?.subject !== 'string' || !identity.subject.trim() || identity.subject.trim().length > 255) {
      throw new Error('Invalid identity')
    }
  } catch {
    req.app.locals.securityLogger.audit?.('auth.google.failure', { reason: 'invalid_google_id_token' })
    return res.status(401).json({ error: 'Unauthorized' })
  }

  try {
    const subject = identity.subject.trim()
    await database.transaction(tx => tx.query(
      'INSERT IGNORE INTO auth_principals (subject) VALUES (?)',
      [subject]
    ))
    const accessToken = jwt.sign(
      { roles: ['member'] },
      config.jwtSecret,
      {
        algorithm: 'HS256',
        issuer: config.jwtIssuer,
        audience: config.jwtAudience,
        subject,
        expiresIn: config.jwtTtlSeconds
      }
    )
    req.app.locals.securityLogger.audit?.('auth.google.success')
    return res.status(200).json({
      accessToken,
      tokenType: 'Bearer',
      expiresIn: config.jwtTtlSeconds,
      subject
    })
  } catch {
    req.app.locals.securityLogger.error?.({ event: 'auth.google.persistence_failed', errorType: 'DatabaseError' })
    return res.status(503).json({ error: 'Service Unavailable' })
  }
})

module.exports = router
