'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { once } = require('node:events')
const jwt = require('jsonwebtoken')

const { createApp } = require('../server/app')

const silentLogger = Object.freeze({
  info() {},
  warn() {},
  error() {},
  audit() {}
})

function securityConfig(overrides = {}) {
  return Object.freeze({
    nodeEnv: 'test',
    port: 3000,
    jwtSecret: 'a-strong-signing-key-with-at-least-32-characters',
    jwtIssuer: 'https://identity.fitplan.mx',
    jwtAudience: 'fitplan-backend',
    jwtAlgorithms: Object.freeze(['HS256']),
    geminiApiKey: 'provider-key-0123456789abcdef',
    corsAllowedOrigins: Object.freeze(['https://app.fitplan.mx']),
    trustProxyHops: 1,
    preAuthRateLimitWindowMs: 60_000,
    preAuthRateLimitMax: 100,
    userRateLimitWindowMs: 60_000,
    userRateLimitMax: 100,
    geminiUserRateLimitWindowMs: 60_000,
    geminiUserRateLimitMax: 20,
    rateLimitStore: 'memory',
    ...overrides
  })
}

function testDatabase() {
  return {
    query: async sql => /SELECT/i.test(sql) ? [[]] : [{ affectedRows: 1 }],
    transaction: async work => work({ query: async () => [{ affectedRows: 1 }] })
  }
}

function bearer(subject = 'user-1') {
  return jwt.sign(
    { sub: subject, roles: ['member'] },
    'a-strong-signing-key-with-at-least-32-characters',
    {
      algorithm: 'HS256',
      issuer: 'https://identity.fitplan.mx',
      audience: 'fitplan-backend',
      expiresIn: '5m'
    }
  )
}

async function withServer(config, callback) {
  const app = createApp({ securityConfig: config, securityLogger: silentLogger, database: testDatabase() })
  const server = app.listen(0, '127.0.0.1')
  await once(server, 'listening')
  const address = server.address()
  try {
    await callback({ app, baseUrl: `http://127.0.0.1:${address.port}` })
  } finally {
    server.close()
    await once(server, 'close')
  }
}

test('allows configured browser origins and rejects other browser origins', async () => {
  await withServer(securityConfig(), async ({ baseUrl }) => {
    const allowed = await fetch(`${baseUrl}/health`, { headers: { Origin: 'https://app.fitplan.mx' } })
    assert.equal(allowed.status, 200)
    assert.equal(allowed.headers.get('access-control-allow-origin'), 'https://app.fitplan.mx')

    const denied = await fetch(`${baseUrl}/health`, { headers: { Origin: 'https://attacker.invalid' } })
    assert.equal(denied.status, 403)
    assert.deepEqual(await denied.json(), { error: 'Forbidden' })
  })
})

test('preflight advertises only approved methods and request headers', async () => {
  await withServer(securityConfig(), async ({ baseUrl }) => {
    const response = await fetch(`${baseUrl}/api/health/summary`, {
      method: 'OPTIONS',
      headers: {
        Origin: 'https://app.fitplan.mx',
        'Access-Control-Request-Method': 'POST',
        'Access-Control-Request-Headers': 'authorization,content-type,x-correlation-id'
      }
    })

    assert.equal(response.status, 204)
    assert.equal(response.headers.get('access-control-allow-methods'), 'GET,POST,DELETE,OPTIONS')
    assert.equal(response.headers.get('access-control-allow-headers'), 'Authorization,Content-Type,X-Correlation-ID')
    assert.equal(response.headers.get('access-control-allow-methods').includes('DELETE'), true)
  })
})

test('allows native requests without an Origin header', async () => {
  await withServer(securityConfig(), async ({ baseUrl }) => {
    const response = await fetch(`${baseUrl}/health`)

    assert.equal(response.status, 200)
    assert.equal(response.headers.get('access-control-allow-origin'), null)
  })
})

test('sets defensive HTTP headers without exposing Express', async () => {
  await withServer(securityConfig(), async ({ baseUrl }) => {
    const response = await fetch(`${baseUrl}/health`)

    assert.equal(response.status, 200)
    assert.equal(response.headers.get('x-powered-by'), null)
    assert.equal(response.headers.get('x-content-type-options'), 'nosniff')
    assert.equal(response.headers.get('content-security-policy') !== null, true)
    assert.equal(response.headers.get('permissions-policy'), 'camera=(), microphone=(), geolocation=()')
    assert.equal(response.headers.get('strict-transport-security'), null)
  })
})

test('sets HSTS only for production requests known to be HTTPS', async () => {
  await withServer(securityConfig({ nodeEnv: 'production', trustProxyHops: 1 }), async ({ baseUrl }) => {
    const httpResponse = await fetch(`${baseUrl}/health`)
    const httpsAtProxy = await fetch(`${baseUrl}/health`, {
      headers: { 'X-Forwarded-Proto': 'https' }
    })

    assert.equal(httpResponse.headers.get('strict-transport-security'), null)
    assert.match(httpsAtProxy.headers.get('strict-transport-security'), /^max-age=31536000;/)
  })
})

test('returns a safe correlation ID and preserves a valid incoming ID', async () => {
  await withServer(securityConfig(), async ({ baseUrl }) => {
    const supplied = 'request-1234-safe'
    const preserved = await fetch(`${baseUrl}/health`, { headers: { 'X-Correlation-ID': supplied } })
    const generated = await fetch(`${baseUrl}/health`, { headers: { 'X-Correlation-ID': 'not valid spaces' } })

    assert.equal(preserved.headers.get('x-correlation-id'), supplied)
    assert.match(generated.headers.get('x-correlation-id'), /^[0-9a-f-]{36}$/)
  })
})

test('configures trust proxy as a hop count and never boolean true', () => {
  const app = createApp({ securityConfig: securityConfig({ trustProxyHops: 2 }), securityLogger: silentLogger, database: testDatabase() })

  assert.equal(app.get('trust proxy'), 2)
  assert.notEqual(app.get('trust proxy'), true)
})

test('rejects ordinary JSON bodies larger than 256 KB with a generic 413', async () => {
  await withServer(securityConfig(), async ({ baseUrl }) => {
    const response = await fetch(`${baseUrl}/api/gemini/chat`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${bearer()}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ text: 'x'.repeat(300 * 1024) })
    })

    assert.equal(response.status, 413)
    assert.deepEqual(await response.json(), { error: 'Payload Too Large' })
  })
})

test('rejects non-JSON POST bodies with a generic 415 before route parsers', async () => {
  await withServer(securityConfig(), async ({ baseUrl }) => {
    const response = await fetch(`${baseUrl}/api/gemini/vision`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${bearer()}`,
        'Content-Type': 'text/plain'
      },
      body: 'not-json'
    })

    assert.equal(response.status, 415)
    assert.deepEqual(await response.json(), { error: 'Unsupported Media Type' })
  })
})

test('uses the larger JSON parser only on image endpoints after authentication', async () => {
  await withServer(securityConfig(), async ({ baseUrl }) => {
    const bytes = Buffer.alloc(300 * 1024, 1)
    const response = await fetch(`${baseUrl}/api/gemini/vision`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${bearer()}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        requestId: 'invalid-before-provider',
        imageBase64: bytes.toString('base64'),
        mimeType: 'image/jpeg',
        preparedBytes: bytes.length
      })
    })

    assert.equal(response.status, 400)
    assert.notDeepEqual(await response.json(), { error: 'Payload Too Large' })
  })
})

test('limits unauthenticated traffic by client IP before route authentication', async () => {
  await withServer(securityConfig({ preAuthRateLimitMax: 2 }), async ({ baseUrl }) => {
    const first = await fetch(`${baseUrl}/api/health/summary`)
    const second = await fetch(`${baseUrl}/api/health/summary`)
    const limited = await fetch(`${baseUrl}/api/health/summary`)

    assert.equal(first.status, 401)
    assert.equal(second.status, 401)
    assert.equal(limited.status, 429)
    assert.deepEqual(await limited.json(), { error: 'Too Many Requests' })
    assert.equal(limited.headers.get('x-content-type-options'), 'nosniff')
    assert.equal(limited.headers.get('access-control-allow-origin'), null)
  })
})

test('uses one authenticated quota for case and trailing-slash variants of a route', async () => {
  await withServer(securityConfig({ userRateLimitMax: 1 }), async ({ baseUrl }) => {
    const headers = { Authorization: `Bearer ${bearer('canonical-path-user')}` }
    const first = await fetch(`${baseUrl}/api/health/summary`, { headers })
    const variant = await fetch(`${baseUrl}/API/HEALTH/SUMMARY/`, { headers })

    assert.equal(first.status, 200)
    assert.equal(variant.status, 429)
  })
})

test('marks authenticated health responses as non-cacheable', async () => {
  await withServer(securityConfig(), async ({ baseUrl }) => {
    const response = await fetch(`${baseUrl}/api/health/summary`, {
      headers: { Authorization: `Bearer ${bearer('private-health-user')}` }
    })

    assert.equal(response.status, 200)
    assert.match(response.headers.get('cache-control') || '', /no-store/i)
  })
})

test('limits authenticated traffic independently by user and endpoint', async () => {
  await withServer(securityConfig({ userRateLimitMax: 1 }), async ({ baseUrl }) => {
    const headers = { Authorization: `Bearer ${bearer('user-1')}` }
    const firstSummary = await fetch(`${baseUrl}/api/health/summary`, { headers })
    const limitedSummary = await fetch(`${baseUrl}/api/health/summary`, { headers })
    const firstReadiness = await fetch(`${baseUrl}/api/health/readiness`, { headers })
    const otherUserSummary = await fetch(`${baseUrl}/api/health/summary`, {
      headers: { Authorization: `Bearer ${bearer('user-2')}` }
    })

    assert.equal(firstSummary.status, 200)
    assert.equal(limitedSummary.status, 429)
    assert.equal(firstReadiness.status, 200)
    assert.equal(otherUserSummary.status, 200)
    assert.deepEqual(await limitedSummary.json(), { error: 'Too Many Requests' })
  })
})

test('uses a stricter authenticated limiter for Gemini endpoints with Retry-After', async () => {
  await withServer(securityConfig({ userRateLimitMax: 5, geminiUserRateLimitMax: 1 }), async ({ baseUrl }) => {
    const headers = {
      Authorization: `Bearer ${bearer('cost-limited-user')}`,
      'Content-Type': 'application/json'
    }
    const firstGemini = await fetch(`${baseUrl}/api/gemini/vision`, {
      method: 'POST', headers, body: '{}'
    })
    const limitedGemini = await fetch(`${baseUrl}/api/gemini/vision`, {
      method: 'POST', headers, body: '{}'
    })
    const firstSummary = await fetch(`${baseUrl}/api/health/summary`, { headers })
    const secondSummary = await fetch(`${baseUrl}/api/health/summary`, { headers })

    assert.equal(firstGemini.status, 400)
    assert.equal(limitedGemini.status, 429)
    assert.match(limitedGemini.headers.get('retry-after'), /^\d+$/)
    assert.deepEqual(await limitedGemini.json(), { error: 'Too Many Requests' })
    assert.equal(firstSummary.status, 200)
    assert.equal(secondSummary.status, 200)
  })
})
