'use strict'

const db = require('./db')
const { createMySqlDatabase } = db
const { loadSecurityConfig } = require('./config/security')
const { createApp } = require('./server/app')
const { createServerLifecycle } = require('./server/lifecycle')
const { createSecurityLogger } = require('./services/securityLogger')

async function startServer({
  env = process.env,
  appFactory = createApp,
  databaseFactory = createMySqlDatabase,
  securityLogger = createSecurityLogger(),
  processRef = process,
  exit = code => processRef.exit(code),
  resources,
  host = '0.0.0.0'
} = {}) {
  const securityConfig = loadSecurityConfig(env)
  const database = await databaseFactory({
    databaseUrl: securityConfig.databaseUrl,
    databaseTls: securityConfig.databaseTls,
    logger: securityLogger
  })
  db.configure({
    logger: securityLogger,
    execute: (sql, params) => database.query(sql, params),
    close: null
  })
  const app = appFactory({ securityConfig, securityLogger, database })
  const server = app.listen(securityConfig.port, host)
  const lifecycle = createServerLifecycle({
    server,
    resources: resources || [database],
    processRef,
    logger: securityLogger,
    shutdownTimeoutMs: securityConfig.shutdownTimeoutMs,
    exit
  })

  server.once('listening', () => {
    securityLogger.info?.({ event: 'server.listening' })
  })
  server.once('error', error => {
    lifecycle.shutdown({
      reason: 'server_error',
      exitCode: 1,
      exitProcess: true,
      errorType: error?.name || 'Error'
    }).catch(() => exit(1))
  })

  return Object.freeze({
    app,
    server,
    securityConfig,
    shutdown: options => lifecycle.shutdown(options),
    dispose: lifecycle.dispose
  })
}

if (require.main === module) {
  const securityLogger = createSecurityLogger()
  startServer({ securityLogger }).catch(error => {
    securityLogger.error({
      event: 'server.startup_failed',
      errorType: error?.name || 'Error'
    })
    process.exitCode = 1
  })
}

module.exports = {
  startServer
}
