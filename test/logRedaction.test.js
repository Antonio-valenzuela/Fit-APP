'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { once } = require('node:events')
const jwt = require('jsonwebtoken')

const { createSecurityLogger } = require('../services/securityLogger')
const { createApp } = require('../server/app')

function config(overrides = {}) {
  return {
    nodeEnv: 'test',
    port: 3000,
    jwtSecret: 'a-strong-signing-key-with-at-least-32-characters',
    jwtIssuer: 'https://identity.fitplan.mx',
    jwtAudience: 'fitplan-backend',
    jwtAlgorithms: ['HS256'],
    geminiApiKey: 'provider-key-0123456789abcdef',
    geminiModel: 'gemini-3.5-flash',
    geminiAttemptTimeoutMs: 20_000,
    corsAllowedOrigins: ['https://app.fitplan.mx'],
    trustProxyHops: 0,
    preAuthRateLimitWindowMs: 60_000,
    preAuthRateLimitMax: 100,
    userRateLimitWindowMs: 60_000,
    userRateLimitMax: 100,
    ...overrides
  }
}

function token(roles = ['member'], subject = 'opaque-user-123') {
  return jwt.sign(
    { sub: subject, roles },
    config().jwtSecret,
    {
      algorithm: 'HS256',
      issuer: config().jwtIssuer,
      audience: config().jwtAudience,
      expiresIn: '5m'
    }
  )
}

function testDatabase(execute = async sql => /SELECT/i.test(sql) ? [[]] : [{ affectedRows: 1 }]) {
  return {
    query: execute,
    transaction: async work => work({ query: execute })
  }
}

test('writes one-line structured events using only allowlisted fields', () => {
  const lines = []
  const logger = createSecurityLogger({ sink: line => lines.push(line), now: () => new Date('2026-07-19T12:00:00.000Z') })

  logger.info({
    event: 'http.request',
    requestId: 'request-1234',
    method: 'GET',
    path: '/health\r\nFORGED',
    status: 200,
    password: 'password-value-that-must-not-appear',
    arbitrary: 'not-allowlisted'
  })

  assert.equal(lines.length, 1)
  assert.equal(lines[0].includes('\n'), false)
  const parsed = JSON.parse(lines[0])
  assert.equal(parsed.level, 'info')
  assert.equal(parsed.event, 'http.request')
  assert.equal(/[\r\n]/.test(parsed.path), false)
  assert.equal(Object.hasOwn(parsed, 'password'), false)
  assert.equal(Object.hasOwn(parsed, 'arbitrary'), false)
})

test('redacts token, email, medical and GPS values from audit output', () => {
  const lines = []
  const logger = createSecurityLogger({ sink: line => lines.push(line) })
  const secrets = [
    'Bearer header.payload.signature',
    'patient@example.com',
    'heart_rate=187',
    '19.4326,-99.1332'
  ]

  logger.audit('auth.failure', {
    reason: 'token_invalid',
    subject: secrets[1],
    token: secrets[0],
    medicalData: secrets[2],
    gps: secrets[3]
  })

  const output = lines.join('')
  for (const secret of secrets) assert.equal(output.includes(secret), false)
  assert.equal(Object.hasOwn(JSON.parse(lines[0]), 'subject'), false)
})

test('request logging never passes the client IP to the logger sink', async () => {
  const records = []
  const logger = {
    info: value => records.push(value),
    warn: value => records.push(value),
    error: value => records.push(value),
    audit: (event, value) => records.push({ event, ...value })
  }
  const app = createApp({ securityConfig: config({ trustProxyHops: 1 }), securityLogger: logger, database: testDatabase() })
  const server = app.listen(0, '127.0.0.1')
  await once(server, 'listening')
  try {
    await fetch(`http://127.0.0.1:${server.address().port}/health`, {
      headers: { 'X-Forwarded-For': '203.0.113.99' }
    })
  } finally {
    server.close()
    await once(server, 'close')
  }

  const requestRecord = records.find(record => record.event === 'http.request')
  assert.equal(Object.hasOwn(requestRecord, 'ip'), false)
  assert.equal(JSON.stringify(records).includes('203.0.113.99'), false)
})

test('emits authentication, authorization and rate-limit audit events', async () => {
  const lines = []
  const logger = createSecurityLogger({ sink: line => lines.push(line) })
  const app = createApp({
    securityConfig: config({ preAuthRateLimitMax: 20, userRateLimitMax: 1 }),
    securityLogger: logger,
    database: testDatabase()
  })
  const server = app.listen(0, '127.0.0.1')
  await once(server, 'listening')
  const baseUrl = `http://127.0.0.1:${server.address().port}`
  try {
    await fetch(`${baseUrl}/api/health/summary`, { headers: { Authorization: 'Bearer static-secret-value' } })
    await fetch(`${baseUrl}/api/gemini/benchmark/text`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token()}`, 'Content-Type': 'application/json' },
      body: '{}'
    })
    await fetch(`${baseUrl}/api/gemini/benchmark/text`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token(['benchmark'], 'opaque-operator-456')}`, 'Content-Type': 'application/json' },
      body: '{}'
    })
    await fetch(`${baseUrl}/api/health/summary`, { headers: { Authorization: `Bearer ${token()}` } })
    await fetch(`${baseUrl}/api/health/summary`, { headers: { Authorization: `Bearer ${token()}` } })
  } finally {
    server.close()
    await once(server, 'close')
  }

  const events = lines.map(line => JSON.parse(line).event)
  assert.equal(events.includes('auth.failure'), true)
  assert.equal(events.includes('authz.denied'), true)
  assert.equal(events.includes('authz.granted'), true)
  assert.equal(events.includes('rate_limit.exceeded'), true)
  assert.equal(lines.join('').includes('static-secret-value'), false)
})

test('routes report database failures without console output or sensitive exception text', async () => {
  const lines = []
  const consoleCalls = []
  const originalConsoleError = console.error
  const logger = createSecurityLogger({ sink: line => lines.push(line) })
  const database = testDatabase(async sql => {
    if (/auth_principals/i.test(sql)) return [{ affectedRows: 1 }]
    throw new Error('patient@example.com heart_rate=187')
  })
  console.error = (...args) => consoleCalls.push(args)
  const app = createApp({ securityConfig: config(), securityLogger: logger, database })
  const server = app.listen(0, '127.0.0.1')
  await once(server, 'listening')
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/api/health/summary`, {
      headers: { Authorization: `Bearer ${token()}` }
    })
    assert.equal(response.status, 500)
    assert.deepEqual(await response.json(), { error: 'Error al obtener resumen de salud' })
  } finally {
    server.close()
    await once(server, 'close')
    console.error = originalConsoleError
  }

  assert.equal(consoleCalls.length, 0)
  assert.equal(lines.some(line => JSON.parse(line).event === 'health.summary.error'), true)
  assert.equal(lines.join('').includes('patient@example.com'), false)
  assert.equal(lines.join('').includes('heart_rate=187'), false)
})

test('consent audit persistence and logs omit IP and user agent', async () => {
  const queries = []
  const lines = []
  const logger = createSecurityLogger({ sink: line => lines.push(line) })
  const database = testDatabase(async (sql, params) => {
      queries.push({ sql, params })
      return [[]]
  })
  const app = createApp({ securityConfig: config({ trustProxyHops: 1 }), securityLogger: logger, database })
  const server = app.listen(0, '127.0.0.1')
  await once(server, 'listening')
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/api/health/consent`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token()}`,
        'Content-Type': 'application/json',
        'User-Agent': 'sensitive-device-fingerprint',
        'X-Forwarded-For': '203.0.113.88'
      },
      body: JSON.stringify({ platform: 'android_hc', status: 'granted' })
    })
    assert.equal(response.status, 200)
  } finally {
    server.close()
    await once(server, 'close')
  }

  const persistence = JSON.stringify(queries)
  assert.equal(/ip_address|user_agent/i.test(persistence), false)
  assert.equal(persistence.includes('sensitive-device-fingerprint'), false)
  assert.equal(persistence.includes('203.0.113.88'), false)
  assert.equal(lines.some(line => JSON.parse(line).event === 'health.consent.changed'), true)
  assert.equal(lines.join('').includes('sensitive-device-fingerprint'), false)
  assert.equal(lines.join('').includes('203.0.113.88'), false)
})
