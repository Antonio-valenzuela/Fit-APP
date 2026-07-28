'use strict'

const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')

const root = path.resolve(__dirname, '..')
const datasetRoot = path.join(root, 'test-data', 'gemini-food-benchmark')
const manifest = JSON.parse(fs.readFileSync(path.join(datasetRoot, 'manifest.json'), 'utf8'))
const caseBook = JSON.parse(fs.readFileSync(path.join(datasetRoot, 'benchmark-cases.json'), 'utf8'))
const phase = process.argv[2] || 'validate'
const pauseMs = Number(process.env.BENCHMARK_PAUSE_MS || 4000)
const allowedPhases = new Set(['validate', 'pilots', 'full'])
if (!allowedPhases.has(phase)) throw new Error('Fase permitida: validate, pilots o full.')

const nutritionFields = ['foodName', 'calories', 'protein', 'carbs', 'fat', 'portionEstimate', 'confidence', 'notes', 'ingredients']
const nutritionSchema = {
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
  required: nutritionFields,
  additionalProperties: false
}
const prompt = [
  'Analiza esta entrada como un registro personal de nutricion.',
  'Identifica la comida y estima porcion, calorias y macronutrientes.',
  'No uses herramientas, busquedas ni datos externos.',
  'Si no hay comida o no puede identificarse, usa foodName "Comida no identificada", confidence 0, valores nutricionales 0 y explica brevemente el motivo en notes.',
  'Las cifras son estimaciones, no mediciones medicas.'
].join(' ')

function readEnv(name) {
  const lines = fs.readFileSync(path.join(root, '.env'), 'utf8').split(/\r?\n/)
  const prefix = `${name}=`
  const line = lines.find(value => value.trimStart().startsWith(prefix))
  return line ? line.trim().slice(prefix.length).replace(/^['"]|['"]$/g, '') : null
}

function sha256(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')
}

function assertDataset() {
  for (const item of manifest.cases) {
    const original = path.join(datasetRoot, 'originals', item.fileName)
    if (!fs.existsSync(original) || sha256(original) !== item.original.sha256) throw new Error(`Hash original distinto: ${item.id}`)
    for (const profile of ['conservative', 'balanced', 'aggressive', 'realistic']) {
      const prepared = item.prepared[profile]
      const file = path.join(datasetRoot, prepared.file)
      if (!fs.existsSync(file) || sha256(file) !== prepared.sha256) throw new Error(`Hash preparado distinto: ${item.id}/${profile}`)
    }
  }
  console.log(`Dataset verificado: ${manifest.caseCount} originales y 40 variantes; hashes SHA-256 correctos.`)
}

function generationConfig(thinkingLevel = 'minimal') {
  return {
    responseMimeType: 'application/json',
    responseJsonSchema: nutritionSchema,
    maxOutputTokens: 512,
    thinkingConfig: { thinkingLevel }
  }
}

function textPayload(item) {
  return {
    contents: [{ role: 'user', parts: [{ text: `${prompt} Descripcion controlada, sin imagen: ${item.description}` }] }],
    generationConfig: generationConfig()
  }
}

function imagePayload(item, profile) {
  const prepared = item.prepared[profile]
  const bytes = fs.readFileSync(path.join(datasetRoot, prepared.file))
  return {
    payload: {
      contents: [{ role: 'user', parts: [
        { inline_data: { mime_type: 'image/jpeg', data: bytes.toString('base64') } },
        { text: prompt }
      ] }],
      generationConfig: generationConfig()
    },
    bytes: bytes.length
  }
}

function validateNutrition(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('INVALID_NUTRITION_RESULT')
  if (Object.keys(value).some(field => !nutritionFields.includes(field))) throw new Error('INVALID_NUTRITION_RESULT')
  for (const field of nutritionFields) if (!Object.hasOwn(value, field)) throw new Error('INVALID_NUTRITION_RESULT')
  if (!value.foodName.trim() || !value.portionEstimate.trim() || !Array.isArray(value.ingredients)) throw new Error('INVALID_NUTRITION_RESULT')
  for (const field of ['calories', 'protein', 'carbs', 'fat']) if (!Number.isFinite(value[field]) || value[field] < 0) throw new Error('INVALID_NUTRITION_RESULT')
  if (!Number.isFinite(value.confidence) || value.confidence < 0 || value.confidence > 1) throw new Error('INVALID_NUTRITION_RESULT')
  return value
}

function parseEnvelope(envelope) {
  const texts = []
  for (const candidate of Array.isArray(envelope?.candidates) ? envelope.candidates : []) {
    for (const part of Array.isArray(candidate?.content?.parts) ? candidate.content.parts : []) {
      if (part?.thought !== true && typeof part?.text === 'string' && part.text.trim()) texts.push(part.text.trim())
    }
  }
  if (!texts.length) throw new Error('INVALID_NUTRITION_RESULT')
  let decoded
  try { decoded = JSON.parse(texts.at(-1)) } catch { throw new Error('INVALID_PROVIDER_JSON') }
  return { nutrition: validateNutrition(decoded), modelVersion: typeof envelope?.modelVersion === 'string' ? envelope.modelVersion : null }
}

function score(nutrition, rule) {
  if (!nutrition) return { identificationPass: false, plausibilityPass: false, qualityPass: false }
  const searchable = [nutrition.foodName, ...nutrition.ingredients].join(' ').toLowerCase()
  const identificationPass = rule.keywords.some(keyword => searchable.includes(keyword.toLowerCase()))
  const plausibilityPass = nutrition.calories >= rule.caloriesMin && nutrition.calories <= rule.caloriesMax
  return { identificationPass, plausibilityPass, qualityPass: identificationPass && plausibilityPass }
}

function percentile(values, percent) {
  if (!values.length) return null
  const sorted = [...values].sort((a, b) => a - b)
  return sorted[Math.max(0, Math.ceil((percent / 100) * sorted.length) - 1)]
}

function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)) }

async function requestCase({ endpoint, apiKey, providerBody, metadata, rule, imageBytes }) {
  const requestText = JSON.stringify(providerBody)
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 65_000)
  const started = performance.now()
  let httpStatus = 0
  let outcome = 'other'
  let normalized = null
  let safeError = null
  try {
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
      body: requestText,
      signal: controller.signal
    })
    httpStatus = response.status
    const responseText = await response.text()
    let envelope
    try { envelope = JSON.parse(responseText) } catch { outcome = 'json_invalid' }
    if (httpStatus === 200 && envelope) {
      try {
        normalized = parseEnvelope(envelope)
        outcome = 'success'
      } catch (error) {
        outcome = error.message === 'INVALID_PROVIDER_JSON' ? 'json_invalid' : 'invalid_result'
        safeError = { code: error.message, retryable: false, attempt: 1 }
      }
    } else if (envelope) {
      outcome = httpStatus === 429 ? '429' : httpStatus === 503 ? '503' : httpStatus === 504 ? 'timeout' : 'other'
      safeError = { code: typeof envelope?.error?.status === 'string' ? `PROVIDER_${envelope.error.status}` : `HTTP_${httpStatus}`, retryable: [503, 504].includes(httpStatus), attempt: 1 }
    }
  } catch (error) {
    outcome = error?.name === 'AbortError' ? 'timeout' : 'network_error'
    safeError = { code: outcome === 'timeout' ? 'PROVIDER_TIMEOUT' : 'NETWORK_ERROR', retryable: outcome === 'timeout', attempt: 1 }
  } finally {
    clearTimeout(timeout)
  }
  const latencyMs = Math.round(performance.now() - started)
  const result = {
    ...metadata,
    timestampUtc: new Date().toISOString(),
    httpStatus,
    outcome,
    latencyMs,
    modelVersion: normalized?.modelVersion || null,
    metrics: { requestBytes: Buffer.byteLength(requestText), imageBytes, attempt: 1 },
    nutrition: normalized?.nutrition || null,
    quality: score(normalized?.nutrition, rule),
    error: outcome === 'success' ? null : safeError
  }
  console.log(`${metadata.group.padEnd(17)} ${metadata.caseId.padEnd(20)} HTTP ${String(httpStatus).padEnd(3)} ${String(latencyMs).padStart(7)} ms ${outcome}`)
  return result
}

async function main() {
  assertDataset()
  if (phase === 'validate') return
  const baseUrl = readEnv('BACKEND_BASE_URL')?.replace(/\/$/, '')
  const apiKey = readEnv('BACKEND_API_KEY')
  if (!baseUrl || !apiKey) throw new Error('Faltan BACKEND_BASE_URL o BACKEND_API_KEY en .env.')
  const endpoint = `${baseUrl}/api/gemini/chat`
  const results = []
  const run = async input => {
    results.push(await requestCase({ endpoint, apiKey, ...input }))
    await sleep(pauseMs)
  }

  if (phase === 'pilots') {
    const item = manifest.cases.find(value => value.id === 'simple_apple')
    const requestedPilot = process.env.BENCHMARK_PILOT_PROFILE
    const profiles = requestedPilot ? [requestedPilot] : ['conservative', 'balanced', 'aggressive']
    if (profiles.some(profile => !['conservative', 'balanced', 'aggressive'].includes(profile))) throw new Error('Perfil piloto no permitido.')
    for (const profile of profiles) {
      const prepared = item.prepared[profile]
      const built = imagePayload(item, profile)
      await run({
        providerBody: built.payload,
        imageBytes: built.bytes,
        rule: caseBook.imageCases[item.id],
        metadata: { group: 'pilot', caseId: item.id, profile, configuredModel: 'gemini-flash-latest', thinkingLevel: 'minimal', imageSha256: prepared.sha256, originalBytes: item.original.bytes, preparedBytes: prepared.bytes, originalWidth: item.original.width, originalHeight: item.original.height, preparedWidth: prepared.width, preparedHeight: prepared.height, quality: prepared.quality }
      })
    }
  }

  if (phase === 'full') {
    for (const item of caseBook.textCases) {
      await run({
        providerBody: textPayload(item), imageBytes: 0, rule: item,
        metadata: { group: 'text', caseId: item.id, profile: null, configuredModel: 'gemini-flash-latest', thinkingLevel: 'minimal', imageSha256: null, originalBytes: 0, preparedBytes: 0, originalWidth: 0, originalHeight: 0, preparedWidth: 0, preparedHeight: 0, quality: 0 }
      })
    }
    for (const profile of ['balanced', 'realistic']) {
      for (const item of manifest.cases) {
        const prepared = item.prepared[profile]
        const built = imagePayload(item, profile)
        await run({
          providerBody: built.payload, imageBytes: built.bytes, rule: caseBook.imageCases[item.id],
          metadata: { group: profile === 'balanced' ? 'image_optimized' : 'image_realistic', caseId: item.id, profile, configuredModel: 'gemini-flash-latest', thinkingLevel: 'minimal', imageSha256: prepared.sha256, originalBytes: item.original.bytes, preparedBytes: prepared.bytes, originalWidth: item.original.width, originalHeight: item.original.height, preparedWidth: prepared.width, preparedHeight: prepared.height, quality: prepared.quality }
        })
      }
    }
  }

  const runId = new Date().toISOString().replace(/[-:]/g, '').replace(/\..+/, '')
  const output = path.join(root, 'test-results', 'gemini')
  fs.mkdirSync(output, { recursive: true })
  const jsonl = path.join(output, `${runId}-live-${phase}.jsonl`)
  fs.writeFileSync(jsonl, results.map(value => JSON.stringify(value)).join('\n') + '\n')
  const groups = Object.groupBy(results, value => value.group)
  const summary = {
    runId, phase, datasetId: manifest.datasetId, transport: 'published-backend-chat-proxy', sequential: true, pauseMs,
    resultCount: results.length,
    groups: Object.entries(groups).map(([group, values]) => {
      const successful = values.filter(value => value.outcome === 'success')
      const latencies = successful.map(value => value.latencyMs)
      return {
        group, count: values.length, success: successful.length,
        status429: values.filter(value => value.outcome === '429').length,
        status503: values.filter(value => value.outcome === '503').length,
        timeout: values.filter(value => value.outcome === 'timeout').length,
        jsonInvalid: values.filter(value => value.outcome === 'json_invalid').length,
        invalidResult: values.filter(value => value.outcome === 'invalid_result').length,
        p50Ms: percentile(latencies, 50), p95Ms: percentile(latencies, 95),
        meanPreparedBytes: Math.round(values.reduce((sum, value) => sum + value.preparedBytes, 0) / values.length),
        qualityPass: values.filter(value => value.quality.qualityPass).length
      }
    })
  }
  const summaryFile = path.join(output, `${runId}-live-${phase}-summary.json`)
  fs.writeFileSync(summaryFile, JSON.stringify(summary, null, 2) + '\n')
  console.log(`Resultados sanitizados: ${jsonl}`)
  console.log(`Resumen: ${summaryFile}`)
}

main().catch(error => {
  console.error(`Benchmark detenido: ${error.message}`)
  process.exitCode = 1
})
