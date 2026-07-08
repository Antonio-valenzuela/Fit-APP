# Fitness Intelligence Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert FitPlan MX into a cleaner local-first fitness intelligence app without mixing older Flutter or desktop versions.

**Architecture:** Keep the Android Kotlin/Jetpack Compose app as the source of truth. Add small, testable local rules first, then connect them to Compose screens and repositories. Prepare sync/back-end interfaces later, without replacing offline-first behavior.

**Tech Stack:** Kotlin, Jetpack Compose, Room, WorkManager, Material 3, JUnit/Robolectric.

---

### Task 1: Branch, Java, Premium, Dashboard

**Files:**
- Modify: `gradlew.bat`
- Modify: `gradle.properties`
- Modify: `README.md`
- Modify: `app/src/main/java/com/example/data/model/PremiumAccessPolicy.kt`
- Create: `app/src/main/java/com/example/data/model/DashboardTodaySummaryRules.kt`
- Modify: `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`
- Modify: `app/src/test/java/com/example/data/model/PremiumAccessPolicyTest.kt`
- Create: `app/src/test/java/com/example/data/model/DashboardTodaySummaryRulesTest.kt`

- [x] Create branch `feature/fitness-intelligence-platform`.
- [x] Pin Gradle to `C:/Program Files/Eclipse Adoptium/jdk-17.0.16.8-hotspot`.
- [x] Add central premium testing flags without deleting premium architecture.
- [x] Replace the saturated dashboard feed with `Plan de hoy`, `Estado rapido`, and `Accesos rapidos`.
- [x] Verify focused red-green tests for premium and dashboard summary rules.

### Task 2: Health & Activity Monitor

**Files:**
- Create: `app/src/main/java/com/example/data/model/HealthActivityMonitorRules.kt`
- Create: `app/src/test/java/com/example/data/model/HealthActivityMonitorRulesTest.kt`
- Modify: `app/src/main/java/com/example/ui/viewmodel/FitPlanViewModel.kt`
- Modify: `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`

- [ ] Write tests for missing food, low hydration, recent training, lack of data, and overtraining risk copy.
- [ ] Implement a pure local monitor that returns user-friendly statuses only.
- [ ] Expose the monitor through the ViewModel or dashboard summary layer.
- [ ] Show only concise statuses on the dashboard.
- [ ] Run `app:testDebugUnitTest` and `app:assembleDebug`.

### Task 3: Readiness Score

**Files:**
- Create: `app/src/main/java/com/example/data/model/ReadinessScoreRules.kt`
- Create: `app/src/test/java/com/example/data/model/ReadinessScoreRulesTest.kt`
- Modify: `app/src/main/java/com/example/data/model/DashboardTodaySummaryRules.kt`
- Modify: `app/src/main/java/com/example/ui/screens/DashboardScreen.kt`

- [ ] Write tests for score bands 80-100, 60-79, 40-59, and 0-39.
- [ ] Implement local scoring from recent training, hydration, sleep, fatigue, rest days, and missing data.
- [ ] Replace the temporary `Preparacion inicial` dashboard copy with a real 0-100 score and explanation.
- [ ] Run `app:testDebugUnitTest` and `app:assembleDebug`.

### Task 4: Offline-First Sync Preparation

**Files:**
- Create: `app/src/main/java/com/example/sync/SyncStatus.kt`
- Create: `app/src/main/java/com/example/sync/SyncState.kt`
- Create: `app/src/main/java/com/example/sync/SyncRepository.kt`
- Create: `app/src/main/java/com/example/sync/LocalSyncManager.kt`
- Create: `app/src/test/java/com/example/sync/LocalSyncManagerTest.kt`
- Modify Room entities only after migration impact is reviewed.

- [ ] Write tests for pending, synced, failed, and conflict summaries.
- [ ] Add sync interfaces and local manager without a real backend.
- [ ] Prepare entity metadata strategy before changing Room schema.
- [ ] Add WorkManager wiring only if it can compile and run locally.
- [ ] Run `app:testDebugUnitTest` and `app:assembleDebug`.

### Task 5: GPS Robustness

**Files:**
- Inspect current GPS/activity tracking classes before editing.
- Add tests near existing activity tracking rules.

- [ ] Write tests for accuracy filtering, bad-point discard, auto-pause, GPS quality, and walking route support.
- [ ] Implement robust point filtering in local tracking rules.
- [ ] Keep the active activity UI focused on time, distance, pace/speed, GPS status, pause, and finish.
- [ ] Prepare GPX export model or interface if route history already supports it.
- [ ] Run `app:testDebugUnitTest` and `app:assembleDebug`.

### Task 6: Security, Privacy, Human Messages

**Files:**
- Review API, logging, error copy, permissions, and AI integration files.

- [ ] Add tests or static checks for secret-like strings where practical.
- [ ] Replace technical user-facing messages with safe human copy.
- [ ] Keep medical guidance as general wellness language, not diagnosis.
- [ ] Document remaining production risks.
- [ ] Run `app:testDebugUnitTest` and `app:assembleDebug`.
