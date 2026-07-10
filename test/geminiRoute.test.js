'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')

const geminiRouter = require('../routes/gemini')
const { ProviderRequestError } = require('../services/geminiProvider')

const { createVisionHandler, validateVisionPayload } = geminiRouter.testing

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
