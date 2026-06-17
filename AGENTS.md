# AGENTS.md

## Project orientation
- Android app module: `app` (single-module project, root name is `FlowSense` in `settings.gradle.kts`).
- Source-of-truth SDK/config is `app/build.gradle.kts` (`minSdk = 31`, `compileSdk/targetSdk = 36`), even if docs differ.
- App entrypoints: `app/src/main/java/com/flowsense/app/FlowSenseApp.kt` and `app/src/main/java/com/flowsense/app/MainActivity.kt`.

## Architecture and data flow
- Core pattern is MVVM + StateFlow over a JSON persistence layer.
- `MainViewModel` is the orchestration hub: UI events -> repository writes -> `StateFlow<AppData>` -> recomputed `UiState`.
- `ExpenseRepository` is the single write gateway; keep business rules there (dedupe, soft-delete, recurring detection, batch ops).
- Persistence is `JsonStorageManager` writing `filesDir/flowsense_data.json` with atomic temp-file swap + backup (`flowsense_data_backup.json`).
- Schema root is `AppData` in `data/model/Models.kt`; when adding fields, default values and Gson-backward compatibility matter.
- Background ingestion paths (`SmsReceiver`, `BankingNotificationListener`) write through the same repository and must call `awaitInitialization()` before reads/writes.

## Financial analytics engine
- Deterministic, framework-free analysis lives in `analytics/FinancialAnalyticsEngine.kt` (no Android/network deps; JVM unit-tested in `app/src/test`).
- One `analyze(...)` pass returns a `FinancialAnalysis`: weighted health score, EWMA+pace cash-flow forecast, robust median/MAD anomaly detection, interval-based recurring/subscription detection, and per-category trends.
- `Statistics.kt` holds robust helpers (median, MAD, modified z-score, OLS slope, EWMA); prefer these over naive mean/stddev so outliers don't skew results.
- `AiExpenseEngine.generateSuggestions(...)` and `MainViewModel.analyzeFinances()` delegate here; the optional LLM layer only phrases findings, it does not compute them. Inject the clock (`now`) when testing time-relative logic.

## AI subsystem (tri-mode)
- Mode selection is centralized in `ai/provider/AiProviderSelector.kt` (`AUTO`, `SYSTEM_AI`, `LOCAL_MODEL`, `CLOUD_AI`).
- Explicit user mode does not silently fallback; fallback behavior is mainly in `AUTO`.
- Prompt/parse contract lives in `ai/provider/PromptAdapter.kt`; keep response formats compatible with `parseCategorization`, `parseInsight`, `parseOcrResponse`.
- App-level bridge for receivers: `FlowSenseApp.aiProviderSelector` and `FlowSenseApp.aiCategorize(...)`.
- `MainViewModel.initAiProviderSelector()` wires settings -> provider configuration; preserve this path when changing AI settings fields.

## Transaction pipeline conventions
- New transaction paths should use `MainViewModel.addTransaction(...)` or repository methods, not direct JSON writes.
- Dedupe windows are source-specific in `ExpenseRepository.addTransaction` (manual/ocr/voice 30s, sms/notification 2m or card-last4 10m).
- Deletes are soft by default (`deletedAt`); trash purge is retention-based (`AppSettings.trashRetentionDays`).
- Categories can be auto-created via `repository.ensureCategoryExists(...)`; many flows depend on this behavior.
- Currency conversion preserves original-amount metadata (`originalAmount`, `originalCurrencyCode`, `exchangeRate`) for reversible conversions.

## Integrations and boundaries
- Android components declared in `AndroidManifest.xml`: SMS receiver, notification listener service, FileProvider, widget receiver.
- Scheduler integration: `SalarySchedulerWorker` and `ScheduledExpenseWorker` via WorkManager unique periodic work.
- Billing integration is local-state + Play Billing in `service/subscription/SubscriptionManager.kt`; debug builds can activate plans when billing is unavailable.
- OCR stack combines ML Kit + Tesseract (`ArmenianOcrService`) and optional AI parsing (`MainViewModel.processOcrText`).
- Currency rates come from `CurrencyConverterService` (open.er-api/exchangerate-api, optional `rate.am` parsing).

## Developer workflows (local)
- Build debug APK:
  - `./gradlew :app:assembleDebug`
- Run JVM tests (currently no `app/src/test` files, but task exists):
  - `./gradlew :app:testDebugUnitTest`
- Lint the app module:
  - `./gradlew :app:lintDebug`
- Clean + rebuild when model/native deps change:
  - `./gradlew clean :app:assembleDebug`

## Practical edit guidance for agents
- Prefer minimal, focused edits in `MainViewModel` + `ExpenseRepository` + `Models.kt`; these three files define most behavior.
- If changing settings fields in `AppSettings`, also verify usage in: `SettingsScreen`, `MainViewModel.initAiProviderSelector`, and background services.
- For notification/SMS behavior changes, validate both keyword filtering and dedupe notes (`card:####` marker) to avoid duplicate inserts.
- Keep JSON compatibility: nullable/backward-compatible fields are intentionally used in model classes to tolerate older persisted data.

