'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { once } = require('node:events')
const jwt = require('jsonwebtoken')

const { createApp } = require('../server/app')

const securityConfig = Object.freeze({
  nodeEnv: 'test',
  jwtSecret: 'a-strong-signing-key-with-at-least-32-characters',
  jwtIssuer: 'https://identity.fitplan.mx',
  jwtAudience: 'fitplan-backend',
  jwtAlgorithms: Object.freeze(['HS256']),
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

function bearer(version = 3) {
  return jwt.sign(
    { sub: 'google:delete-subject', roles: ['member'], ver: version },
    securityConfig.jwtSecret,
    {
      algorithm: 'HS256',
      issuer: securityConfig.jwtIssuer,
      audience: securityConfig.jwtAudience,
      expiresIn: '5m'
    }
  )
}

async function withServer(database, securityLogger, callback) {
  const app = createApp({ securityConfig, securityLogger, database })
  const server = app.listen(0, '127.0.0.1')
  await once(server, 'listening')
  try {
    await callback(`http://127.0.0.1:${server.address().port}`)
  } finally {
    server.close()
    await once(server, 'close')
  }
}

test('deletes all subject-scoped persistent data and leaves a versioned tombstone in one transaction', async () => {
  const transactionQueries = []
  let transactionCalls = 0
  let principal = { token_version: 3, deleted_at: null }
  const database = {
    async query(sql) {
      if (/FROM auth_principals/i.test(sql)) return [[principal]]
      return [{ affectedRows: 1 }]
    },
    async transaction(work) {
      transactionCalls += 1
      return work({
        query: async (sql, params) => {
          transactionQueries.push({ sql, params })
          if (/FOR UPDATE/i.test(sql)) return [[principal]]
          if (/UPDATE auth_principals/i.test(sql)) {
            principal = { token_version: 4, deleted_at: new Date().toISOString() }
            return [{ affectedRows: 1 }]
          }
          return [{ affectedRows: 1 }]
        }
      })
    }
  }
  const auditEvents = []
  const logger = {
    info() {}, warn() {}, error() {},
    audit(event, fields) { auditEvents.push({ event, fields }) }
  }

  await withServer(database, logger, async baseUrl => {
    const response = await fetch(`${baseUrl}/api/account`, {
      method: 'DELETE',
      headers: {
        Authorization: `Bearer ${bearer()}`,
        'X-Correlation-ID': 'request-delete-1234'
      }
    })

    assert.equal(response.status, 204)
    assert.equal(await response.text(), '')
    assert.equal(transactionCalls, 1)
    for (const table of ['health_records', 'consent_state', 'consent_audit', 'session_muscle_volume']) {
      assert.equal(transactionQueries.some(query => new RegExp(`DELETE FROM ${table}`, 'i').test(query.sql)), true)
    }
    const tombstone = transactionQueries.find(query => /UPDATE auth_principals/i.test(query.sql))
    assert.ok(tombstone)
    assert.match(tombstone.sql, /deleted_at\s*=\s*CURRENT_TIMESTAMP\(3\)/i)
    assert.match(tombstone.sql, /token_version\s*=\s*token_version\s*\+\s*1/i)
    assert.deepEqual(tombstone.params, ['google:delete-subject', 3])
    assert.equal(auditEvents.some(item => item.event === 'account.deleted' && item.fields.requestId === 'request-delete-1234'), true)

    const revoked = await fetch(`${baseUrl}/api/health/summary`, {
      headers: { Authorization: `Bearer ${bearer()}` }
    })
    assert.equal(revoked.status, 401)
  })
})
