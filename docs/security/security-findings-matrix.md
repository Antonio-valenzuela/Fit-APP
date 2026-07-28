# Security Findings Matrix

**Branch:** `codex/security-remediation-2026-07-19`  
**Updated:** 2026-07-21  
**Overall state:** Provisional; pendiente de verificación final por coordinador; no finding is finally closed by this documentation-only pass

The Node 29/29 and Android `:app:testDebugUnitTest` success recorded below are historical baseline evidence. They were not rerun after the current working-tree changes.

## Original remediation baseline

| Finding | Root cause | Primary files | Required verification | Owner | Initial state |
|---|---|---|---|---|---|
| CN-001 | Static auth/JWT fallbacks and fixed privileged identity | `middleware/auth.js`, config, auth tests | Missing config fails; hostile JWT matrix rejected | Backend | Confirmed/open |
| CN-002 | Global permissive CORS | `index.js`, HTTP tests | Allowed/native accepted; hostile origin rejected | Backend | Confirmed/open |
| CN-003 | Missing explicit security headers | `index.js`, `package*.json` | Helmet/header assertions | Backend | Confirmed/open |
| CN-004 | Untracked npm lock and floating direct ranges | `package*.json` | `npm ci`, `npm audit`, Git tracking | Supply | Confirmed/open |
| CN-005 | No Gradle verification/locking | Gradle config/metadata/locks | Strict clean resolution | Supply | Confirmed/open |
| CN-006 | DB query parameters logged | `db/index.js`, logger tests | Medical/token/log injection values absent | Backend | Confirmed/open |
| CN-007 | No authz/authn audit trail | auth/routes/logger | Structured audit events without secrets | Backend | Confirmed/open |
| CN-008 | Local plaintext signing passwords beside keystore | ignored script/docs/signing config | No tracked secret; APK secret scan; cert check | Supply/signing | Confirmed/open |
| CN-009 | 10 MB parser global and pre-auth | app/routes/middleware | 413 and route-specific limits | Backend | Confirmed/open |
| CN-010 | Manual pseudo-login and unscoped Room data | entities/DAOs/repository/viewmodel/login | A→logout→B isolation→A restore | Android | Confirmed/open |
| CN-011 | Limiter runs after auth and is not global | rate-limit middleware/routes | pre-auth IP and post-auth user 429 | Backend | Confirmed/open |
| CN-012 | Wrapper version/checksum mismatch | `gradle/wrapper/*` | Official checksums; `gradlew --version` | Supply | Confirmed/open |
| CN-013 | Missing bounded proxy trust | backend config/HTTP tests | spoofed XFF rejected; configured hop honored | Backend | Confirmed/open |
| CN-014 | Sensitive unfiltered Logcat analytics | `ProductAnalytics.kt`, call sites | Release silent; allowlist/control-char tests | Android | Confirmed/open |
| CN-015 | No graceful/fatal process shutdown | server lifecycle | SIGTERM/fatal close tests | Backend | Confirmed/open |
| CN-016 | Debug global cleartext | manifests/network config | Main deny; debug local-only | Android | Confirmed/open |

## Current post-remediation audit

IDs in this table correspond to the 2026-07-21 Cyber Neo report and are distinct from the original baseline numbering above.

| Finding | Severity | Current working-tree state | Verification required to close | Owner |
|---|---|---|---|---|
| CN-001 Remote account deletion/session revocation | High | Open | Transactional backend deletion, JWT/session revocation, Android session clear and failure/retry tests | Backend + Android |
| CN-002 Historical static credential | High | Documentation redacted; operational remediation pending | Provider rotation/revocation, old deployment audit, coordinated history cleanup and full-ref/artifact rescan | Security/operations |
| CN-003 Supply-chain files not delivered | High | Open; relevant files remain outside a reviewed delivery | Track intended files and prove `npm ci` plus strict Gradle resolution from a clean clone/CI | Supply chain |
| CN-004 Room data at rest | Medium | Residual risk/design decision pending | Document threat-model decision; if adopted, test Keystore-backed SQLCipher migration/rotation/recovery | Android/security |
| CN-005 Mobile certificate pinning | Medium | Open pending stable production endpoint | Current and backup SPKI pins, rotation/runbook and interception tests | Android/operations |
| CN-006 Gemini arbitrary request/upstream response | Medium | Open | Exact schema, server-owned generation config, bounded fields and sanitized error/success contract tests | Backend |
| CN-007 Auth audit correlation | Medium | Open | `requestId` on all auth/authz paths plus retention/alert verification | Backend/operations |
| CN-008 Local purge reliability/caches | Medium | Open | Awaitable result, honest UI state and scoped cache deletion tests | Android |
| CN-009 Sensitive ignore patterns | Low | Implemented in working tree; not finally verified | Check ignore precedence/examples and include in intended delivery | Supply chain |
| CN-010 Release Logcat throwables | Low | Open | Debug-only throwable tests and release-log inspection | Android |

## Closure rule

`Implemented in working tree` is not equivalent to `Closed`. Closure requires the intended code/configuration, a passing regression test on the exact candidate and evidence that the change is included in the reviewed delivery. Final APK/AAB hashes, certificate fingerprints and deployment checks remain intentionally absent until those artifacts exist.
