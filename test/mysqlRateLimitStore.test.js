'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')

const fs = require('node:fs')
const path = require('node:path')

test('hashes quota keys and updates counters inside a database transaction', async () => {
  const modulePath = path.join(__dirname, '..', 'services', 'mysqlRateLimitStore.js')
  assert.equal(fs.existsSync(modulePath), true)
  const { MySqlRateLimitStore } = require(modulePath)
  const calls = []
  const database = {
    async transaction(work) {
      return work({
        async query(sql, params) {
          calls.push({ sql, params })
          return /SELECT/i.test(sql) ? [[{ total_hits: 2, reset_at_ms: 1784635200000 }]] : [{ affectedRows: 1 }]
        }
      })
    }
  }
  const store = new MySqlRateLimitStore({ database, namespace: 'gemini' })
  store.init({ windowMs: 60_000 })

  const result = await store.increment('opaque-user:/api/gemini/chat')
  await store.increment('another-user:/api/gemini/chat')

  assert.equal(result.totalHits, 2)
  assert.equal(result.resetTime.toISOString(), '2026-07-21T12:00:00.000Z')
  assert.match(calls[0].sql, /DELETE FROM rate_limit_counters[\s\S]*namespace = \?[\s\S]*reset_at <= CURRENT_TIMESTAMP/i)
  assert.deepEqual(calls[0].params, ['gemini'])
  assert.equal(calls[1].params[0], 'gemini')
  assert.match(calls[1].params[1], /^[a-f0-9]{64}$/)
  assert.equal(JSON.stringify(calls).includes('opaque-user'), false)
  assert.equal(calls.filter(call => /DELETE FROM rate_limit_counters/i.test(call.sql)).length, 1)
})
