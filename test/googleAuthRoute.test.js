'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { once } = require('node:events')
const jwt = require('jsonwebtoken')

const { createApp } = require('../server/app')

const logger = Object.freeze({ info() {}, warn() {}, error() {}, audit() {} })

function config() {
  return Object.freeze({
    nodeEnv: 'test',
    jwtSecret: 'a-strong-signing-key-with-at-least-32-characters',
    jwtIssuer: 'https://identity.fitplan.mx',
    jwtAudience: 'fitplan-backend',
    jwtAlgorithms: Object.freeze(['HS256']),
    jwtTtlSeconds: 900,
    googleOAuthClientId: '123456789.apps.googleusercontent.com',
    geminiApiKey: 'provider-key',
    corsAllowedOrigins: Object.freeze(['https://app.fitplan.mx']),
    trustProxyHops: 0,
    preAuthRateLimitWindowMs: 60_000,
    preAuthRateLimitMax: 100,
    userRateLimitWindowMs: 60_000,
    userRateLimitMax: 100,
    geminiUserRateLimitWindowMs: 60_000,
    geminiUserRateLimitMax: 20,
    rateLimitStore: 'memory'
  })
}

async function postGoogle({ verifier, body, database: suppliedDatabase, securityLogger = logger }) {
  const writes = []
  const database = suppliedDatabase || {
    async query(sql, params) {
      writes.push({ sql, params })
      if (/SELECT[\s\S]+token_version/i.test(sql)) return [[{ token_version: 1 }]]
      return [{ affectedRows: 1 }]
    },
    async transaction(work) { return work({ query: this.query.bind(this) }) }
  }
  const app = createApp({
    securityConfig: config(),
    securityLogger,
    database,
    googleTokenVerifier: verifier
  })
  const server = app.listen(0, '127.0.0.1')
  await once(server, 'listening')
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/api/auth/google`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Correlation-ID': 'request-google-1234' },
      body: JSON.stringify(body)
    })
    return { response, writes }
  } finally {
    server.close()
    await once(server, 'close')
  }
}

test('verifies a Google ID token and emits a minimal internal HS256 JWT', async () => {
  const seen = []
  const { response, writes } = await postGoogle({
    verifier: { async verify(idToken) { seen.push(idToken); return { subject: 'google:opaque-sub-123' } } },
    body: { idToken: 'signed-google-id-token' }
  })

  assert.equal(response.status, 200)
  const body = await response.json()
  assert.deepEqual(Object.keys(body).sort(), ['accessToken', 'expiresIn', 'subject', 'tokenType'])
  assert.equal(body.subject, 'google:opaque-sub-123')
  assert.deepEqual(seen, ['signed-google-id-token'])
  const claims = jwt.verify(body.accessToken, config().jwtSecret, {
    algorithms: ['HS256'], issuer: config().jwtIssuer, audience: config().jwtAudience
  })
  assert.equal(claims.sub, 'google:opaque-sub-123')
  assert.deepEqual(claims.roles, ['member'])
  assert.equal(claims.ver, 1)
  assert.equal(writes.some(write => /auth_principals/i.test(write.sql) && write.params[0] === claims.sub), true)
})

test('reactivates a verified Google tombstone by incrementing its version before issuing a JWT', async () => {
  const writes = []
  const database = {
    async query(sql, params) {
      writes.push({ sql, params })
      if (/SELECT[\s\S]+token_version/i.test(sql)) return [[{ token_version: 6 }]]
      return [{ affectedRows: 1 }]
    },
    async transaction(work) { return work({ query: this.query.bind(this) }) }
  }

  const { response } = await postGoogle({
    verifier: { async verify() { return { subject: 'google:reactivated-subject' } } },
    body: { idToken: 'verified-google-token' },
    database
  })

  assert.equal(response.status, 200)
  const claims = jwt.verify((await response.json()).accessToken, config().jwtSecret, {
    algorithms: ['HS256'], issuer: config().jwtIssuer, audience: config().jwtAudience
  })
  assert.equal(claims.ver, 6)
  assert.match(writes[0].sql, /ON DUPLICATE KEY UPDATE/i)
  assert.match(writes[0].sql, /deleted_at\s*=\s*NULL/i)
  assert.match(writes[0].sql, /token_version\s*\+\s*1/i)
})

test('includes the correlation ID in Google authentication audit events', async () => {
  const events = []
  const securityLogger = {
    info() {}, warn() {}, error() {},
    audit(event, fields) { events.push({ event, fields }) }
  }

  const { response } = await postGoogle({
    verifier: { async verify() { return { subject: 'google:audit-subject' } } },
    body: { idToken: 'verified-google-token' },
    securityLogger
  })

  assert.equal(response.status, 200)
  assert.equal(events.at(-1).event, 'auth.google.success')
  assert.equal(events.at(-1).fields.requestId, 'request-google-1234')
})

test('returns a generic 401 and creates no principal when Google verification fails', async () => {
  const { response, writes } = await postGoogle({
    verifier: { async verify() { throw new Error('provider detail user@example.com') } },
    body: { idToken: 'invalid-google-token' }
  })

  assert.equal(response.status, 401)
  assert.deepEqual(await response.json(), { error: 'Unauthorized' })
  assert.equal(writes.length, 0)
})

test('returns 503 rather than misclassifying a database outage as an invalid Google token', async () => {
  const database = {
    async query() { throw new Error('database unavailable') },
    async transaction(work) { return work({ query: this.query.bind(this) }) }
  }
  const app = createApp({
    securityConfig: config(), securityLogger: logger, database,
    googleTokenVerifier: { async verify() { return { subject: 'google:valid-subject' } } }
  })
  const server = app.listen(0, '127.0.0.1')
  await once(server, 'listening')
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/api/auth/google`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ idToken: 'valid' })
    })
    assert.equal(response.status, 503)
  } finally {
    server.close()
    await once(server, 'close')
  }
})

test('accepts only an idToken field and never trusts client-supplied profile data', async () => {
  let verifierCalls = 0
  const { response, writes } = await postGoogle({
    verifier: { async verify() { verifierCalls += 1; return { subject: 'must-not-run' } } },
    body: { idToken: 'signed-google-id-token', email: 'attacker@example.com' }
  })

  assert.equal(response.status, 400)
  assert.equal(verifierCalls, 0)
  assert.equal(writes.length, 0)
})
