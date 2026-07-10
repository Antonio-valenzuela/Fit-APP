# Gemini Nutrition Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make food-photo analysis measurable, strictly parsed, resilient, and reproducibly benchmarked through the existing Android and Render architecture.

**Architecture:** Android prepares one JPEG and sends a small app-specific DTO. Render owns the Gemini `generateContent` request, extracts only usable structured text from the protocol envelope, validates the nutrition object, and returns a stable DTO with sanitized metrics. Both layers validate defensively; Render controls retry/idempotency/circuit breaking, while Android retains user state and exposes retry/manual/local/cancel actions.

**Tech Stack:** Kotlin 2.2, Jetpack Compose, OkHttp, JUnit/Robolectric, Node.js CommonJS, Express, native `fetch`, `node:test`, PowerShell benchmark runner, ADB.

---

### Task 1: Align the legitimate palette test

**Files:**
- Modify: `app/src/test/java/com/example/ui/theme/FitPlanThemeColorTest.kt`
- Verify: `app/src/main/java/com/example/ui/theme/Color.kt`

- [ ] **Step 1: Confirm the production palette values and installed screenshot**

Run: `git show e54d2d4:app/src/main/java/com/example/ui/theme/Color.kt` and compare the primary purple with the captured Huawei UI.

- [ ] **Step 2: Update the failing expectation only**

Replace the stale green assertion with the exact production `FitPrimary` purple constant while retaining contrast/role assertions.

- [ ] **Step 3: Run the focused test**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.ui.theme.FitPlanThemeColorTest`

Expected: all `FitPlanThemeColorTest` cases pass.

- [ ] **Step 4: Run the complete baseline suite**

Run: `.\gradlew.bat testDebugUnitTest`

Expected: 116 tests, zero failures before any provider benchmark.

- [ ] **Step 5: Commit**

Run: `git add app/src/test/java/com/example/ui/theme/FitPlanThemeColorTest.kt && git commit -m "test: align theme expectation with approved palette"`

### Task 2: Define backend nutrition parsing with failing tests

**Files:**
- Create: `test/geminiNutrition.test.js`
- Create: `services/geminiNutrition.js`
- Modify: `package.json`

- [ ] **Step 1: Add `node:test` cases before implementation**

The tests must call exported pure functions and cover:

```javascript
test('extracts valid structured text while ignoring thinking and thoughtSignature', () => {
  const envelope = {
    candidates: [{ content: { parts: [
      { thought: true, text: 'internal', thoughtSignature: 'opaque' },
      { text: JSON.stringify(validNutrition), thoughtSignature: 'opaque-2', extra: true }
    ] } }],
    modelVersion: 'gemini-3.5-flash'
  }
  assert.deepEqual(parseNutritionEnvelope(envelope).nutrition, validNutrition)
})
```

Add independent cases for invalid JSON, absent required fields, negative macros, confidence outside `[0,1]`, null ingredients, blank names, extra nutrition fields, and an envelope with no usable text.

- [ ] **Step 2: Verify RED**

Run: `node --test test/geminiNutrition.test.js`

Expected: failure because `services/geminiNutrition.js` does not yet export the parser.

- [ ] **Step 3: Implement pure protocol and DTO functions**

Export:

```javascript
module.exports = {
  NUTRITION_SCHEMA,
  buildGeminiRequest,
  extractStructuredText,
  validateNutrition,
  parseNutritionEnvelope,
  classifyProviderError
}
```

`extractStructuredText` iterates candidates and parts, skips `part.thought === true`, and accepts only nonblank `part.text`. `validateNutrition` returns a new allow-listed object and throws a typed validation error for missing/invalid values. It never copies protocol fields.

- [ ] **Step 4: Verify GREEN**

Run: `node --test test/geminiNutrition.test.js`

Expected: all parser and validation cases pass.

- [ ] **Step 5: Add the backend test script and commit**

Set `package.json` script `test` to `node --test test/*.test.js`, run `npm test`, then commit the three files.

### Task 3: Implement measured provider execution and resilience

**Files:**
- Create: `services/geminiProvider.js`
- Create: `test/geminiProvider.test.js`
- Modify: `routes/gemini.js`

- [ ] **Step 1: Write failing tests with injected fetch and clock**

Cover one retry only for 503/504, no retry for 4xx/validation, `Retry-After`, bounded jitter, circuit opening after consecutive failures, one in-flight request per user/idempotency key, completed-result reuse, timeout classification, and sanitized logs.

- [ ] **Step 2: Verify RED**

Run: `node --test test/geminiProvider.test.js`

Expected: missing provider module failure.

- [ ] **Step 3: Implement the provider executor**

Use stable default `GEMINI_MODEL=gemini-3.5-flash`, native `fetch`, `AbortController`, maximum one retry, and an in-memory circuit breaker. Capture `preprocessMs`, `providerMs`, `parseMs`, `totalMs`, `requestBytes`, `imageBytes`, `attempt`, HTTP status, returned `modelVersion`, and `Retry-After` without logging request content.

- [ ] **Step 4: Split route behavior**

Keep `/chat` compatible. Make `/vision` accept only:

```json
{
  "requestId": "UUID",
  "imageBase64": "not logged",
  "mimeType": "image/jpeg",
  "originalBytes": 1,
  "preparedBytes": 1,
  "originalWidth": 1,
  "originalHeight": 1,
  "preparedWidth": 1,
  "preparedHeight": 1,
  "quality": 75
}
```

Validate size and types, construct `responseJsonSchema`, call `generateContent`, normalize the result, and return `{ requestId, modelVersion, nutrition, metrics }`.

- [ ] **Step 5: Verify and commit**

Run: `npm test`

Expected: parser, resilience, and route tests pass with no network calls.

### Task 4: Add Android strict DTO parsing with TDD

**Files:**
- Create: `app/src/main/java/com/example/data/api/GeminiNutritionProtocol.kt`
- Create: `app/src/test/java/com/example/data/api/GeminiNutritionProtocolTest.kt`
- Modify: `app/src/main/java/com/example/data/api/GeminiService.kt`

- [ ] **Step 1: Write failing parser tests**

Fixtures cover the normalized valid response, a legacy provider envelope containing valid text plus `thoughtSignature`, thinking blocks, extra fields, invalid JSON, missing fields, negative values, null lists, and blank names.

- [ ] **Step 2: Verify RED**

Run: `.\gradlew.bat testDebugUnitTest --tests com.example.data.api.GeminiNutritionProtocolTest`

Expected: compile failure because the protocol parser is absent.

- [ ] **Step 3: Implement strict parsing and validation**

Create typed request/response/metrics data classes and a parser that accepts only the normalized backend shape. Map confidence to a numeric `0f..1f`; reject rather than coerce invalid negative values. Convert validation/provider failures to stable Spanish messages without raw JSON.

- [ ] **Step 4: Change `GeminiService` to the app DTO**

Encode the JPEG once, send the idempotency key and size/dimension metadata, parse only the normalized backend response, propagate coroutine cancellation, and expose a cancellable OkHttp call. Do not automatically save any estimate.

- [ ] **Step 5: Verify GREEN and commit**

Run the focused test and then `.\gradlew.bat testDebugUnitTest` before committing.

### Task 5: Correct EXIF and make preparation configurable

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/NutritionScreen.kt`
- Modify: `app/src/test/java/com/example/ui/screens/NutritionImagePreparationTest.kt`

- [ ] **Step 1: Add failing preparation cases**

Test 90/180/270-degree orientation, aspect-ratio preservation, 1600/85, 1280/75, 960/65, single final encode, nonempty output, and rejection above the configured payload ceiling.

- [ ] **Step 2: Verify RED**

Run the focused `NutritionImagePreparationTest`; orientation/configuration cases must fail against the fixed 1024/75 implementation.

- [ ] **Step 3: Implement `FoodImageProfile` and EXIF transform**

Use `android.media.ExifInterface` on a separate input stream, decode with sampling, transform via `Matrix`, resize once, and encode once. Return original/prepared dimensions and byte sizes in `PreparedFoodImage`.

- [ ] **Step 4: Verify GREEN and commit**

Run the focused test and the full unit suite before committing.

### Task 6: Retain state and expose recovery actions

**Files:**
- Create: `app/src/main/java/com/example/ui/nutrition/GeminiFoodUiState.kt`
- Create: `app/src/test/java/com/example/ui/nutrition/GeminiFoodUiStateTest.kt`
- Modify: `app/src/main/java/com/example/ui/viewmodel/FitPlanViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/screens/NutritionScreen.kt`

- [ ] **Step 1: Write failing state-transition tests**

Test Idle -> Preparing -> AwaitingConsent -> Loading -> Review, failure retaining captured data, cancellation, retry reuse of the same prepared hash, duplicate-tap suppression, and no automatic save.

- [ ] **Step 2: Verify RED**

Run the focused UI-state test; it must fail because the state machine is absent.

- [ ] **Step 3: Move request state to the ViewModel**

Keep the captured URI string, prepared bytes/metrics, editable fields, status, request ID, and result in a `StateFlow`. Cancel on explicit cancel/navigation, retain across configuration change, and clear only after successful user-confirmed save or discard.

- [ ] **Step 4: Add Retry, Manual, Local estimate, and Cancel actions**

Local estimate uses only an explicitly typed food name and the existing local food catalog; when there is no safe match it opens manual entry instead of inventing nutrition.

- [ ] **Step 5: Verify and commit**

Run focused state tests and the full unit suite.

### Task 7: Build and document the controlled dataset

**Files:**
- Create: `test-data/gemini-food-benchmark/README.md`
- Create: `test-data/gemini-food-benchmark/manifest.json`
- Create: `scripts/download-gemini-benchmark-dataset.ps1`
- Create: `scripts/prepare-gemini-benchmark-images.ps1`

- [ ] **Step 1: Select ten open-license source images**

Use Wikimedia Commons file pages or a named open dataset. Record direct source page, author, license, expected category, and attribution for every file before download.

- [ ] **Step 2: Download only the ten selected files**

Save originals under `test-data/gemini-food-benchmark/originals/`. Do not read or copy any device image.

- [ ] **Step 3: Generate and verify hashes**

Write SHA-256, format, dimensions, and byte size into the manifest. Preparation outputs go under `prepared/conservative`, `prepared/balanced`, and `prepared/aggressive`, each with its own SHA-256.

- [ ] **Step 4: Document licenses and commit**

The README must state that images are test-only and never packaged in the app.

### Task 8: Implement the sequential benchmark harness

**Files:**
- Create: `scripts/run-gemini-benchmark.ps1`
- Create: `test-results/gemini/.gitkeep`

- [ ] **Step 1: Add dry-run validation**

Dry-run checks all source/prepared hashes, required metadata, unique case IDs, secret redaction, and that no Base64 is written to disk or console.

- [ ] **Step 2: Add sequential execution**

Run one request at a time and write JSONL/CSV rows with timestamps, model, case, hashes, dimensions, quality, weights, status, timings, attempt, structured validity, and sanitized error. Stop when required configuration is absent rather than printing credentials.

- [ ] **Step 3: Run three pilots**

Use one fixed source and fixed `gemini-3.5-flash`/prompt, changing only compression profile. Continue only if parsing works or the recorded provider error is understood.

- [ ] **Step 4: Run controlled groups**

Execute ten text, ten selected optimized images, and ten realistic-size images. Model comparisons reuse identical prepared hashes and prompt version. Do not parallelize.

- [ ] **Step 5: Compute summary**

Generate success rate, nearest-rank p50/p95, maximum, 429, 503, timeout, invalid JSON, duplicates, average/median weight, identification score, nutrition-plausibility score, and before/after comparison.

### Task 9: Final verification and Huawei QA

**Files:**
- Create: `test-results/qa/2026-07-10-huawei-mar-lx3a.md`
- Create: `test-results/qa/evidence/` files produced by ADB

- [ ] **Step 1: Run clean verification**

Run `.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug`, `npm test`, and available instrumentation tests. Record commands, exit codes, and counts.

- [ ] **Step 2: Install and launch**

Use the SDK-local `adb.exe` to install the debug APK, clear Logcat, launch the package, and record package version/device identifiers without serializing personal data.

- [ ] **Step 3: Execute required flows**

Use UI hierarchy plus coordinate commands where automatable; mark manual interactions as Manual and unavailable flows as Not verified. For each flow record steps, result, persistence, crash, ANR, filtered Logcat, and screenshot/XML evidence.

- [ ] **Step 4: Repeat the five critical flows**

Repeat onboarding, workout, manual nutrition, Gemini analysis, and close/reopen after all changes.

- [ ] **Step 5: Remove temporary artifacts and write final report**

Delete temporary prepared copies outside the documented dataset/result folders. Preserve the licensed dataset, hashes, sanitized benchmark results, build/test outputs, and QA evidence. Report only Approved, Failed, Approved with observations, or Not verified.
