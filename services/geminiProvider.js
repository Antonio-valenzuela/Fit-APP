'use strict'

const { classifyProviderError } = require('./geminiNutrition')

class ProviderRequestError extends Error {
  constructor({ code, status, retryable = false, attempt = 0, retryAfterMs = null, providerMessage = null }) {
    super(code)
    this.name = 'ProviderRequestError'
    this.code = code
    this.status = status
    this.retryable = retryable
    this.attempt = attempt
    this.retryAfterMs = retryAfterMs
    this.providerMessage = providerMessage
  }
}

function parseRetryAfterMs(value, nowMs = Date.now()) {
  if (typeof value !== 'string' || !value.trim()) return null
  const seconds = Number(value)
  if (Number.isFinite(seconds) && seconds >= 0) return Math.round(seconds * 1000)
  const dateMs = Date.parse(value)
  if (!Number.isFinite(dateMs)) return null
  return Math.max(0, dateMs - nowMs)
}

function parseProviderMessage(bodyText) {
  try {
    const decoded = JSON.parse(bodyText)
    return typeof decoded?.error?.message === 'string' ? decoded.error.message.slice(0, 500) : null
  } catch {
    return null
  }
}

class GeminiProvider {
  constructor({
    apiKey,
    model = process.env.GEMINI_MODEL || 'gemini-3.5-flash',
    fetchFn = globalThis.fetch,
    now = Date.now,
    sleep = ms => new Promise(resolve => setTimeout(resolve, ms)),
    random = Math.random,
    logger = console,
    attemptTimeoutMs = Number(process.env.GEMINI_ATTEMPT_TIMEOUT_MS) || 20_000,
    maxRetries = 1,
    failureThreshold = 3,
    circuitOpenMs = 30_000,
    cacheTtlMs = 5 * 60_000
  }) {
    this.apiKey = apiKey
    this.model = model
    this.fetchFn = fetchFn
    this.now = now
    this.sleep = sleep
    this.random = random
    this.logger = logger
    this.attemptTimeoutMs = attemptTimeoutMs
    this.maxRetries = maxRetries
    this.failureThreshold = failureThreshold
    this.circuitOpenMs = circuitOpenMs
    this.cacheTtlMs = cacheTtlMs
    this.inflight = new Map()
    this.activeUsers = new Map()
    this.completed = new Map()
    this.consecutiveFailures = 0
    this.circuitOpenUntil = 0
  }

  get url() {
    return `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(this.model)}:generateContent`
  }

  execute(request) {
    const key = `${request.userId}:${request.requestId}`
    const currentTime = this.now()
    const cached = this.completed.get(key)
    if (cached && cached.expiresAt > currentTime) {
      return Promise.resolve({
        data: cached.result.data,
        metrics: { ...cached.result.metrics, cacheHit: true }
      })
    }
    if (cached) this.completed.delete(key)

    const duplicate = this.inflight.get(key)
    if (duplicate) return duplicate

    if (this.circuitOpenUntil > currentTime) {
      return Promise.reject(new ProviderRequestError({
        code: 'CIRCUIT_OPEN',
        status: 503,
        retryable: false
      }))
    }

    if (this.activeUsers.has(request.userId)) {
      return Promise.reject(new ProviderRequestError({
        code: 'CONCURRENT_REQUEST',
        status: 409,
        retryable: false
      }))
    }

    this.activeUsers.set(request.userId, key)
    const operation = this.#executeAttempts(request)
      .then(result => {
        this.consecutiveFailures = 0
        this.completed.set(key, { result, expiresAt: this.now() + this.cacheTtlMs })
        return result
      })
      .catch(error => {
        if (error instanceof ProviderRequestError && error.status >= 500) {
          this.consecutiveFailures += 1
          if (this.consecutiveFailures >= this.failureThreshold) {
            this.circuitOpenUntil = this.now() + this.circuitOpenMs
          }
        }
        throw error
      })
      .finally(() => {
        this.inflight.delete(key)
        if (this.activeUsers.get(request.userId) === key) this.activeUsers.delete(request.userId)
      })
    this.inflight.set(key, operation)
    return operation
  }

  async #executeAttempts(request) {
    const startedAt = this.now()
    const attempts = []
    for (let attempt = 1; attempt <= this.maxRetries + 1; attempt += 1) {
      const attemptStartedAt = this.now()
      const controller = new AbortController()
      const timeoutId = setTimeout(() => controller.abort(), this.attemptTimeoutMs)
      try {
        const response = await this.fetchFn(this.url, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-goog-api-key': this.apiKey
          },
          body: JSON.stringify(request.providerBody),
          signal: controller.signal
        })
        const bodyText = await response.text()
        const providerMs = this.now() - attemptStartedAt
        const retryAfterMs = parseRetryAfterMs(response.headers?.get?.('retry-after'), this.now())
        attempts.push({ attempt, status: response.status, providerMs, retryAfterMs })

        if (response.ok) {
          let data
          try {
            data = JSON.parse(bodyText)
          } catch {
            throw new ProviderRequestError({
              code: 'PROVIDER_INVALID_JSON',
              status: 502,
              retryable: false,
              attempt
            })
          }
          const result = {
            data,
            metrics: {
              attempt,
              providerMs: attempts.reduce((total, item) => total + item.providerMs, 0),
              totalMs: this.now() - startedAt,
              requestBytes: request.requestBytes,
              imageBytes: request.imageBytes,
              cacheHit: false,
              attempts
            }
          }
          this.logger.info?.({
            event: 'gemini_request_complete',
            requestId: request.requestId,
            model: this.model,
            status: response.status,
            requestBytes: request.requestBytes,
            imageBytes: request.imageBytes,
            attempt,
            totalMs: result.metrics.totalMs
          })
          return result
        }

        const classification = classifyProviderError(response.status)
        const error = new ProviderRequestError({
          ...classification,
          status: response.status,
          attempt,
          retryAfterMs,
          providerMessage: parseProviderMessage(bodyText)
        })
        this.logger.warn?.({
          event: 'gemini_request_failed',
          requestId: request.requestId,
          model: this.model,
          status: response.status,
          code: error.code,
          requestBytes: request.requestBytes,
          imageBytes: request.imageBytes,
          attempt,
          retryable: error.retryable
        })
        if (!error.retryable || attempt > this.maxRetries) throw error
        const delayMs = retryAfterMs ?? Math.round(400 + (this.random() * 400))
        await this.sleep(Math.min(delayMs, 5_000))
      } catch (error) {
        if (error instanceof ProviderRequestError) throw error
        const isTimeout = error?.name === 'AbortError'
        const wrapped = new ProviderRequestError({
          code: isTimeout ? 'PROVIDER_TIMEOUT' : 'PROVIDER_NETWORK',
          status: isTimeout ? 504 : 502,
          retryable: isTimeout,
          attempt
        })
        this.logger.warn?.({
          event: 'gemini_request_failed',
          requestId: request.requestId,
          model: this.model,
          status: wrapped.status,
          code: wrapped.code,
          requestBytes: request.requestBytes,
          imageBytes: request.imageBytes,
          attempt,
          retryable: wrapped.retryable
        })
        if (!wrapped.retryable || attempt > this.maxRetries) throw wrapped
        await this.sleep(Math.round(400 + (this.random() * 400)))
      } finally {
        clearTimeout(timeoutId)
      }
    }
    throw new ProviderRequestError({ code: 'PROVIDER_ERROR', status: 502 })
  }
}

module.exports = {
  GeminiProvider,
  ProviderRequestError,
  parseRetryAfterMs
}
