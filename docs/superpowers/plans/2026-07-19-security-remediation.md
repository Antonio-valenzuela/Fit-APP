# Security Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development and superpowers:test-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close Cyber Neo findings CN-001 through CN-016 with automated evidence while preserving existing functionality and user data.

**Architecture:** A testable backend security boundary, owner-scoped Android persistence with a non-destructive migration, and reproducible dependency/signing configuration. File ownership is partitioned so implementation agents do not overlap.

**Tech Stack:** Node.js, Express, JWT, node:test, Kotlin, Jetpack Compose, Room, Hilt, Gradle, PowerShell.

---

### Task 1: Backend authentication, HTTP hardening, privacy logging and lifecycle

**Owner:** backend implementation agent.  
**Findings:** CN-001, CN-002, CN-003, CN-006, CN-007, CN-009, CN-011, CN-013, CN-015.  
**Files:**

- Modify: `index.js`, `middleware/auth.js`, `db/index.js`, `routes/gemini.js`, `routes/health.js`, `routes/muscles.js`, `package.json`, `package-lock.json`
- Create: `config/security.js`, `middleware/requestContext.js`, `middleware/rateLimits.js`, `services/securityLogger.js`, `test/securityConfig.test.js`, `test/authSecurity.test.js`, `test/httpSecurity.test.js`, `test/logRedaction.test.js`, `test/lifecycle.test.js`

- [ ] Write failing tests proving startup/config rejects missing or placeholder secrets and JWT rejects bad algorithm, issuer, audience, expiry and subject.
- [ ] Run targeted tests and record the expected failures caused by the current fallbacks.
- [ ] Implement strict config loading and JWT principal validation; remove static API-key identity and `default_admin_user`.
- [ ] Write failing HTTP tests for CORS allowlist/native requests, Helmet headers, small global body limits, route-specific image limits, pre-auth IP limits, per-user limits and proxy hop behavior.
- [ ] Run targeted tests and confirm failures reflect existing middleware ordering/configuration.
- [ ] Refactor the Express entrypoint into a testable app/server boundary, add Helmet, explicit CORS, correlation IDs, trusted proxy configuration and ordered parsers/limiters.
- [ ] Write failing logging tests that inject tokens, email, medical values, GPS and CR/LF and assert none appear in captured output.
- [ ] Replace raw database/request logs with structured allowlisted audit events and centralized redaction.
- [ ] Write failing lifecycle tests for SIGTERM/fatal error shutdown and resource closure, then implement graceful/fatal shutdown behavior.
- [ ] Run `npm test`; require all tests to pass with no secret values printed.

### Task 2: Android secure local identity, Room isolation, logout, analytics and network policy

**Owner:** Android implementation agent.  
**Findings:** CN-010, CN-014, CN-016.  
**Files:**

- Modify: `app/src/main/java/com/example/data/model/Entities.kt`, `app/src/main/java/com/example/data/local/Daos.kt`, `app/src/main/java/com/example/data/local/AppDatabase.kt`, `app/src/main/java/com/example/data/local/PreferencesManager.kt`, `app/src/main/java/com/example/data/repository/FitPlanRepository.kt`, `app/src/main/java/com/example/ui/viewmodel/FitPlanViewModel.kt`, `app/src/main/java/com/example/ui/screens/LoginScreen.kt`, `app/src/main/java/com/example/data/auth/GoogleAuthManager.kt`, `app/src/main/java/com/example/data/analytics/ProductAnalytics.kt`, `app/src/main/AndroidManifest.xml`, `app/src/debug/AndroidManifest.xml`
- Create/modify Room schemas under `app/schemas/`
- Create: `app/src/debug/res/xml/network_security_config.xml`, Android unit and migration tests under `app/src/test/java/com/example/security/` and `app/src/androidTest/java/com/example/`

- [ ] Inventory every persisted entity/DAO read and write that contains health, nutrition, workout, GPS, AI history or profile data.
- [ ] Write failing tests for stable opaque local subject IDs, explicit auth mode and logout clearing the active session.
- [ ] Write failing DAO/repository tests: user A writes data, user B sees no A rows, A can later recover A rows.
- [ ] Implement owner IDs and owner-filtered queries with a non-destructive Room migration assigning unattributed rows to a controlled legacy owner.
- [ ] Update repository/viewmodel flows so active-owner changes cancel/rebind observation and Back cannot reveal authenticated screens after logout.
- [ ] Separate secure local mode from Google mode; do not claim server authentication without a verified token.
- [ ] Write failing analytics tests for disallowed fields/control characters and release suppression, then implement event/property allowlists and sanitized debug-only logging.
- [ ] Write manifest tests for cleartext disabled in main and debug limited to `localhost`/`10.0.2.2`; implement scoped network security config.
- [ ] Run `gradlew :app:testDebugUnitTest` and the relevant migration/instrumentation tests when a device is available.

### Task 3: Gradle/npm supply chain and release signing

**Owner:** supply-chain implementation agent.  
**Findings:** CN-004, CN-005, CN-008, CN-012.  
**Files:**

- Modify: `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, `.env.example`
- Generate: `gradle/verification-metadata.xml`, Gradle dependency lock files
- Create: `release-secrets.example.ps1`, `docs/security/secrets-setup.md`, `docs/security/release-signing.md`

- [ ] Verify the configured Gradle/AGP/Kotlin/Java compatibility and official wrapper/distribution checksums before changing wrapper files.
- [ ] Regenerate the wrapper consistently and add the verified `distributionSha256Sum`; prove `gradlew --version` succeeds.
- [ ] Add dependency verification and locking, generate metadata/locks, and prove a normal build resolves with strict verification.
- [ ] Confirm repository centralization and preserve `FAIL_ON_PROJECT_REPOS`.
- [ ] Inspect signing inputs without printing secrets; preserve the keystore, document moving credentials outside the project and create only placeholder examples.
- [ ] Apply restrictive Windows ACL guidance without changing the signing identity.
- [ ] After Task 1 dependency edits settle, validate `package-lock.json` against `package.json`, run `npm ci`, `npm audit`, and ensure the lockfile is trackable.
- [ ] Run `gradlew --version`, unit tests, lint, debug/release APK and AAB tasks.

### Task 4: Integration, review and evidence

**Owner:** coordinator + QA/reviewer agents.  
**Files:**

- Create/update: `docs/security/security-remediation-2026-07-19.md`, `docs/security/security-findings-matrix.md`, `docs/security/backend-security.md`, `docs/security/android-data-isolation.md`, `docs/security/security-test-results.md`

- [ ] Review Task 1–3 against every acceptance criterion and inspect the exact diff against the recorded dirty-tree baseline.
- [ ] Run full Node and Android unit suites, lint, assembleDebug, assembleRelease and bundleRelease.
- [ ] Run available instrumented tests on Android 10/API 29; if no device exists, mark the physical-device criteria unverified and production-blocking where appropriate.
- [ ] Scan source, generated artifacts, APK/AAB, mappings, reports and logs for secret candidates without printing values.
- [ ] Inspect signing certificates and prove release never uses the debug certificate.
- [ ] Re-run Cyber Neo read-only and map CN-001..CN-016 to closed or residual with concrete test/artifact evidence.
- [ ] Dispatch specification compliance review, then code-quality/security review; resolve all blocking feedback and rerun affected tests.

