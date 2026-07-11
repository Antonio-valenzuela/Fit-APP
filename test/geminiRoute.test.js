'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')

const geminiRouter = require('../routes/gemini')
const { ProviderRequestError } = require('../services/geminiProvider')

const { createVisionHandler, createTextBenchmarkHandler, requireBenchmarkAccess, validateVisionPayload } = geminiRouter.testing

const nutrition = Object.freeze({
  foodName: 'Ensalada',
  calories: 240,
  protein: 8,
  carbs: 30,
  fat: 10,
  portionEstimate: '1 plato mediano',
  confidence: 0.75,
  notes: 'Estimación visual.',
  ingredients: ['lechuga', 'tomate']
})

function payload() {
  const bytes = Buffer.from('jpeg-test-bytes')
  return {
    requestId: '123e4567-e89b-42d3-a456-426614174000',
    imageBase64: bytes.toString('base64'),
    mimeType: 'image/jpeg',
    originalBytes: 1200,
    preparedBytes: bytes.length,
    originalWidth: 2400,
    originalHeight: 1800,
    preparedWidth: 1280,
    preparedHeight: 960,
    quality: 75
  }
}

function responseRecorder() {
  return {
    statusCode: 200,
    headers: {},
    body: null,
    status(code) { this.statusCode = code; return this },
    set(name, value) { this.headers[name] = value; return this },
    json(value) { this.body = value; return this }
  }
}

test('validates and decodes the app-specific vision payload', () => {
  const validated = validateVisionPayload(payload())

  assert.equal(validated.imageBytes.length, Buffer.from('jpeg-test-bytes').length)
  assert.equal(validated.requestId, payload().requestId)
  assert.equal(Object.hasOwn(validated, 'imageBase64'), false)
})

test('rejects malformed Base64 and inconsistent prepared size', () => {
  assert.throws(() => validateVisionPayload({ ...payload(), imageBase64: '***' }), error => error.status === 400)
  assert.throws(() => validateVisionPayload({ ...payload(), preparedBytes: 999 }), error => error.status === 400)
})

test('returns only normalized nutrition and sanitized metrics', async () => {
  const provider = {
    model: 'gemini-3.5-flash',
    execute: async request => ({
      data: {
        candidates: [{ content: { parts: [
          { thought: true, text: 'private reasoning', thoughtSignature: 'opaque' },
          { text: JSON.stringify(nutrition), thoughtSignature: 'opaque-final' }
        ] } }],
        modelVersion: 'gemini-3.5-flash',
        usageMetadata: { promptTokenCount: 99 }
      },
      metrics: { attempt: 1, providerMs: 1200, totalMs: 1200, requestBytes: request.requestBytes, imageBytes: request.imageBytes, cacheHit: false }
    })
  }
  const handler = createVisionHandler({ provider, now: (() => { let value = 1000; return () => value += 5 })() })
  const req = { body: payload(), user: { id: 'user-1' } }
  const res = responseRecorder()

  await handler(req, res)

  assert.equal(res.statusCode, 200)
  assert.deepEqual(res.body.nutrition, nutrition)
  assert.equal(res.body.modelVersion, 'gemini-3.5-flash')
  assert.equal(JSON.stringify(res.body).includes('thoughtSignature'), false)
  assert.equal(JSON.stringify(res.body).includes('private reasoning'), false)
  assert.equal(JSON.stringify(res.body).includes('usageMetadata'), false)
})

test('maps provider failures to friendly responses without exposing technical JSON', async () => {
  const provider = {
    model: 'gemini-3.5-flash',
    execute: async () => {
      throw new ProviderRequestError({
        code: 'PROVIDER_UNAVAILABLE',
        status: 503,
        retryable: true,
        attempt: 2,
        retryAfterMs: 2000,
        providerMessage: 'This model is currently experiencing high demand.'
      })
    }
  }
  const handler = createVisionHandler({ provider })
  const res = responseRecorder()

  await handler({ body: payload(), user: { id: 'user-1' } }, res)

  assert.equal(res.statusCode, 503)
  assert.equal(res.headers['Retry-After'], '2')
  assert.equal(res.body.code, 'PROVIDER_UNAVAILABLE')
  assert.equal(res.body.retryable, true)
  assert.equal(JSON.stringify(res.body).includes('high demand'), false)
  assert.equal(JSON.stringify(res.body).includes('providerMessage'), false)
})

test('benchmark handler selects only an allow-listed stable model', async () => {
  const selected = []
  const providerFactory = model => {
    selected.push(model)
    return {
      model,
      execute: async request => ({
        data: {
          candidates: [{ content: { parts: [{ text: JSON.stringify(nutrition) }] } }],
          modelVersion: model
        },
        metrics: { attempt: 1, providerMs: 10, totalMs: 10, requestBytes: request.requestBytes, imageBytes: request.imageBytes, cacheHit: false }
      })
    }
  }
  const handler = createVisionHandler({
    provider: providerFactory('gemini-3.5-flash'),
    providerFactory,
    allowModelOverride: true
  })
  const res = responseRecorder()

  await handler({
    body: { ...payload(), model: 'gemini-3.1-flash-lite', thinkingLevel: 'minimal' },
    user: { id: 'benchmark-user' }
  }, res)

  assert.equal(res.statusCode, 200)
  assert.equal(res.body.modelVersion, 'gemini-3.1-flash-lite')
  assert.equal(selected.at(-1), 'gemini-3.1-flash-lite')
})

test('benchmark handler rejects a model outside the allow-list before provider execution', async () => {
  let calls = 0
  const provider = {
    model: 'gemini-3.5-flash',
    execute: async () => { calls += 1; throw new Error('must not run') }
  }
  const handler = createVisionHandler({ provider, providerFactory: () => provider, allowModelOverride: true })
  const res = responseRecorder()

  await handler({ body: { ...payload(), model: 'unapproved-model' }, user: { id: 'benchmark-user' } }, res)

  assert.equal(res.statusCode, 400)
  assert.equal(res.body.code, 'UNSUPPORTED_BENCHMARK_MODEL')
  assert.equal(calls, 0)
})

test('text benchmark handler returns normalized nutrition with sanitized metrics', async () => {
  const provider = {
    model: 'gemini-3.5-flash',
    execute: async request => ({
      data: {
        candidates: [{ content: { parts: [{ text: JSON.stringify(nutrition) }] } }],
        modelVersion: 'gemini-3.5-flash'
      },
      metrics: { attempt: 1, providerMs: 15, requestBytes: request.requestBytes, imageBytes: 0, cacheHit: false }
    })
  }
  const handler = createTextBenchmarkHandler({ providerFactory: () => provider })
  const res = responseRecorder()

  await handler({
    body: {
      requestId: '123e4567-e89b-42d3-a456-426614174001',
      description: 'Una ensalada mediana con lechuga y tomate.',
      model: 'gemini-3.5-flash',
      thinkingLevel: 'minimal'
    },
    user: { id: 'benchmark-user' }
  }, res)

  assert.equal(res.statusCode, 200)
  assert.deepEqual(res.body.nutrition, nutrition)
  assert.equal(res.body.metrics.imageBytes, 0)
  assert.equal(JSON.stringify(res.body).includes('Una ensalada mediana'), false)
})

test('text benchmark handler rejects blank descriptions before provider execution', async () => {
  let calls = 0
  const provider = { model: 'gemini-3.5-flash', execute: async () => { calls += 1 } }
  const handler = createTextBenchmarkHandler({ providerFactory: () => provider })
  const res = responseRecorder()

  await handler({
    body: {
      requestId: '123e4567-e89b-42d3-a456-426614174002',
      description: '   ',
      model: 'gemini-3.5-flash',
      thinkingLevel: 'minimal'
    },
    user: { id: 'benchmark-user' }
  }, res)

  assert.equal(res.statusCode, 400)
  assert.equal(res.body.code, 'INVALID_TEXT_DESCRIPTION')
  assert.equal(calls, 0)
})

test('benchmark endpoints reject normal JWT users and allow the static backend credential', () => {
  let nextCalls = 0
  const denied = responseRecorder()
  requireBenchmarkAccess({ user: { id: 'normal-user' } }, denied, () => { nextCalls += 1 })
  assert.equal(denied.statusCode, 403)
  assert.equal(denied.body.code, 'BENCHMARK_ACCESS_DENIED')

  requireBenchmarkAccess({ user: { id: 'default_admin_user' } }, responseRecorder(), () => { nextCalls += 1 })
  assert.equal(nextCalls, 1)
})

test('reports malformed provider JSON with a distinct sanitized code', async () => {
  const provider = {
    model: 'gemini-3.5-flash',
    execute: async request => ({
      data: { candidates: [{ content: { parts: [{ text: '{not-json' }] } }] },
      metrics: { attempt: 1, providerMs: 10, requestBytes: request.requestBytes, imageBytes: request.imageBytes, cacheHit: false }
    })
  }
  const handler = createVisionHandler({ provider })
  const res = responseRecorder()

  await handler({ body: payload(), user: { id: 'user-1' } }, res)

  assert.equal(res.statusCode, 422)
  assert.equal(res.body.code, 'INVALID_PROVIDER_JSON')
  assert.equal(JSON.stringify(res.body).includes('{not-json'), false)
})
