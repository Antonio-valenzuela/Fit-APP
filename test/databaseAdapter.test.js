'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')

const { createMySqlDatabase } = require('../db')

function fakeMysql({ failPing = false } = {}) {
  const events = []
  const connection = {
    async beginTransaction() { events.push('begin') },
    async execute(sql, params) { events.push({ sql, params }); return [{ affectedRows: 1 }] },
    async commit() { events.push('commit') },
    async rollback() { events.push('rollback') },
    release() { events.push('release') }
  }
  const pool = {
    async execute(sql, params) {
      events.push({ sql, params })
      if (failPing && /SELECT 1/.test(sql)) throw new Error('connection refused')
      return [[{ ok: 1 }]]
    },
    async getConnection() { events.push('getConnection'); return connection },
    async end() { events.push('end') }
  }
  return {
    events,
    module: { createPool(options) { events.push({ createPool: options }); return pool } }
  }
}

test('creates and verifies a real mysql pool before returning the database', async () => {
  assert.equal(typeof createMySqlDatabase, 'function')
  const mysql = fakeMysql()
  const database = await createMySqlDatabase({
    databaseUrl: 'mysql://fitplan:secret@db.internal:3306/fitplan',
    databaseTls: true,
    mysqlModule: mysql.module,
    logger: { info() {} }
  })

  assert.equal(mysql.events[0].createPool.host, 'db.internal')
  assert.equal(mysql.events[0].createPool.database, 'fitplan')
  assert.deepEqual(mysql.events[0].createPool.ssl, { rejectUnauthorized: true })
  assert.match(mysql.events[1].sql, /SELECT 1/)
  await database.close()
  assert.equal(mysql.events.at(-1), 'end')
})

test('fails closed when the mysql connectivity check fails', async () => {
  assert.equal(typeof createMySqlDatabase, 'function')
  const mysql = fakeMysql({ failPing: true })

  await assert.rejects(
    createMySqlDatabase({
      databaseUrl: 'mysql://fitplan:secret@db.internal:3306/fitplan',
      databaseTls: true,
      mysqlModule: mysql.module,
      logger: { info() {} }
    }),
    /Database initialization failed/
  )
  assert.equal(mysql.events.at(-1), 'end')
})

test('commits successful work and rolls back failed work in transactions', async () => {
  assert.equal(typeof createMySqlDatabase, 'function')
  const mysql = fakeMysql()
  const database = await createMySqlDatabase({
    databaseUrl: 'mysql://fitplan:secret@db.internal:3306/fitplan',
    databaseTls: true,
    mysqlModule: mysql.module,
    logger: { info() {} }
  })

  await database.transaction(tx => tx.query('INSERT INTO example(value) VALUES (?)', ['safe']))
  assert.deepEqual(mysql.events.slice(-5).map(event => typeof event === 'string' ? event : event.sql), [
    'getConnection', 'begin', 'INSERT INTO example(value) VALUES (?)', 'commit', 'release'
  ])

  await assert.rejects(database.transaction(async () => { throw new Error('stop') }), /stop/)
  assert.deepEqual(mysql.events.slice(-4), ['getConnection', 'begin', 'rollback', 'release'])
})
