# Security Remediation Design — CN-001 to CN-016

## Scope and constraints

This design closes the sixteen confirmed Cyber Neo findings without redesigning product UI or discarding existing user data. The current dirty worktree is preserved in place on branch `codex/security-remediation-2026-07-19`; agents may only edit their assigned file sets and must not stage or commit unrelated user changes.

## Architecture

### Backend security boundary

The backend will expose a testable Express application factory separated from process startup. Security configuration is validated once, with no credential fallback. JWT authentication accepts only configured algorithms, issuer and audience, requires `sub` and `exp`, and produces an authenticated principal that authorization middleware can consume. Static API-key-as-user behavior and `default_admin_user` are removed.

Request processing is ordered as: correlation ID → proxy-aware IP derivation → structured security logger → pre-auth IP limiter → security headers/CORS → small JSON parser → routes. Image routes receive their own larger parser only after lightweight abuse controls. Per-user rate limiting runs after authentication. Client errors remain generic.

### Logging and lifecycle

A central structured logger allowlists fields, neutralizes control characters and redacts sensitive keys. Database logs never contain SQL parameters. Authentication failures, authorization denials, expired tokens and rate-limit events emit audit events without credentials or medical payloads. Startup returns a server handle; SIGTERM/SIGINT and fatal process events stop accepting traffic, close resources and exit with the appropriate status.

### Android identity and data isolation

Manual email entry is not treated as remote authentication. Local mode creates a durable opaque local subject identifier and labels the session as local. Google mode remains distinct; no backend identity claim is accepted until a verifiable ID token flow exists. Persistent health/fitness data is scoped by owner ID through a non-destructive Room migration. Legacy rows move to an explicit legacy owner rather than being silently reassigned.

Logout clears the active identity and in-memory session state, prevents navigation back into authenticated content and does not delete owned data. A later login sees only records belonging to that subject. Explicit account deletion remains the only destructive path.

Analytics uses an event/property allowlist and emits nothing sensitive in release. Debug logging uses sanitized categorical values only. Main explicitly forbids cleartext; debug network policy permits only emulator loopback hosts required for local development.

### Supply chain and signing

The Node lockfile is updated after backend dependency changes, validated with `npm ci`, and tracked. Gradle wrapper components are aligned to an officially compatible version and protected with the official distribution checksum. Dependency verification metadata and locks are generated and reviewed. Release credentials remain external; ignored local secret scripts are replaced by safe setup documentation without deleting the existing keystore.

## Testing strategy

Every behavioral change follows red-green-refactor. Node tests exercise missing config, JWT claims, malformed tokens, CORS, headers, 413/429 responses, log redaction, proxy handling and graceful shutdown. Android unit/instrumentation tests cover owner scoping, legacy migration, user A/B isolation, logout state, analytics filtering and cleartext manifests. Final gates run Node tests, Android unit tests, lint, debug/release APK, AAB, signature inspection and secret scans.

## Residual constraints

Physical Android 10 validation requires an available device or emulator. Production JWT issuance/Google token verification also requires real provider configuration; when unavailable, the app must remain in clearly labelled secure local mode and production deployment remains blocked from claiming remote account authentication.

