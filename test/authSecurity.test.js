'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const jwt = require('jsonwebtoken')

const { authenticateToken, ensureAuthenticatedPrincipal, requireRole } = require('../middleware/auth')

const securityConfig = Object.freeze({
  jwtSecret: 'a-strong-signing-key-with-at-least-32-characters',
  jwtIssuer: 'https://identity.fitplan.mx',
  jwtAudience: 'fitplan-backend',
  jwtAlgorithms: ['HS256']
})

function request(token, auditEvents = []) {
  return {
    headers: token ? { authorization: `Bearer ${token}` } : {},
    app: { locals: { securityConfig, securityLogger: { audit: (event, fields) => auditEvents.push({ event, fields }) } } },
    id: 'request-auth-1234',
    ip: '127.0.0.1',
    method: 'GET',
    originalUrl: '/protected'
  }
}

function responseRecorder() {
  return {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this },
    json(value) { this.body = value; return this }
  }
}

function sign(claims = {}, options = {}) {
  return jwt.sign(
    { sub: 'user-123', roles: ['member'], ver: 1, ...claims },
    securityConfig.jwtSecret,
    {
      algorithm: 'HS256',
      issuer: securityConfig.jwtIssuer,
      audience: securityConfig.jwtAudience,
      expiresIn: '5m',
      ...options
    }
  )
}

function authenticate(req) {
  const res = responseRecorder()
  let nextCalls = 0
  authenticateToken(req, res, () => { nextCalls += 1 })
  return { res, nextCalls }
}

test('authenticates a valid JWT into a minimal principal', () => {
  const req = request(sign())
  const result = authenticate(req)

  assert.equal(result.nextCalls, 1)
  assert.deepEqual(req.auth, { subject: 'user-123', roles: ['member'], tokenVersion: 1 })
  assert.deepEqual(req.user, { id: 'user-123', roles: ['member'] })
})

test('requires a positive integer token version claim', () => {
  const invalidTokens = [
    sign({ ver: 0 }),
    sign({ ver: -1 }),
    sign({ ver: 1.5 }),
    sign({ ver: '1' }),
    jwt.sign(
      { sub: 'user-123', roles: ['member'] },
      securityConfig.jwtSecret,
      { algorithm: 'HS256', issuer: securityConfig.jwtIssuer, audience: securityConfig.jwtAudience, expiresIn: '5m' }
    )
  ]

  for (const token of invalidTokens) {
    const result = authenticate(request(token))
    assert.equal(result.nextCalls, 0)
    assert.equal(result.res.statusCode, 401)
  }
})

test('rejects the former static backend API key as authentication', () => {
  const result = authenticate(request('former-static-key-value'))

  assert.equal(result.nextCalls, 0)
  assert.equal(result.res.statusCode, 401)
  assert.deepEqual(result.res.body, { error: 'Unauthorized' })
})

test('rejects JWTs with an unapproved algorithm, issuer or audience', () => {
  const invalidTokens = [
    jwt.sign({ sub: 'user-123' }, securityConfig.jwtSecret, {
      algorithm: 'HS384', issuer: securityConfig.jwtIssuer, audience: securityConfig.jwtAudience, expiresIn: '5m'
    }),
    sign({}, { issuer: 'https://attacker.invalid' }),
    sign({}, { audience: 'other-service' })
  ]

  for (const token of invalidTokens) {
    const result = authenticate(request(token))
    assert.equal(result.nextCalls, 0)
    assert.equal(result.res.statusCode, 401)
    assert.deepEqual(result.res.body, { error: 'Unauthorized' })
  }
})

test('requires non-empty subject and explicit expiration claims', () => {
  const missingSubject = jwt.sign(
    { roles: ['member'] },
    securityConfig.jwtSecret,
    { algorithm: 'HS256', issuer: securityConfig.jwtIssuer, audience: securityConfig.jwtAudience, expiresIn: '5m' }
  )
  const missingExpiration = jwt.sign(
    { sub: 'user-123', roles: ['member'] },
    securityConfig.jwtSecret,
    { algorithm: 'HS256', issuer: securityConfig.jwtIssuer, audience: securityConfig.jwtAudience }
  )
  const blankSubject = sign({ sub: '   ' })

  for (const token of [missingSubject, missingExpiration, blankSubject]) {
    const result = authenticate(request(token))
    assert.equal(result.nextCalls, 0)
    assert.equal(result.res.statusCode, 401)
  }
})

test('rejects subjects that cannot fit the canonical textual database identity', () => {
  const result = authenticate(request(sign({ sub: 'x'.repeat(256) })))

  assert.equal(result.nextCalls, 0)
  assert.equal(result.res.statusCode, 401)
})

test('rejects subjects whose identity would change through whitespace normalization', () => {
  for (const subject of [' user-1', 'user-1 ']) {
    const result = authenticate(request(sign({ sub: subject })))
    assert.equal(result.nextCalls, 0)
    assert.equal(result.res.statusCode, 401)
  }
})

test('loads an active principal and matches the persisted token version before protected data access', async () => {
  const calls = []
  const req = {
    id: 'request-principal-active',
    auth: { subject: 'opaque-google-subject-001', roles: ['member'], tokenVersion: 7 },
    app: { locals: {
      securityLogger: { audit() {} },
      database: {
        query: async (sql, params) => {
          calls.push({ sql, params })
          return [[{ token_version: 7, deleted_at: null }]]
        }
      }
    } }
  }
  let nextError = 'not-called'

  await ensureAuthenticatedPrincipal(req, responseRecorder(), error => { nextError = error })

  assert.equal(nextError, undefined)
  assert.equal(calls.length, 1)
  assert.match(calls[0].sql, /SELECT[\s\S]+token_version[\s\S]+deleted_at[\s\S]+FROM auth_principals/i)
  assert.doesNotMatch(calls[0].sql, /INSERT/i)
  assert.deepEqual(calls[0].params, ['opaque-google-subject-001'])
})

test('rejects deleted, missing, and version-mismatched principals without recreating them', async () => {
  const rowsByCase = [
    [],
    [{ token_version: 8, deleted_at: new Date().toISOString() }],
    [{ token_version: 9, deleted_at: null }]
  ]

  for (const rows of rowsByCase) {
    const calls = []
    const req = {
      id: 'request-principal-rejected',
      auth: { subject: 'opaque-google-subject-001', roles: ['member'], tokenVersion: 8 },
      app: { locals: {
        securityLogger: { audit() {} },
        database: { query: async (sql, params) => { calls.push({ sql, params }); return [rows] } }
      } }
    }
    const res = responseRecorder()
    let nextCalls = 0

    await ensureAuthenticatedPrincipal(req, res, () => { nextCalls += 1 })

    assert.equal(nextCalls, 0)
    assert.equal(res.statusCode, 401)
    assert.equal(calls.length, 1)
    assert.doesNotMatch(calls[0].sql, /INSERT/i)
  }
})

test('includes requestId in every authentication and authorization audit event', () => {
  const auditEvents = []
  authenticate(request(null, auditEvents))
  authenticate(request(sign(), auditEvents))
  const roleReq = request(null, auditEvents)
  roleReq.auth = { subject: 'user-123', roles: ['member'], tokenVersion: 1 }
  requireRole('benchmark')(roleReq, responseRecorder(), () => {})

  assert.deepEqual(auditEvents.map(item => item.event), ['auth.failure', 'auth.success', 'authz.denied'])
  for (const item of auditEvents) assert.equal(item.fields.requestId, 'request-auth-1234')
})

test('rejects expired JWTs with the same generic response', () => {
  const token = sign({}, { expiresIn: -1 })
  const result = authenticate(request(token))

  assert.equal(result.nextCalls, 0)
  assert.equal(result.res.statusCode, 401)
  assert.deepEqual(result.res.body, { error: 'Unauthorized' })
})

test('authorization checks roles separately from authentication', () => {
  const middleware = requireRole('benchmark')
  const denied = responseRecorder()
  let nextCalls = 0

  middleware({ auth: { subject: 'member-1', roles: ['member'] } }, denied, () => { nextCalls += 1 })
  assert.equal(denied.statusCode, 403)
  assert.deepEqual(denied.body, { error: 'Forbidden' })

  middleware({ auth: { subject: 'operator-1', roles: ['benchmark'] } }, responseRecorder(), () => { nextCalls += 1 })
  assert.equal(nextCalls, 1)
})
