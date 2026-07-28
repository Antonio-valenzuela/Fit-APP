# Security Test Results

**Updated:** 2026-07-21  
**Status:** Provisional evidence ledger; pendiente de verificación final por coordinador  
**Release approval:** Not granted

## This documentation-only pass

No application tests, builds, services, database migrations, network probes or deployment commands were executed in this pass. No final artifact hashes or signing certificate fingerprints were produced.

The checks for this pass are limited to:

- verifying that the expected Cyber Neo report exists on the Desktop;
- reviewing the working-tree documentation and `.gitignore` changes;
- confirming that the deployment guide no longer documents the static shared-key flow;
- recording pending verification without upgrading any finding to finally closed.

## Historical evidence already recorded

The following results were present in the existing remediation matrix or Cyber Neo report before this documentation update. They were **not rerun in this pass** and must not be treated as final release evidence.

| Check | Previously recorded result | Current interpretation |
|---|---|---|
| Node test suite | 29/29 passing | Historical baseline only; rerun against the final diff. |
| Android `:app:testDebugUnitTest` | Successful | Historical baseline only; rerun against the final diff. |
| npm advisory audit | 0 advisories | Snapshot result only; rerun after lockfile integration. |
| npm signature audit | 0 missing/invalid signatures | Snapshot result only; rerun from a clean install. |
| Gradle dependency locking | 542 resolutions recorded | Files are not yet proven delivered; verify strict resolution from a clean clone. |
| Gradle verification metadata | 1,330 SHA-256 entries recorded | Do not call final until clean resolution/build succeeds with committed metadata. |
| Cyber Neo source coverage | 350 first-party files; secret scanner recorded 322 | Audit-snapshot evidence, not a final artifact scan. |
| Existing API 29 XML results under `test-results/instrumented/` | Results dated 2026-07-15 | They predate the current remediation and do not satisfy the final device gate. |

## Required final verification

Run these only when the application changes are complete and the exact candidate is ready:

| Area | Required command or evidence | Status |
|---|---|---|
| Backend tests | `npm test` | Pending |
| npm reproducibility | Clean `npm ci`, then `npm audit` and signature audit | Pending |
| Android unit tests | `.\gradlew.bat :app:testDebugUnitTest` | Pending |
| Android static quality | `.\gradlew.bat :app:lint` | Pending |
| Debug artifact | `.\gradlew.bat :app:assembleDebug` | Pending |
| Release artifacts | `.\gradlew.bat :app:assembleRelease :app:bundleRelease` | Pending |
| API 29 behavior | Relevant connected/instrumented suite on Android 10/API 29 | Pending |
| Room/account isolation | A writes → logout → B cannot read A → A can recover A; deletion failure/retry paths | Pending |
| Backend integration | Production-like MySQL TLS identity verification, shared rate limiter and migration checks | Pending |
| Authentication | Valid Google token succeeds; malformed/expired/wrong issuer/audience tokens fail; JWT revocation after deletion | Pending |
| HTTP boundary | HTTPS/HSTS, CORS, hostile proxy headers, 401/403/413/429 and sanitized upstream failures | Pending |
| Logging | Correlation IDs present; secrets, bodies, health/GPS values and release stack traces absent | Pending |
| Secret scan | Working tree, full Git refs/history and APK/AAB/mapping outputs | Pending |
| Signing | Release certificate is expected production identity and not the debug certificate | Pending |
| External assessment | DAST against the deployed HTTPS endpoint | Pending |

## Final evidence to capture later

- Exact commit identifier and clean working-tree state.
- Tool/runtime versions and timestamps.
- Exit code and concise result for each required command.
- Final APK/AAB SHA-256 values.
- Signing certificate fingerprint and issuer, recorded without private-key material.
- MySQL/TLS environment identity and migration version without credentials.
- Secret-scan scope, exclusions and result without reproducing candidates.
- Links to CI logs or immutable artifacts where available.

Until this ledger is completed on the exact candidate, all remediation states remain provisional.
