'use strict'

class ConfigurationError extends Error {
  constructor(setting, message) {
    super(`Invalid security configuration for ${setting}: ${message}`)
    this.name = 'ConfigurationError'
    this.setting = setting
  }
}

const PLACEHOLDER_VALUES = new Set([
  'change_me',
  'changeme',
  'placeholder',
  'secret'
])

const PLACEHOLDER_PATTERN = /(?:change[_-]?me|placeholder|fallback|^my(?:[_-].*)?(?:api[_-]?key|secret)$|^(?:demo|example|sample|test)[_-])/i

function requiredText(env, setting) {
  const value = env[setting]
  if (typeof value !== 'string' || !value.trim()) {
    throw new ConfigurationError(setting, 'a non-empty value is required')
  }
  return value.trim()
}

function rejectPlaceholder(setting, value) {
  const normalized = value.trim().toLowerCase()
  if (PLACEHOLDER_VALUES.has(normalized) || PLACEHOLDER_PATTERN.test(normalized)) {
    throw new ConfigurationError(setting, 'placeholder values are forbidden')
  }
  return value
}

function parseInteger(env, setting, { min, max }) {
  const raw = requiredText(env, setting)
  if (!/^\d+$/.test(raw)) throw new ConfigurationError(setting, 'an integer is required')
  const value = Number(raw)
  if (!Number.isSafeInteger(value) || value < min || value > max) {
    throw new ConfigurationError(setting, `must be between ${min} and ${max}`)
  }
  return value
}

function optionalInteger(env, setting, defaultValue, limits) {
  if (env[setting] === undefined || env[setting] === '') return defaultValue
  return parseInteger(env, setting, limits)
}

function parseAlgorithms(env) {
  const allowed = new Set(['HS256', 'HS384', 'HS512'])
  const values = requiredText(env, 'JWT_ALGORITHMS')
    .split(',')
    .map(value => value.trim())
    .filter(Boolean)
  if (values.length === 0 || values.some(value => !allowed.has(value)) || !values.includes('HS256')) {
    throw new ConfigurationError('JWT_ALGORITHMS', 'HS256 must be included and only explicit HMAC algorithms are supported')
  }
  return [...new Set(values)]
}

function parseOrigins(env, nodeEnv) {
  const values = requiredText(env, 'CORS_ALLOWED_ORIGINS')
    .split(',')
    .map(value => value.trim())
    .filter(Boolean)
  if (values.length === 0 || values.includes('*')) {
    throw new ConfigurationError('CORS_ALLOWED_ORIGINS', 'wildcard or empty allowlists are forbidden')
  }
  for (const value of values) {
    let url
    try {
      url = new URL(value)
    } catch {
      throw new ConfigurationError('CORS_ALLOWED_ORIGINS', 'every origin must be an absolute URL')
    }
    if (!['http:', 'https:'].includes(url.protocol) || url.origin !== value || url.username || url.password) {
      throw new ConfigurationError('CORS_ALLOWED_ORIGINS', 'every entry must be an HTTP(S) origin without credentials or paths')
    }
    if (nodeEnv === 'production' && url.protocol !== 'https:') {
      throw new ConfigurationError('CORS_ALLOWED_ORIGINS', 'production browser origins must use HTTPS')
    }
  }
  return [...new Set(values)]
}

function loadSecurityConfig(env = process.env) {
  const nodeEnv = requiredText(env, 'NODE_ENV')
  if (!['development', 'test', 'production'].includes(nodeEnv)) {
    throw new ConfigurationError('NODE_ENV', 'must be development, test or production')
  }
  const jwtSecret = rejectPlaceholder('JWT_SECRET', requiredText(env, 'JWT_SECRET'))
  if (Buffer.byteLength(jwtSecret, 'utf8') < 32) {
    throw new ConfigurationError('JWT_SECRET', 'must contain at least 32 bytes')
  }
  const geminiApiKey = rejectPlaceholder('GEMINI_API_KEY', requiredText(env, 'GEMINI_API_KEY'))
  const userRateLimitMax = optionalInteger(env, 'USER_RATE_LIMIT_MAX', 30, { min: 1, max: 10_000 })
  const geminiUserRateLimitMax = optionalInteger(env, 'GEMINI_USER_RATE_LIMIT_MAX', 10, { min: 1, max: 10_000 })
  if (geminiUserRateLimitMax >= userRateLimitMax) {
    throw new ConfigurationError('GEMINI_USER_RATE_LIMIT_MAX', 'must be lower than USER_RATE_LIMIT_MAX')
  }
  const rateLimitWindowMs = optionalInteger(env, 'RATE_LIMIT_WINDOW_MS', 60_000, { min: 1_000, max: 3_600_000 })
  const databaseUrl = nodeEnv === 'production'
    ? requiredText(env, 'DATABASE_URL')
    : env.DATABASE_URL?.trim() || null
  if (databaseUrl) {
    let parsedDatabaseUrl
    try {
      parsedDatabaseUrl = new URL(databaseUrl)
    } catch {
      throw new ConfigurationError('DATABASE_URL', 'must be an absolute MySQL URL')
    }
    if (parsedDatabaseUrl.protocol !== 'mysql:' || !parsedDatabaseUrl.hostname || parsedDatabaseUrl.pathname === '/') {
      throw new ConfigurationError('DATABASE_URL', 'must be a mysql:// URL with host and database name')
    }
  }
  const databaseTlsMode = databaseUrl ? requiredText(env, 'DATABASE_TLS') : null
  if (databaseTlsMode !== null && databaseTlsMode !== 'verify_identity') {
    throw new ConfigurationError('DATABASE_TLS', 'must be verify_identity when DATABASE_URL is configured')
  }
  const rateLimitStore = env.RATE_LIMIT_STORE?.trim() || (nodeEnv === 'production' ? '' : 'memory')
  if (!['memory', 'mysql'].includes(rateLimitStore) || (nodeEnv === 'production' && rateLimitStore !== 'mysql')) {
    throw new ConfigurationError('RATE_LIMIT_STORE', 'production requires the shared mysql store')
  }
  const googleOAuthClientId = nodeEnv === 'production'
    ? requiredText(env, 'GOOGLE_OAUTH_CLIENT_ID')
    : env.GOOGLE_OAUTH_CLIENT_ID?.trim() || null

  return Object.freeze({
    nodeEnv,
    port: parseInteger(env, 'PORT', { min: 1, max: 65535 }),
    jwtSecret,
    jwtIssuer: requiredText(env, 'JWT_ISSUER'),
    jwtAudience: requiredText(env, 'JWT_AUDIENCE'),
    jwtAlgorithms: Object.freeze(parseAlgorithms(env)),
    geminiApiKey,
    geminiModel: env.GEMINI_MODEL?.trim() || 'gemini-3.5-flash',
    geminiAttemptTimeoutMs: 20_000,
    corsAllowedOrigins: Object.freeze(parseOrigins(env, nodeEnv)),
    trustProxyHops: parseInteger(env, 'TRUST_PROXY_HOPS', { min: 0, max: 10 }),
    preAuthRateLimitWindowMs: rateLimitWindowMs,
    preAuthRateLimitMax: optionalInteger(env, 'PRE_AUTH_RATE_LIMIT_MAX', 120, { min: 1, max: 100_000 }),
    userRateLimitWindowMs: rateLimitWindowMs,
    userRateLimitMax,
    geminiUserRateLimitWindowMs: rateLimitWindowMs,
    geminiUserRateLimitMax,
    shutdownTimeoutMs: optionalInteger(env, 'SHUTDOWN_TIMEOUT_MS', 10_000, { min: 100, max: 60_000 }),
    databaseUrl,
    databaseTls: databaseTlsMode === 'verify_identity',
    rateLimitStore,
    googleOAuthClientId,
    jwtTtlSeconds: optionalInteger(env, 'JWT_TTL_SECONDS', 900, { min: 300, max: 3_600 })
  })
}

module.exports = {
  ConfigurationError,
  loadSecurityConfig
}
