'use strict'

const ALLOWED_FIELDS = new Set([
  'event',
  'requestId',
  'method',
  'path',
  'status',
  'durationMs',
  'reason',
  'requiredRole',
  'scope',
  'errorType',
  'operation',
  'entity',
  'code',
  'model',
  'requestBytes',
  'imageBytes',
  'attempt',
  'totalMs',
  'retryable'
])

const SENSITIVE_FIELD = /(?:authorization|cookie|password|secret|token|email|medical|health|gps|latitude|longitude|payload|body|params?)/i
const SENSITIVE_VALUE = /(?:Bearer\s+\S+|\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b|\beyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\b)/i

function sanitizeString(value) {
  const neutralized = value.replace(/[\u0000-\u001f\u007f-\u009f]/g, ' ').trim().slice(0, 512)
  return SENSITIVE_VALUE.test(neutralized) ? '[REDACTED]' : neutralized
}

function safeValue(field, value) {
  if (SENSITIVE_FIELD.test(field)) return '[REDACTED]'
  if (typeof value === 'string') return sanitizeString(value)
  if (typeof value === 'number' && Number.isFinite(value)) return value
  if (typeof value === 'boolean') return value
  return undefined
}

function createSecurityLogger({
  sink = line => process.stdout.write(`${line}\n`),
  now = Date.now
} = {}) {
  function write(level, input = {}) {
    const source = input && typeof input === 'object' && !Array.isArray(input) ? input : {}
    const record = {
      timestamp: new Date(now()).toISOString(),
      level
    }
    for (const [field, value] of Object.entries(source)) {
      if (!ALLOWED_FIELDS.has(field)) continue
      const sanitized = safeValue(field, value)
      if (sanitized !== undefined && sanitized !== '') record[field] = sanitized
    }
    if (!record.event) record.event = 'application.event'
    sink(JSON.stringify(record))
  }

  return Object.freeze({
    info: value => write('info', value),
    warn: value => write('warn', value),
    error: value => write('error', value),
    audit(event, fields = {}) {
      write('audit', { ...fields, event })
    }
  })
}

module.exports = {
  createSecurityLogger
}
