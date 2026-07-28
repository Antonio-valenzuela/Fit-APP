'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')

const geminiRouter = require('../routes/gemini')
const { ProviderRequestError } = require('../services/geminiProvider')

const {
  createVisionHandler,
  createTextBenchmarkHandler,
  createChatHandler,
  buildChatProviderBody,
  requireBenchmarkAccess,
  validateVisionPayload,
  validateChatPayload
} = geminiRouter.testing

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

function chatPayload(text = 'Consejo preventivo seguro') {
  return { contents: [{ parts: [{ text }] }] }
}

test('accepts only the exact Android chat wire shape with one bounded text part', () => {
  assert.equal(validateChatPayload(chatPayload('  Consejo seguro  ')), 'Consejo seguro')

  const invalidPayloads = [
    { ...chatPayload(), generationConfig: { temperature: 2 } },
    { contents: [{ parts: [{ text: 'ok' }], role: 'system' }] },
    { contents: [{ parts: [{ text: 'ok', inlineData: 'blocked' }] }] },
    { contents: [{ parts: [{ text: 'one' }, { text: 'two' }] }] },
    { contents: [{ parts: [{ text: 'x'.repeat(12_001) }] }] },
    { contents: [{ parts: [{ text: '   ' }] }] }
  ]
  for (const body of invalidPayloads) {
    assert.throws(() => validateChatPayload(body), error => error.status === 400)
  }
})

test('constructs Gemini chat configuration server-side and returns only the minimal Android response', async () => {
  const calls = []
  const fetchFn = async (url, options) => {
    calls.push({ url, options })
    return {
      ok: true,
      status: 200,
      text: async () => JSON.stringify({
        candidates: [{
          content: { parts: [
            { thought: true, text: 'private reasoning' },
            { text: 'Consejo validado', thoughtSignature: 'opaque' }
          ] },
          finishReason: 'STOP'
        }],
        usageMetadata: { promptTokenCount: 12 }
      })
    }
  }
  const handler = createChatHandler({ fetchFn })
  const req = {
    id: 'request-chat-1234',
    body: chatPayload('  Consejo preventivo seguro  '),
    app: { locals: {
      securityConfig: { geminiApiKey: 'provider-key', geminiModel: 'gemini-3.5-flash', geminiAttemptTimeoutMs: 1000 },
      securityLogger: { warn() {} }
    } }
  }
  const res = responseRecorder()

  await handler(req, res)

  assert.equal(res.statusCode, 200)
  assert.deepEqual(res.body, { candidates: [{ content: { parts: [{ text: 'Consejo validado' }] } }] })
  const upstream = JSON.parse(calls[0].options.body)
  assert.deepEqual(upstream, buildChatProviderBody('Consejo preventivo seguro'))
  assert.deepEqual(upstream.contents, [{ role: 'user', parts: [{ text: 'Consejo preventivo seguro' }] }])
  assert.equal(Object.hasOwn(upstream, 'generationConfig'), true)
  assert.equal(JSON.stringify(res.body).includes('private reasoning'), false)
  assert.equal(JSON.stringify(res.body).includes('thoughtSignature'), false)
  assert.equal(JSON.stringify(res.body).includes('usageMetadata'), false)
})

test('sanitizes unsuccessful and malformed Gemini chat responses', async () => {
  const providerBodies = [
    { ok: false, status: 400, text: async () => '{"error":{"message":"raw provider secret"}}' },
    { ok: true, status: 200, text: async () => '{not-json' },
    { ok: true, status: 200, text: async () => JSON.stringify({ candidates: [] }) }
  ]

  for (const providerResponse of providerBodies) {
    const handler = createChatHandler({ fetchFn: async () => providerResponse })
    const res = responseRecorder()
    await handler({
      id: 'request-chat-error',
      body: chatPayload(),
      app: { locals: {
        securityConfig: { geminiApiKey: 'provider-key', geminiModel: 'gemini-3.5-flash', geminiAttemptTimeoutMs: 1000 },
        securityLogger: { warn() {} }
      } }
    }, res)

    assert.equal(res.statusCode, 502)
    assert.equal(JSON.stringify(res.body).includes('raw provider secret'), false)
    assert.equal(JSON.stringify(res.body).includes('{not-json'), false)
  }
})

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

test('benchmark endpoints reject normal users and allow an authenticated benchmark role', () => {
  let nextCalls = 0
  const denied = responseRecorder()
  requireBenchmarkAccess({ auth: { subject: 'normal-user', roles: ['member'] } }, denied, () => { nextCalls += 1 })
  assert.equal(denied.statusCode, 403)
  assert.deepEqual(denied.body, { error: 'Forbidden' })

  requireBenchmarkAccess({ auth: { subject: 'operator-user', roles: ['benchmark'] } }, responseRecorder(), () => { nextCalls += 1 })
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
