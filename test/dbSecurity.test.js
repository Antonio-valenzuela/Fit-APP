'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')

const db = require('../db')

test('database diagnostics never include SQL text, parameters or health data', async () => {
  const events = []
  db.configure({ logger: { info: value => events.push(value) }, execute: async () => [[]] })

  const sql = 'INSERT INTO health_records (user_id, metric_type, value) VALUES (?, ?, ?)'
  const params = ['patient@example.com', 'heart_rate', 187]
  await db.query(sql, params)

  const output = JSON.stringify(events)
  assert.equal(output.includes(sql), false)
  for (const value of params) assert.equal(output.includes(String(value)), false)
  assert.deepEqual(events, [{ event: 'db.query', operation: 'INSERT' }])
})
