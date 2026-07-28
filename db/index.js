'use strict'

let activeLogger = null
let queryImplementation = null
let closeImplementation = null

function configure({ logger, execute, close } = {}) {
  activeLogger = logger || null
  if (execute !== undefined) queryImplementation = execute
  if (close !== undefined) closeImplementation = close
}

function operationFrom(sql) {
  const match = typeof sql === 'string' && sql.match(/^\s*(SELECT|INSERT|UPDATE|DELETE|CALL)\b/i)
  return match ? match[1].toUpperCase() : 'OTHER'
}

async function query(sql, params) {
  activeLogger?.info?.({ event: 'db.query', operation: operationFrom(sql) })
  if (queryImplementation) return queryImplementation(sql, params)
  throw new Error('Database is not configured')
}

async function close() {
  if (closeImplementation) await closeImplementation()
}

async function createMySqlDatabase({ databaseUrl, databaseTls, logger, mysqlModule } = {}) {
  if (typeof databaseUrl !== 'string' || !databaseUrl.trim() || databaseTls !== true) {
    throw new Error('Database initialization failed')
  }
  const mysql = mysqlModule || require('mysql2/promise')
  let parsed
  try {
    parsed = new URL(databaseUrl)
  } catch {
    throw new Error('Database initialization failed')
  }
  if (parsed.protocol !== 'mysql:' || !parsed.hostname || parsed.pathname === '/') {
    throw new Error('Database initialization failed')
  }
  const pool = mysql.createPool({
    host: parsed.hostname,
    port: parsed.port ? Number(parsed.port) : 3306,
    user: decodeURIComponent(parsed.username),
    password: decodeURIComponent(parsed.password),
    database: decodeURIComponent(parsed.pathname.slice(1)),
    ssl: { rejectUnauthorized: true }
  })
  const logOperation = sql => logger?.info?.({ event: 'db.query', operation: operationFrom(sql) })

  try {
    await pool.execute('SELECT 1 AS ok')
  } catch {
    await pool.end().catch?.(() => {})
    throw new Error('Database initialization failed')
  }

  async function execute(sql, params) {
    logOperation(sql)
    return pool.execute(sql, params)
  }

  async function transaction(work) {
    if (typeof work !== 'function') throw new TypeError('transaction work must be a function')
    const connection = await pool.getConnection()
    try {
      await connection.beginTransaction()
      const tx = Object.freeze({
        query: async (sql, params) => {
          logOperation(sql)
          return connection.execute(sql, params)
        }
      })
      const result = await work(tx)
      await connection.commit()
      return result
    } catch (error) {
      await connection.rollback()
      throw error
    } finally {
      connection.release()
    }
  }

  return Object.freeze({
    query: execute,
    transaction,
    close: () => pool.end()
  })
}

module.exports = {
  close,
  configure,
  createMySqlDatabase,
  query
}
