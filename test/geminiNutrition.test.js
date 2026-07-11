'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')

const {
  NUTRITION_SCHEMA,
  NutritionValidationError,
  buildGeminiRequest,
  buildGeminiTextRequest,
  extractStructuredText,
  validateNutrition,
  parseNutritionEnvelope,
  classifyProviderError
} = require('../services/geminiNutrition')

const validNutrition = Object.freeze({
  foodName: 'Tacos de pollo',
  calories: 520,
  protein: 32,
  carbs: 54,
  fat: 20,
  portionEstimate: '3 tacos medianos',
  confidence: 0.82,
  notes: 'Estimación visual aproximada.',
  ingredients: ['tortilla', 'pollo', 'cebolla']
})

function envelopeWith(value, extraPart = {}) {
  return {
    candidates: [{
      content: {
        role: 'model',
        parts: [
          { thought: true, text: 'bloque de razonamiento', thoughtSignature: 'opaque-thinking' },
          { text: typeof value === 'string' ? value : JSON.stringify(value), thoughtSignature: 'opaque-final', ...extraPart }
        ]
      },
      finishReason: 'STOP',
      safetyRatings: [{ category: 'HARM_CATEGORY_DANGEROUS_CONTENT', probability: 'NEGLIGIBLE' }]
    }],
    modelVersion: 'gemini-3.5-flash',
    usageMetadata: { promptTokenCount: 10, candidatesTokenCount: 20 },
    extraProtocolField: true
  }
}

test('extracts valid structured output and ignores thinking and thoughtSignature metadata', () => {
  const parsed = parseNutritionEnvelope(envelopeWith(validNutrition, { extraProtocolPartField: true }))

  assert.deepEqual(parsed, {
    nutrition: validNutrition,
    modelVersion: 'gemini-3.5-flash'
  })
})

test('extractStructuredText selects the non-thinking text part', () => {
  assert.equal(extractStructuredText(envelopeWith(validNutrition)), JSON.stringify(validNutrition))
})

test('rejects invalid JSON without returning provider content', () => {
  assert.throws(
    () => parseNutritionEnvelope(envelopeWith('{not-json')),
    error => error instanceof NutritionValidationError && error.code === 'INVALID_PROVIDER_JSON'
  )
})

test('rejects every absent required field', () => {
  for (const field of Object.keys(validNutrition)) {
    const value = { ...validNutrition }
    delete value[field]
    assert.throws(
      () => validateNutrition(value),
      error => error instanceof NutritionValidationError && error.code === 'MISSING_FIELD' && error.field === field
    )
  }
})

test('rejects negative calories and macronutrients', () => {
  for (const field of ['calories', 'protein', 'carbs', 'fat']) {
    assert.throws(
      () => validateNutrition({ ...validNutrition, [field]: -0.1 }),
      error => error instanceof NutritionValidationError && error.code === 'INVALID_NON_NEGATIVE_NUMBER' && error.field === field
    )
  }
})

test('rejects confidence outside zero to one', () => {
  for (const confidence of [-0.01, 1.01, 'high']) {
    assert.throws(
      () => validateNutrition({ ...validNutrition, confidence }),
      error => error instanceof NutritionValidationError && error.code === 'INVALID_CONFIDENCE'
    )
  }
})

test('rejects null ingredients and blank names', () => {
  assert.throws(
    () => validateNutrition({ ...validNutrition, ingredients: null }),
    error => error instanceof NutritionValidationError && error.code === 'INVALID_LIST'
  )
  assert.throws(
    () => validateNutrition({ ...validNutrition, foodName: '   ' }),
    error => error instanceof NutritionValidationError && error.code === 'INVALID_TEXT'
  )
})

test('rejects additional nutrition fields while ignoring protocol fields', () => {
  assert.throws(
    () => validateNutrition({ ...validNutrition, thoughtSignature: 'must-not-enter-dto' }),
    error => error instanceof NutritionValidationError && error.code === 'UNKNOWN_FIELD'
  )
})

test('builds a strict structured request for the stable model configuration', () => {
  const request = buildGeminiRequest('abc123', 'image/jpeg')

  assert.equal(request.generationConfig.responseMimeType, 'application/json')
  assert.equal(request.generationConfig.thinkingConfig.thinkingLevel, 'minimal')
  assert.equal(request.generationConfig.responseJsonSchema.additionalProperties, false)
  assert.deepEqual(request.generationConfig.responseJsonSchema.required, NUTRITION_SCHEMA.required)
  assert.equal(request.contents.length, 1)
  assert.equal(request.contents[0].parts.filter(part => part.inline_data).length, 1)
})

test('builds a text-only nutrition request without an image part', () => {
  const request = buildGeminiTextRequest('Una manzana roja de 180 g.', 'medium')

  assert.equal(request.generationConfig.thinkingConfig.thinkingLevel, 'medium')
  assert.equal(request.contents[0].parts.filter(part => part.inline_data).length, 0)
  assert.equal(request.contents[0].parts.length, 1)
  assert.match(request.contents[0].parts[0].text, /manzana roja de 180 g/)
})

test('classifies retryable provider statuses without guessing their cause', () => {
  assert.deepEqual(classifyProviderError(503), { code: 'PROVIDER_UNAVAILABLE', retryable: true })
  assert.deepEqual(classifyProviderError(504), { code: 'PROVIDER_TIMEOUT', retryable: true })
  assert.deepEqual(classifyProviderError(429), { code: 'PROVIDER_RATE_LIMIT', retryable: false })
  assert.deepEqual(classifyProviderError(400), { code: 'PROVIDER_VALIDATION', retryable: false })
})
