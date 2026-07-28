'use strict'

function closeServer(server) {
  return new Promise(resolve => {
    let settled = false
    const done = () => {
      if (settled) return
      settled = true
      resolve()
    }
    try {
      server.close(done)
      server.closeIdleConnections?.()
    } catch {
      done()
    }
  })
}

function createServerLifecycle({
  server,
  resources = [],
  processRef = process,
  logger,
  shutdownTimeoutMs = 10_000,
  exit = code => processRef.exit(code)
}) {
  if (!server || typeof server.close !== 'function') throw new TypeError('server is required')
  if (!logger) throw new TypeError('logger is required')

  let shutdownPromise = null

  const handlers = {
    SIGTERM: () => triggerShutdown('SIGTERM', 0),
    SIGINT: () => triggerShutdown('SIGINT', 0),
    unhandledRejection: reason => triggerFatal('unhandledRejection', reason),
    uncaughtException: error => triggerFatal('uncaughtException', error)
  }

  function dispose() {
    for (const [event, handler] of Object.entries(handlers)) {
      processRef.removeListener(event, handler)
    }
  }

  function shutdown({ reason = 'manual', exitCode = 0, exitProcess = false, errorType } = {}) {
    if (shutdownPromise) return shutdownPromise
    shutdownPromise = (async () => {
      if (exitCode === 0) {
        logger.audit?.('server.shutdown', { reason })
      } else {
        logger.error?.({ event: 'server.fatal', reason, errorType: errorType || 'Error' })
      }

      let timeoutId
      const closeOperations = [
        closeServer(server),
        ...resources.map(resource => Promise.resolve().then(() => resource?.close?.()))
      ]
      const gracefulClose = Promise.allSettled(closeOperations)
      const timedOut = new Promise(resolve => {
        timeoutId = setTimeout(() => resolve(true), shutdownTimeoutMs)
      })
      const result = await Promise.race([
        gracefulClose.then(results => ({ results })),
        timedOut
      ])
      clearTimeout(timeoutId)

      if (result === true) {
        server.closeAllConnections?.()
        logger.error?.({ event: 'server.shutdown_timeout', reason, errorType: 'ShutdownTimeoutError' })
        exitCode = 1
      } else if (result.results.some(item => item.status === 'rejected')) {
        logger.error?.({ event: 'server.resource_close.error', errorType: 'ResourceCloseError' })
        exitCode = 1
      }
      dispose()
      if (exitProcess) exit(exitCode)
    })()
    return shutdownPromise
  }

  function triggerShutdown(reason, exitCode) {
    shutdown({ reason, exitCode, exitProcess: true }).catch(() => exit(1))
  }

  function triggerFatal(reason, error) {
    shutdown({
      reason,
      exitCode: 1,
      exitProcess: true,
      errorType: error?.name || typeof error
    }).catch(() => exit(1))
  }

  for (const [event, handler] of Object.entries(handlers)) {
    processRef.once(event, handler)
  }

  return Object.freeze({ dispose, shutdown })
}

module.exports = {
  createServerLifecycle
}
