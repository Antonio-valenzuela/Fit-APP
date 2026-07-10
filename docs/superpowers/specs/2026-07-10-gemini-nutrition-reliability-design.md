# Gemini Nutrition Reliability Design

**Date:** 2026-07-10

## Objective

Make the existing Android -> Render -> Gemini food-photo flow measurable, safe to parse, cancellable, and resilient without changing infrastructure. Produce a reproducible benchmark and device-QA report that distinguishes verified results from untested behavior.

## Verified starting point

- Android calls `POST /api/gemini/vision` through `GeminiService` with a Gemini-shaped JSON body containing one Base64 JPEG and one prompt.
- Render forwards the body through native `fetch` to the `generateContent` REST API. It does not use a Google SDK.
- The configured alias `gemini-flash-latest` returned `modelVersion=gemini-3.5-flash` on 2026-07-10.
- Android has 30 s connect, 60 s write, 90 s read, and 120 s total call timeouts. Render uses 60 s locally unless `GEMINI_TIMEOUT_MS` overrides it.
- Android currently reads only `candidates[0].content.parts[0].text`; it does not deserialize the complete provider envelope. This is unsafe when the usable text is not the first part.
- A live request returned HTTP 503 after 51.733 s with Google status `UNAVAILABLE` and a high-demand message. No evidence currently attributes this to Base64, Render, or account quota.
- The pre-change unit baseline is 116 tests with one palette expectation failure.

## Approaches considered

1. **Android-only parser patch.** Smallest change, but it leaves provider envelopes, retries, timing, idempotency, and model selection distributed across the client.
2. **Backend normalization plus Android validation.** Keep the existing Render service and endpoint, but make `/vision` own the provider contract and return a stable app DTO. This is the selected approach.
3. **Migrate to the Google SDK or Interactions API.** Not selected because it changes more variables and is unnecessary for a single-turn extraction flow.

## Architecture

### Android request boundary

Android sends a purpose-built request with:

- one JPEG encoded once as Base64;
- MIME type;
- client-generated idempotency key;
- original and prepared image metrics;
- no conversational history, tools, grounding, or user health data.

The request logger records byte counts, dimensions, attempt number, and timings. It never records authorization headers, Base64 content, the image, or the full provider response.

### Image preparation

Image preparation is a pure, testable pipeline:

1. read bounds before allocating the bitmap;
2. decode with sampling when appropriate;
3. apply EXIF rotation/flip;
4. resize while preserving aspect ratio;
5. encode once as JPEG;
6. reject an excessive final payload with a friendly message;
7. retain the captured URI, preview, and entered fields until the user saves or explicitly discards them.

Production uses the benchmark-selected profile. Benchmark profiles are fixed initially at 1600/85, 1280/75, and 960/65. The same source hashes are used for every comparison.

### Render provider boundary

`/api/gemini/vision` validates the app DTO, enforces a request-size limit, builds the provider prompt and JSON schema, and calls the versioned stable model `gemini-3.5-flash`. It uses `generateContent`, `responseMimeType=application/json`, `responseJsonSchema`, a bounded output size, and `thinkingLevel=minimal` for the extraction benchmark.

The backend searches all candidate parts for the corresponding non-thinking text/structured output. `thoughtSignature`, thinking blocks, safety metadata, usage metadata, and any other protocol fields never enter the nutrition DTO.

### Nutrition contract

The normalized response contains:

- `requestId` and `modelVersion`;
- `foodName` (nonblank);
- nonnegative calories, protein, carbohydrates, and fat;
- nonblank portion estimate;
- numeric confidence from 0 to 1;
- non-null notes and ingredients;
- timing and size metadata that contains no sensitive content.

Provider schema constraints and application validation are both required. Unknown protocol fields are ignored at the provider-envelope boundary. Unknown nutrition fields are rejected by backend application validation. Android performs its own defensive validation before displaying the result.

### Failure behavior

- Invalid provider output returns a stable non-JSON user message and is never saved automatically.
- Android keeps the photo preview, URI, manual description, and entered nutrition values.
- 400/401/403/413/422 are not retried automatically.
- 503/504 receive at most one automatic retry, honoring `Retry-After` and otherwise using bounded backoff with jitter.
- A small in-memory circuit breaker opens temporarily after consecutive provider failures.
- One in-flight request per authenticated user/request key is allowed.
- Completed idempotent results may be cached briefly by request key; failures and arbitrary user images are not persisted.
- Leaving the screen cancels the client call. Rotation/recreation keeps UI state and the current result through the ViewModel.
- The UI always offers Retry, Manual entry, Local estimate, and Cancel; no infinite spinner is allowed.

## Benchmark design

### Dataset

Store only the selected public/open images under `test-data/gemini-food-benchmark/`, with a manifest containing source URL, license, attribution, SHA-256, category, expected visible food, original dimensions, format, and size. Do not access device photos.

The ten source cases cover simple food, Mexican food, mixed ingredients, beverage, large portion, ambiguous food, empty plate, blurred image, non-food object, and one additional varied meal.

### Controlled runs

- Ten text cases.
- Ten optimized-image cases.
- Ten realistic-size image cases.
- Three one-image compression pilots before full image runs.
- No parallel provider requests.
- Change one variable at a time: first compression with fixed model/prompt, then model with the selected compression/prompt.
- Compare current stable model, a compatible Flash-Lite alternative, and Gemini 3.5 Flash with reduced thinking only when account availability is confirmed.

Each row records timestamp, source hash, prepared hash, model, prompt version, original/prepared sizes, dimensions, quality, status, total/provider latency, attempt, structured-output validity, identification rubric, nutrition plausibility rubric, and sanitized error classification.

`p50` uses the median and `p95` uses nearest-rank percentile over the defined run group. No single successful request supports a performance claim.

## Testing and QA

- Node tests cover provider-part extraction, thought signatures, thinking parts, extra fields, invalid JSON, missing fields, negative values, retry rules, circuit breaker, concurrency, idempotency, and log sanitization.
- Android unit tests cover envelope/DTO parsing, validation, image orientation/scaling/encoding, retained UI state, cancellation, and friendly error mapping.
- Existing 116 tests must pass before the image benchmark begins.
- Final verification includes clean build, unit tests, lint, available instrumentation tests, ADB install, launch, filtered Logcat, and the required device flows.
- Every functional flow is reported as Approved, Failed, Approved with observations, or Not verified, with explicit persistence/crash/ANR/log evidence.

## Non-goals

- No Vertex AI migration.
- No new paid service or infrastructure change.
- No claim that a model guarantees a latency target or eliminates provider failures.
- No access to the device gallery, personal files, or personal photographs.
