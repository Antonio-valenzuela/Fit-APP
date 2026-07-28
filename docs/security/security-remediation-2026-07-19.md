# Security Remediation Status — 2026-07-19

**Last documentation update:** 2026-07-21  
**Branch:** `codex/security-remediation-2026-07-19`  
**Status:** Provisional; pendiente de verificación final por coordinador; not approved for production

This document records the security state observed after the first remediation cycle. It does not declare findings closed unless the fix, its tests and its delivery artifacts have all been verified. The 2026-07-21 documentation pass changed only documentation, `.gitignore` and the Cyber Neo report on the Desktop; it did not modify application/backend code, run builds, rotate credentials, rewrite Git history or deploy infrastructure.

## Actions performed in the documentation pass

- Removed the historical static backend credential and its obsolete authentication instructions from the working-tree deployment guide without reproducing the value.
- Replaced the deployment flow with Google ID-token verification, short-lived JWT Bearer authentication, MySQL TLS identity verification and a shared MySQL rate-limit store.
- Added `.gitignore` coverage for common credential bundles, package-manager auth files and screenshots while retaining deliberate example-file exceptions.
- Reconciled the remediation tracker and Desktop report with the post-remediation audit. All code, infrastructure and operational actions remain pending until independently verified.

## Current residual findings

| ID | Severity | Provisional state | Remaining work before closure |
|---|---|---|---|
| CN-001 | High | Open | Add transactional remote account deletion, revoke existing sessions/JWTs and clear the Android backend session. Verify failure and retry behavior. |
| CN-002 | High | Partially remediated in working tree | Documentation is redacted. Rotate/revoke the historical credential, audit old deployments, coordinate history cleanup and rescan all refs/artifacts. |
| CN-003 | High | Open/delivery-blocked | Locks, verification metadata and wrapper changes exist locally but are not yet integrated into a reviewed commit/CI path. Validate from a clean clone. |
| CN-004 | Medium | Accepted residual pending design | Decide whether the threat model requires SQLCipher; if adopted, design Keystore-backed key generation, migration, rotation and recovery tests. |
| CN-005 | Medium | Open pending production endpoint | Establish the stable production domain/certificate lifecycle before adding SPKI pinning with current and backup pins. |
| CN-006 | Medium | Open | Validate an exact Gemini request schema, construct generation options server-side and sanitize upstream success/error contracts. |
| CN-007 | Medium | Open | Add `requestId` to every auth/authz event and configure external retention and alerting without sensitive fields. |
| CN-008 | Medium | Open | Make account purge awaitable, report failure honestly and delete only the intended sensitive cache directories. |
| CN-009 | Low | Implemented in working tree; verification pending | Review ignore precedence, confirm examples remain trackable and include the change in the intended delivery. |
| CN-010 | Low | Open | Gate Android throwable stack traces behind debug and emit a sanitized release event only. |

## Security controls present but not finally certified

The current working tree contains strict backend configuration, pinned JWT validation, a Google identity flow, exact CORS origins, Helmet headers, bounded proxy trust, pre/post-auth rate limiting, route-specific body limits, structured redacted logging, graceful shutdown, Android owner scoping, release cleartext denial, backup exclusions and dependency integrity files. These controls were inspected during the audit, but they remain provisional until the final commands in `security-test-results.md` pass on the exact release candidate.

## Production blockers

1. Rotate/revoke the historical credential and verify that no deployed legacy backend accepts it.
2. Complete remote account deletion/session revocation and the reliable Android purge flow.
3. Integrate and verify npm/Gradle locks, checksums, wrapper and dependency metadata from a clean clone or CI worker.
4. Validate Gemini input/output boundaries and auth-event correlation.
5. Run final Node, Android, lint, artifact, signature, secret and API 29 tests on the exact candidate.
6. Exercise the backend against production-like MySQL/TLS and perform an external HTTPS/DAST check.

## Evidence policy

- Do not record secret values, tokens, database URLs, signing passwords or raw health/location payloads.
- A file existing in the working tree is not proof that it will be delivered.
- A previous passing test is historical evidence, not final evidence for a later diff.
- Final artifact hashes and signing certificate fingerprints must be recorded only after the final APK/AAB are produced and independently inspected.
- Findings move to **Closed** only when code/configuration, regression tests and delivery evidence all agree.
