'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')

const { GeminiProvider, ProviderRequestError, parseRetryAfterMs } = require('../services/geminiProvider')

const silentLogger = Object.freeze({ info() {}, warn() {} })

function response(status, body, headers = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: name => headers[name.toLowerCase()] || null },
    text: async () => JSON.stringify(body)
  }
}

function providerWith(fetchFn, overrides = {}) {
  let now = 1_000
  return new GeminiProvider({
    apiKey: 'secret-provider-key',
    fetchFn,
    now: () => now,
    sleep: async ms => { now += ms },
    random: () => 0,
    logger: silentLogger,
    attemptTimeoutMs: 20_000,
    ...overrides
  })
}

const request = Object.freeze({
  userId: 'user-1',
  requestId: 'request-1',
  providerBody: { contents: [{ parts: [{ text: 'safe prompt' }, { inline_data: { data: 'BASE64-SECRET' } }] }] },
  requestBytes: 2048,
  imageBytes: 1024
})

test('retries a 503 once and respects Retry-After', async () => {
  const calls = []
  const sleeps = []
  let now = 1_000
  const provider = new GeminiProvider({
    apiKey: 'secret-provider-key',
    fetchFn: async () => {
      calls.push(now)
      return calls.length === 1
        ? response(503, { error: { message: 'high demand' } }, { 'retry-after': '2' })
        : response(200, { candidates: [], modelVersion: 'gemini-3.5-flash' })
    },
    now: () => now,
    sleep: async ms => { sleeps.push(ms); now += ms },
    random: () => 0,
    logger: silentLogger,
    attemptTimeoutMs: 20_000
  })

  const result = await provider.execute(request)

  assert.equal(calls.length, 2)
  assert.deepEqual(sleeps, [2000])
  assert.equal(result.metrics.attempt, 2)
  assert.equal(result.data.modelVersion, 'gemini-3.5-flash')
})

test('does not retry 429 or validation errors', async () => {
  for (const status of [400, 413, 422, 429]) {
    let calls = 0
    const provider = providerWith(async () => {
      calls += 1
      return response(status, { error: { message: 'provider rejected request' } })
    })

    await assert.rejects(
      provider.execute({ ...request, requestId: `request-${status}` }),
      error => error instanceof ProviderRequestError && error.status === status && error.attempt === 1
    )
    assert.equal(calls, 1)
  }
})

test('opens a temporary circuit after consecutive final provider failures', async () => {
  let calls = 0
  const provider = providerWith(async () => {
    calls += 1
    return response(503, { error: { message: 'unavailable' } })
  }, { maxRetries: 0, failureThreshold: 3, circuitOpenMs: 30_000 })

  for (let index = 0; index < 3; index += 1) {
    await assert.rejects(provider.execute({ ...request, requestId: `failure-${index}` }), ProviderRequestError)
  }
  await assert.rejects(
    provider.execute({ ...request, requestId: 'blocked' }),
    error => error instanceof ProviderRequestError && error.code === 'CIRCUIT_OPEN'
  )
  assert.equal(calls, 3)
})

test('deduplicates the same idempotency key and rejects a second request for the same user', async () => {
  let release
  let calls = 0
  const gate = new Promise(resolve => { release = resolve })
  const provider = providerWith(async () => {
    calls += 1
    await gate
    return response(200, { candidates: [], modelVersion: 'gemini-3.5-flash' })
  })

  const first = provider.execute(request)
  const duplicate = provider.execute(request)
  await assert.rejects(
    provider.execute({ ...request, requestId: 'different-request' }),
    error => error instanceof ProviderRequestError && error.code === 'CONCURRENT_REQUEST'
  )
  release()
  const [firstResult, duplicateResult] = await Promise.all([first, duplicate])

  assert.equal(calls, 1)
  assert.deepEqual(firstResult.data, duplicateResult.data)
})

test('reuses a completed result only for the same idempotency key', async () => {
  let calls = 0
  const provider = providerWith(async () => {
    calls += 1
    return response(200, { candidates: [], modelVersion: 'gemini-3.5-flash' })
  })

  const first = await provider.execute(request)
  const cached = await provider.execute(request)

  assert.equal(calls, 1)
  assert.equal(first.metrics.cacheHit, false)
  assert.equal(cached.metrics.cacheHit, true)
})

test('classifies an aborted provider call as a timeout', async () => {
  const provider = providerWith(async () => {
    const error = new Error('aborted')
    error.name = 'AbortError'
    throw error
  }, { maxRetries: 0 })

  await assert.rejects(
    provider.execute(request),
    error => error instanceof ProviderRequestError && error.code === 'PROVIDER_TIMEOUT' && error.status === 504
  )
})

test('logs metrics without API key, Base64, prompt, or provider body', async () => {
  const logs = []
  const provider = providerWith(
    async () => response(503, { error: { message: 'high demand' } }),
    { maxRetries: 0, logger: { info: value => logs.push(value), warn: value => logs.push(value) } }
  )

  await assert.rejects(provider.execute(request), ProviderRequestError)
  const serialized = JSON.stringify(logs)
  assert.equal(serialized.includes('secret-provider-key'), false)
  assert.equal(serialized.includes('BASE64-SECRET'), false)
  assert.equal(serialized.includes('safe prompt'), false)
  assert.equal(serialized.includes('high demand'), false)
  assert.equal(serialized.includes('requestBytes'), true)
})

test('parses Retry-After seconds and HTTP dates', () => {
  assert.equal(parseRetryAfterMs('3', 1_000), 3_000)
  assert.equal(parseRetryAfterMs('Thu, 01 Jan 1970 00:00:05 GMT', 1_000), 4_000)
  assert.equal(parseRetryAfterMs('invalid', 1_000), null)
})
