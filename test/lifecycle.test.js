'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { EventEmitter } = require('node:events')
const { spawnSync } = require('node:child_process')

const { createServerLifecycle } = require('../server/lifecycle')

class FakeServer {
  constructor() {
    this.closeCalls = 0
    this.closeIdleCalls = 0
  }

  close(callback) {
    this.closeCalls += 1
    setImmediate(callback)
  }

  closeIdleConnections() {
    this.closeIdleCalls += 1
  }
}

class HangingServer extends FakeServer {
  constructor() {
    super()
    this.closeAllCalls = 0
  }

  close() {
    this.closeCalls += 1
  }

  closeAllConnections() {
    this.closeAllCalls += 1
  }
}

function loggerRecorder() {
  const records = []
  return {
    records,
    logger: {
      info: value => records.push(value),
      warn: value => records.push(value),
      error: value => records.push(value),
      audit: (event, value) => records.push({ event, ...value })
    }
  }
}

test('index can be imported without starting a listener', () => {
  const script = "const api = require('./index'); process.stdout.write(typeof api.startServer); process.exit(0)"
  const result = spawnSync(process.execPath, ['-e', script], {
    cwd: process.cwd(),
    encoding: 'utf8',
    timeout: 15_000
  })

  assert.equal(result.status, 0)
  assert.equal(result.stdout, 'function')
  assert.equal(result.stderr, '')
})

test('startServer rejects invalid configuration before constructing an app', async () => {
  const { startServer } = require('../index')
  let appFactoryCalls = 0

  await assert.rejects(
    () => startServer({ env: {}, appFactory: () => { appFactoryCalls += 1 } }),
    error => error.name === 'ConfigurationError'
  )
  assert.equal(appFactoryCalls, 0)
})

test('startServer verifies the production database before constructing an app', async () => {
  const { startServer } = require('../index')
  let appFactoryCalls = 0
  const env = {
    NODE_ENV: 'production',
    PORT: '3000',
    JWT_SECRET: 'a-strong-signing-key-with-at-least-32-characters',
    JWT_ISSUER: 'https://identity.fitplan.mx',
    JWT_AUDIENCE: 'fitplan-backend',
    JWT_ALGORITHMS: 'HS256',
    GEMINI_API_KEY: 'provider-key-0123456789abcdef',
    CORS_ALLOWED_ORIGINS: 'https://app.fitplan.mx',
    TRUST_PROXY_HOPS: '1',
    DATABASE_URL: 'mysql://fitplan:secret@db.internal:3306/fitplan',
    DATABASE_TLS: 'verify_identity',
    RATE_LIMIT_STORE: 'mysql',
    GOOGLE_OAUTH_CLIENT_ID: '123456789.apps.googleusercontent.com'
  }

  await assert.rejects(
    startServer({
      env,
      databaseFactory: async () => { throw new Error('Database initialization failed') },
      appFactory: () => { appFactoryCalls += 1 }
    }),
    /Database initialization failed/
  )
  assert.equal(appFactoryCalls, 0)
})

test('SIGTERM stops accepting traffic, closes resources and exits zero', async () => {
  const server = new FakeServer()
  const processRef = new EventEmitter()
  const resource = { closeCalls: 0, async close() { this.closeCalls += 1 } }
  const { logger } = loggerRecorder()
  let resolveExit
  const exited = new Promise(resolve => { resolveExit = resolve })
  const lifecycle = createServerLifecycle({
    server,
    resources: [resource],
    processRef,
    logger,
    exit: code => resolveExit(code)
  })

  processRef.emit('SIGTERM')
  const exitCode = await exited

  assert.equal(exitCode, 0)
  assert.equal(server.closeCalls, 1)
  assert.equal(server.closeIdleCalls, 1)
  assert.equal(resource.closeCalls, 1)
  lifecycle.dispose()
})

test('fatal process events close once and exit one without logging exception text', async () => {
  for (const event of ['unhandledRejection', 'uncaughtException']) {
    const server = new FakeServer()
    const processRef = new EventEmitter()
    const resource = { closeCalls: 0, async close() { this.closeCalls += 1 } }
    const { logger, records } = loggerRecorder()
    let resolveExit
    const exited = new Promise(resolve => { resolveExit = resolve })
    const lifecycle = createServerLifecycle({
      server,
      resources: [resource],
      processRef,
      logger,
      exit: code => resolveExit(code)
    })

    processRef.emit(event, new Error('Bearer secret-token patient@example.com'))
    processRef.emit('SIGINT')
    const exitCode = await exited

    assert.equal(exitCode, 1)
    assert.equal(server.closeCalls, 1)
    assert.equal(resource.closeCalls, 1)
    const output = JSON.stringify(records)
    assert.equal(output.includes('secret-token'), false)
    assert.equal(output.includes('patient@example.com'), false)
    assert.equal(records.some(record => record.event === 'server.fatal'), true)
    lifecycle.dispose()
  }
})

test('shutdown timeout force-closes active connections and exits one', async () => {
  const server = new HangingServer()
  const processRef = new EventEmitter()
  const resource = { close: () => new Promise(() => {}) }
  const { logger, records } = loggerRecorder()
  let resolveExit
  const exited = new Promise(resolve => { resolveExit = resolve })
  const lifecycle = createServerLifecycle({
    server,
    resources: [resource],
    processRef,
    logger,
    shutdownTimeoutMs: 10,
    exit: code => resolveExit(code)
  })

  processRef.emit('SIGTERM')
  const outcome = await Promise.race([
    exited,
    new Promise(resolve => setTimeout(() => resolve('hung'), 100))
  ])
  lifecycle.dispose()

  assert.equal(outcome, 1)
  assert.equal(server.closeAllCalls, 1)
  assert.equal(records.some(record => record.event === 'server.shutdown_timeout'), true)
})
