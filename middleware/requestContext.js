'use strict'

const { randomUUID } = require('node:crypto')

const CORRELATION_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$/

function correlationId(req, res, next) {
  const supplied = req.headers?.['x-correlation-id']
  req.id = typeof supplied === 'string' && CORRELATION_ID_PATTERN.test(supplied)
    ? supplied
    : randomUUID()
  res.set('X-Correlation-ID', req.id)
  next()
}

function createRequestLogger(logger) {
  return function requestLogger(req, res, next) {
    const startedAt = Date.now()
    res.once('finish', () => {
      logger.info?.({
        event: 'http.request',
        requestId: req.id,
        method: req.method,
        path: req.originalUrl?.split('?')[0],
        status: res.statusCode,
        durationMs: Date.now() - startedAt
      })
    })
    next()
  }
}

module.exports = {
  correlationId,
  createRequestLogger
}
