'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const { ConfigurationError, loadSecurityConfig } = require('../config/security')

function validEnv(overrides = {}) {
  return {
    NODE_ENV: 'production',
    PORT: '3000',
    JWT_SECRET: 'a-strong-signing-key-with-at-least-32-characters',
    JWT_ISSUER: 'https://identity.fitplan.mx',
    JWT_AUDIENCE: 'fitplan-backend',
    JWT_ALGORITHMS: 'HS256',
    GEMINI_API_KEY: 'provider-key-0123456789abcdef',
    CORS_ALLOWED_ORIGINS: 'https://app.fitplan.mx,https://admin.fitplan.mx',
    TRUST_PROXY_HOPS: '1',
    DATABASE_URL: 'mysql://fitplan:password@db.internal:3306/fitplan',
    DATABASE_TLS: 'verify_identity',
    RATE_LIMIT_STORE: 'mysql',
    GOOGLE_OAUTH_CLIENT_ID: '123456789.apps.googleusercontent.com',
    ...overrides
  }
}

test('loads an explicit production security configuration', () => {
  const config = loadSecurityConfig(validEnv())

  assert.deepEqual(config.jwtAlgorithms, ['HS256'])
  assert.deepEqual(config.corsAllowedOrigins, ['https://app.fitplan.mx', 'https://admin.fitplan.mx'])
  assert.equal(config.trustProxyHops, 1)
  assert.equal(config.geminiUserRateLimitMax < config.userRateLimitMax, true)
  assert.equal(config.shutdownTimeoutMs, 10_000)
  assert.equal(Object.hasOwn(config, 'backendApiKey'), false)
})

test('rejects every missing required security setting', () => {
  for (const name of [
    'PORT',
    'JWT_SECRET',
    'JWT_ISSUER',
    'JWT_AUDIENCE',
    'JWT_ALGORITHMS',
    'GEMINI_API_KEY',
    'CORS_ALLOWED_ORIGINS',
    'TRUST_PROXY_HOPS',
    'DATABASE_URL',
    'DATABASE_TLS',
    'RATE_LIMIT_STORE',
    'GOOGLE_OAUTH_CLIENT_ID'
  ]) {
    const env = validEnv()
    delete env[name]
    assert.throws(
      () => loadSecurityConfig(env),
      error => error instanceof ConfigurationError && error.setting === name
    )
  }
})

test('rejects cleartext browser origins in production', () => {
  assert.throws(
    () => loadSecurityConfig(validEnv({ CORS_ALLOWED_ORIGINS: 'http://app.fitplan.mx' })),
    error => error instanceof ConfigurationError && error.setting === 'CORS_ALLOWED_ORIGINS'
  )
})

test('requires the production shared rate-limit store to be mysql', () => {
  for (const value of ['memory', 'redis', '']) {
    assert.throws(
      () => loadSecurityConfig(validEnv({ RATE_LIMIT_STORE: value })),
      error => error instanceof ConfigurationError && error.setting === 'RATE_LIMIT_STORE'
    )
  }
})

test('rejects known placeholders, wildcard origins and unsafe proxy values', () => {
  for (const [name, value] of [
    ['JWT_SECRET', 'a-fallback-signing-key-with-32-characters'],
    ['GEMINI_API_KEY', 'my-provider-api-key'],
    ['CORS_ALLOWED_ORIGINS', '*'],
    ['TRUST_PROXY_HOPS', 'true'],
    ['TRUST_PROXY_HOPS', '-1'],
    ['JWT_ALGORITHMS', 'none'],
    ['GEMINI_USER_RATE_LIMIT_MAX', '31']
  ]) {
    assert.throws(
      () => loadSecurityConfig(validEnv({ [name]: value })),
      error => error instanceof ConfigurationError && error.setting === name
    )
  }
})

test('Node source contains no hard-coded environment credential fallbacks', () => {
  const roots = ['config', 'db', 'middleware', 'routes', 'server', 'services', 'test']
  const files = ['index.js']
  for (const root of roots) {
    const queue = [root]
    while (queue.length > 0) {
      const current = queue.pop()
      for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
        const target = path.join(current, entry.name)
        if (entry.isDirectory()) queue.push(target)
        else if (entry.isFile() && target.endsWith('.js')) files.push(target)
      }
    }
  }

  for (const file of files) {
    const source = fs.readFileSync(file, 'utf8')
    assert.equal(
      /process\.env\.[A-Z0-9_]*(?:KEY|SECRET|TOKEN)[A-Z0-9_]*\s*(?:\|\||\?\?)\s*['"][^'"]+['"]/.test(source),
      false,
      `${file} contains a hard-coded environment credential fallback`
    )
  }
})

test('keeps backend-only settings out of the Android dotenv template', () => {
  const androidEnv = fs.readFileSync(path.join(__dirname, '..', '.env.example'), 'utf8')
  const backendEnv = fs.readFileSync(path.join(__dirname, '..', '.env.backend.example'), 'utf8')

  assert.doesNotMatch(androidEnv, /JWT_SECRET|GEMINI_API_KEY|DATABASE_URL|DATABASE_TLS/)
  for (const name of ['JWT_SECRET', 'GEMINI_API_KEY', 'DATABASE_URL', 'DATABASE_TLS']) {
    assert.match(backendEnv, new RegExp(`^${name}=`, 'm'))
  }
})

test('Android Secrets Gradle plugin ignores every backend-only variable', () => {
  const gradleSource = fs.readFileSync(path.join(__dirname, '..', 'app', 'build.gradle.kts'), 'utf8')
  const backendEnv = fs.readFileSync(path.join(__dirname, '..', '.env.backend.example'), 'utf8')
  const backendNames = backendEnv.match(/^[A-Z][A-Z0-9_]*(?==)/gm) || []

  for (const name of backendNames) {
    assert.match(gradleSource, new RegExp(`"${name}"`), `${name} must be excluded from Android BuildConfig`)
  }
})
