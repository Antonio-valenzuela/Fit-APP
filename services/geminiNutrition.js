'use strict'

const NUTRITION_FIELDS = Object.freeze([
  'foodName',
  'calories',
  'protein',
  'carbs',
  'fat',
  'portionEstimate',
  'confidence',
  'notes',
  'ingredients'
])

const NUTRITION_SCHEMA = Object.freeze({
  type: 'object',
  properties: {
    foodName: { type: 'string', minLength: 1 },
    calories: { type: 'number', minimum: 0 },
    protein: { type: 'number', minimum: 0 },
    carbs: { type: 'number', minimum: 0 },
    fat: { type: 'number', minimum: 0 },
    portionEstimate: { type: 'string', minLength: 1 },
    confidence: { type: 'number', minimum: 0, maximum: 1 },
    notes: { type: 'string' },
    ingredients: { type: 'array', items: { type: 'string' } }
  },
  required: NUTRITION_FIELDS,
  additionalProperties: false
})

const NUTRITION_PROMPT = [
  'Analiza la unica imagen como un registro personal de nutricion.',
  'Identifica la comida visible y estima la porcion, calorias y macronutrientes.',
  'No uses herramientas, busquedas ni datos externos.',
  'Si no hay comida o no puede identificarse, usa foodName "Comida no identificada",',
  'confidence 0, valores nutricionales 0 y explica brevemente el motivo en notes.',
  'Las cifras son estimaciones visuales, no mediciones medicas.'
].join(' ')

class NutritionValidationError extends Error {
  constructor(code, field, message) {
    super(message)
    this.name = 'NutritionValidationError'
    this.code = code
    this.field = field || null
  }
}

function buildGeminiRequest(imageBase64, mimeType = 'image/jpeg') {
  return {
    contents: [{
      role: 'user',
      parts: [
        { inline_data: { mime_type: mimeType, data: imageBase64 } },
        { text: NUTRITION_PROMPT }
      ]
    }],
    generationConfig: {
      responseMimeType: 'application/json',
      responseJsonSchema: NUTRITION_SCHEMA,
      maxOutputTokens: 512,
      thinkingConfig: { thinkingLevel: 'minimal' }
    }
  }
}

function extractStructuredText(envelope) {
  const candidates = Array.isArray(envelope?.candidates) ? envelope.candidates : []
  const usable = []
  for (const candidate of candidates) {
    const parts = Array.isArray(candidate?.content?.parts) ? candidate.content.parts : []
    for (const part of parts) {
      if (part?.thought === true) continue
      if (typeof part?.text === 'string' && part.text.trim()) usable.push(part.text.trim())
    }
  }
  if (usable.length === 0) {
    throw new NutritionValidationError('MISSING_STRUCTURED_OUTPUT', null, 'Gemini no devolvio salida nutricional utilizable.')
  }
  return usable[usable.length - 1]
}

function requireText(value, field, allowEmpty = false) {
  if (typeof value !== 'string' || (!allowEmpty && !value.trim())) {
    throw new NutritionValidationError('INVALID_TEXT', field, `El campo ${field} debe ser texto${allowEmpty ? '' : ' no vacio'}.`)
  }
  return value.trim()
}

function requireNonNegativeNumber(value, field) {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) {
    throw new NutritionValidationError('INVALID_NON_NEGATIVE_NUMBER', field, `El campo ${field} debe ser un numero no negativo.`)
  }
  return value
}

function validateNutrition(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new NutritionValidationError('INVALID_NUTRITION_OBJECT', null, 'La salida nutricional debe ser un objeto.')
  }

  for (const field of NUTRITION_FIELDS) {
    if (!Object.prototype.hasOwnProperty.call(value, field)) {
      throw new NutritionValidationError('MISSING_FIELD', field, `Falta el campo obligatorio ${field}.`)
    }
  }
  for (const field of Object.keys(value)) {
    if (!NUTRITION_FIELDS.includes(field)) {
      throw new NutritionValidationError('UNKNOWN_FIELD', field, `El campo ${field} no pertenece al contrato nutricional.`)
    }
  }

  const confidence = value.confidence
  if (typeof confidence !== 'number' || !Number.isFinite(confidence) || confidence < 0 || confidence > 1) {
    throw new NutritionValidationError('INVALID_CONFIDENCE', 'confidence', 'La confianza debe estar entre 0 y 1.')
  }
  if (!Array.isArray(value.ingredients)) {
    throw new NutritionValidationError('INVALID_LIST', 'ingredients', 'La lista de ingredientes no puede ser nula.')
  }
  const ingredients = value.ingredients.map((ingredient, index) =>
    requireText(ingredient, `ingredients[${index}]`)
  )

  return {
    foodName: requireText(value.foodName, 'foodName'),
    calories: requireNonNegativeNumber(value.calories, 'calories'),
    protein: requireNonNegativeNumber(value.protein, 'protein'),
    carbs: requireNonNegativeNumber(value.carbs, 'carbs'),
    fat: requireNonNegativeNumber(value.fat, 'fat'),
    portionEstimate: requireText(value.portionEstimate, 'portionEstimate'),
    confidence,
    notes: requireText(value.notes, 'notes', true),
    ingredients
  }
}

function parseNutritionEnvelope(envelope) {
  const text = extractStructuredText(envelope)
  let decoded
  try {
    decoded = JSON.parse(text)
  } catch {
    throw new NutritionValidationError('INVALID_PROVIDER_JSON', null, 'Gemini devolvio una salida estructurada invalida.')
  }
  return {
    nutrition: validateNutrition(decoded),
    modelVersion: typeof envelope?.modelVersion === 'string' ? envelope.modelVersion : null
  }
}

function classifyProviderError(status) {
  if (status === 503) return { code: 'PROVIDER_UNAVAILABLE', retryable: true }
  if (status === 504) return { code: 'PROVIDER_TIMEOUT', retryable: true }
  if (status === 429) return { code: 'PROVIDER_RATE_LIMIT', retryable: false }
  if (status >= 400 && status < 500) return { code: 'PROVIDER_VALIDATION', retryable: false }
  return { code: 'PROVIDER_ERROR', retryable: false }
}

module.exports = {
  NUTRITION_SCHEMA,
  NutritionValidationError,
  buildGeminiRequest,
  extractStructuredText,
  validateNutrition,
  parseNutritionEnvelope,
  classifyProviderError
}
