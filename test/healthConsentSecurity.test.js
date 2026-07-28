'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { once } = require('node:events')
const jwt = require('jsonwebtoken')

const { createApp } = require('../server/app')

const logger = Object.freeze({ info() {}, warn() {}, error() {}, audit() {} })
const config = Object.freeze({
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

function token() {
  return jwt.sign({ sub: 'opaque-health-user', roles: ['member'] }, config.jwtSecret, {
    algorithm: 'HS256', issuer: config.jwtIssuer, audience: config.jwtAudience, expiresIn: '5m'
  })
}

async function request(database, body) {
  const app = createApp({ securityConfig: config, securityLogger: logger, database })
  const server = app.listen(0, '127.0.0.1')
  await once(server, 'listening')
  try {
    return await fetch(`http://127.0.0.1:${server.address().port}/api/health/sync`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token()}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    })
  } finally {
    server.close()
    await once(server, 'close')
  }
}

test('rejects revoked health sync inside the locked transaction without writes', async () => {
  let transactionCalls = 0
  const database = {
    async query(sql) {
      if (/auth_principals/i.test(sql)) return [{ affectedRows: 1 }]
      throw new Error('consent must be read in the transaction')
    },
    async transaction(work) {
      transactionCalls += 1
      return work({
        query: async sql => {
          assert.match(sql, /FOR UPDATE/i)
          return [[{ status: 'revoked', denied_types: '[]' }]]
        }
      })
    }
  }

  const response = await request(database, { platform: 'android_hc', data: { steps: 4000 } })

  assert.equal(response.status, 403)
  assert.equal(transactionCalls, 1)
})

test('partial consent stores only metrics that were not denied and never promotes consent', async () => {
  const writes = []
  const database = {
    async query(sql) {
      if (/auth_principals/i.test(sql)) return [{ affectedRows: 1 }]
      throw new Error('consent must be read in the transaction')
    },
    async transaction(work) {
      return work({
        query: async (sql, params) => {
          if (/SELECT status, denied_types/i.test(sql)) {
            assert.match(sql, /FOR UPDATE/i)
            return [[{ status: 'partial', denied_types: JSON.stringify(['heart_rate']) }]]
          }
          writes.push({ sql, params })
          return [{ affectedRows: 1 }]
        }
      })
    }
  }

  const response = await request(database, {
    platform: 'android_hc',
    data: { steps: 4000, heart_rate: 190 }
  })

  assert.equal(response.status, 200)
  assert.equal((await response.json()).synced, 1)
  assert.equal(writes.some(write => write.params?.includes('steps')), true)
  assert.equal(writes.some(write => write.params?.includes('heart_rate')), false)
  assert.equal(writes.some(write => /consent_state/i.test(write.sql)), false)
  assert.equal(writes.some(write => /consent_audit/i.test(write.sql)), true)
})

test('rejects malformed or contradictory denied_types before persistence', async () => {
  const bodies = [
    { platform: 'android_hc', status: 'partial', denied_types: 'heart_rate' },
    { platform: 'android_hc', status: 'partial', denied_types: ['unknown_metric'] },
    { platform: 'android_hc', status: 'partial', denied_types: ['steps', 'heart_rate', 'sleep', 'weight', 'calories_burned', 'spo2', 'hrv'] },
    { platform: 'android_hc', status: 'granted', denied_types: ['heart_rate'] }
  ]
  for (const body of bodies) {
    let calls = 0
    const database = {
      async query() { calls += 1 },
      async transaction() { calls += 1 }
    }
    const app = createApp({ securityConfig: config, securityLogger: logger, database })
    const server = app.listen(0, '127.0.0.1')
    await once(server, 'listening')
    try {
      const response = await fetch(`http://127.0.0.1:${server.address().port}/api/health/consent`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token()}`, 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
      })
      assert.equal(response.status, 400)
    } finally {
      server.close()
      await once(server, 'close')
    }
    assert.equal(calls, 1, 'only principal registration may run')
  }
})
