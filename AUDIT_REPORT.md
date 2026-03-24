# SmartExpenseTracker — Codebase Audit Report

**Date:** 2026-03-24
**Scope:** Full codebase audit — security, bugs, edge cases, thread safety, resource management

---

## CRITICAL (8 issues)

### 1. `SimpleDateFormat` is NOT thread-safe — `DateUtils.kt:7-11`
All five `SimpleDateFormat` instances are shared `object`-level fields used concurrently from multiple coroutines. `SimpleDateFormat` is mutable and not thread-safe — concurrent calls produce corrupted output or `ArrayIndexOutOfBoundsException` crashes.

**Fix:** Replace with `java.time.format.DateTimeFormatter` (immutable, thread-safe) or wrap in `ThreadLocal`.

### 2. `SmsReceiver` exported with weak protection — `AndroidManifest.xml:59-66`
`android:exported="true"` with `BROADCAST_SMS` permission is insufficient. Malicious apps can declare that permission and inject fake SMS, creating fraudulent transactions in the user's data.

**Fix:** Use a signature-level custom permission or validate SMS origin more rigorously.

### 3. `BankingNotificationListener` exported — `AndroidManifest.xml:69-76`
The notification listener service is `exported="true"`. While guarded by `BIND_NOTIFICATION_LISTENER_SERVICE`, this is still a target for intent-spoofing attacks.

**Fix:** Validate notification source in `onNotificationPosted()` before processing.

### 4. Unsafe singleton in `SmartExpenseApp.kt:32`
`instance = this` is set without synchronization. `repository` and `subscriptionManager` are `lateinit` and can be accessed before `onCreate()` completes from background receivers (SMS, notifications).

**Fix:** Use `@Volatile` and double-checked locking, or use dependency injection (Hilt).

### 5. Unmanaged `appScope` in `SmartExpenseApp.kt:28`
`CoroutineScope(SupervisorJob() + Dispatchers.IO)` is never cancelled. Coroutines launched here leak indefinitely.

**Fix:** Cancel in `onTerminate()` or use `ProcessLifecycleOwner.get().lifecycleScope`.

### 6. `allowBackup="true"` in `AndroidManifest.xml:28`
Allows `adb backup` to extract all financial data (transactions, budgets, settings).

**Fix:** Set `android:allowBackup="false"` or define `android:fullBackupContent` to exclude sensitive data.

### 7. ML Kit recognizer resource leak — `ScanReceiptScreen.kt`
Five `TextRecognition.getClient()` instances are created without guaranteed cleanup. If an exception occurs mid-processing, recognizers are not closed.

**Fix:** Use `try-finally` with guaranteed `close()` on all recognizer instances.

### 8. Hardcoded `"AMD"` currency in fallback path — `SmsReceiver.kt:283`
When the fallback repository is used, currency is hardcoded to `"AMD"` regardless of user settings. Corrupts data for non-AMD users.

**Fix:** Read currency from fallback repository settings: `fallbackRepo.appData.value.settings.currency`.

---

## HIGH (8 issues)

### 9. Division by zero — `AiExpenseEngine.kt`
`savingsRate = (1 - recentSpend / recentIncome) * 100` crashes when `recentIncome == 0`.

**Fix:** Guard with `if (recentIncome > 0)` before division.

### 10. `Locale.setDefault()` modifies global state — `LocaleHelper.kt:58`
Affects ALL libraries in the process (Gson serialization, date formatting, third-party SDKs).

**Fix:** Remove `Locale.setDefault(locale)` call; rely solely on `createConfigurationContext()`.

### 11. `getDaysInRange()` unbounded allocation — `DateUtils.kt:86-95`
Called with a massive range (e.g., years), it allocates millions of `Long` values causing OOM.

**Fix:** Add a maximum range cap (e.g., 366 days) and validate `start <= end`.

### 12. Force-unwrap `!!` operators throughout UI screens
- `ScanReceiptScreen.kt:157` — `qrResult!!.take(200)` — NPE risk
- `ScanReceiptScreen.kt:664` — `qrText!!` — double force-unwrap
- `SmsScanScreen.kt:517,559,584,586,588` — `Date(startDate!!)` — crash if date picker cancelled
- `StoreMapScreen.kt:660` — `GeoPoint(tx.resolvedLat!!, tx.resolvedLng!!)` — assumes non-null
- `OcrSectionsScreen.kt:790` — `reportText!!`

**Fix:** Replace all `!!` with safe calls (`?.`) or `?: return`/`?: ""` defaults.

### 13. `isClientReady` flag not volatile — `SubscriptionManager.kt:62`
Multiple threads read/write from billing callbacks and UI thread without synchronization.

**Fix:** Add `@Volatile` annotation or use `AtomicBoolean`.

### 14. `HttpURLConnection` resource leaks — `OllamaService.kt:155-188`, `MediaPipeLlmService.kt:153-210`
If `readText()` or buffer operations throw, `connection.disconnect()` is never called.

**Fix:** Wrap connection in `try-finally { connection.disconnect() }`.

### 15. SMS receiver priority 999 — `AndroidManifest.xml:63`
Intercepts SMS before system and banking apps. Could interfere with SMS verification and other apps.

**Fix:** Lower priority to 0 or remove the `android:priority` attribute.

### 16. Editable item lists use array index as ID — `OcrSectionsScreen.kt:341`, `ScanReceiptScreen.kt:327`
Using `Triple(idx, ...)` with index as key. If items are reordered or deleted, state misaligns and edits apply to the wrong items.

**Fix:** Use UUID-based stable keys for list items.

---

## MEDIUM (16 issues)

### 17. Race condition on currency cache — `CurrencyConverterService.kt:48-61`
Two coroutines can both determine cache is stale and both fetch from the network simultaneously.

**Fix:** Use `Mutex` to serialize cache checks and updates.

### 18. Unbounded coroutine launches — `BankingNotificationListener.kt:172`
`CoroutineScope(Dispatchers.IO).launch { ... }` — no structured concurrency. Rapid notifications create many leaked coroutines.

**Fix:** Use a `SupervisorJob`-scoped singleton or rate-limit notification processing.

### 19. Cursor not closed on query exception — `SmsInboxScanner.kt:136`
If `contentResolver.query()` throws, early return skips cursor cleanup.

**Fix:** Move cursor operations into `cursor?.use { }` block.

### 20. O(n²) duplicate detection — `SmsInboxScanner.kt:107-122`
Linear scan through batch for every new candidate. Degrades with large SMS inboxes.

**Fix:** Use a `HashSet` keyed on `(amount, timestamp, card)` for O(1) lookups.

### 21. Fragile month-key deduplication — `SalarySchedulerWorker.kt:68-72`
Uses `notes.contains("salary_scheduler:$monthKey")`. If the user edits transaction notes, salary gets added twice.

**Fix:** Store salary tracking in a separate persistent flag, not in transaction notes.

### 22. Day-of-month clamping shifts reminders silently — `ScheduledExpenseWorker.kt:53-68`
If `expense.dayOfMonth = 31` in a 30-day month, reminder fires on day 30 without informing the user.

**Fix:** Document behavior or notify user about the date adjustment.

### 23. Pre-auth filtering too broad — `SmsReceiver.kt:108-113`
Substring matching: "pending authorisation approved" is filtered out even though it's a confirmation.

**Fix:** Use word-boundary regex or check that pre-auth keywords aren't followed by confirmation keywords.

### 24. Coordinates (0.0, 0.0) treated as invalid — `LocationProvider.kt:73`
Gulf of Guinea is a valid GPS location.

**Fix:** Use a separate `isValid` flag or sentinel value like `Double.NaN`.

### 25. `LocaleList.getDefault()[0]` can throw — `LocaleHelper.kt:49`
`IndexOutOfBoundsException` if the locale list is empty.

**Fix:** Use `LocaleList.getDefault().takeIf { !it.isEmpty }?.get(0) ?: Locale.getDefault()`.

### 26. Brittle regex-based JSON parsing — `CurrencyConverterService.kt:174-210`
Parses rate.am API response with regex. Any format change silently breaks all rate lookups.

**Fix:** Use `JSONObject`/`JSONArray` parser instead of regex.

### 27. No NaN/Infinity handling — `CurrencyUtils.kt`
`format(Double.NaN, "USD")` produces `"$NaN"`.

**Fix:** Add `if (amount.isNaN() || amount.isInfinite()) return "—"` guard.

### 28. No lat/lng range validation — `StoreMapScreen.kt:1024`
User can enter latitude 999 or longitude -500.

**Fix:** Validate against [-90, 90] and [-180, 180] before saving.

### 29. Salary day allows 31 for all months — `SettingsScreen.kt:1580`
`coerceIn(1, 31)` doesn't account for months with fewer days (Feb has 28/29).

**Fix:** Use `coerceIn(1, 28)` or handle per-month when scheduling.

### 30. `rateRefreshJob` race condition — `MainViewModel.kt`
Job cancel and reassignment isn't atomic.

**Fix:** Use `Mutex` or `compareAndSet` pattern.

### 31. `MapView` doesn't survive configuration change — `StoreMapScreen.kt:155`
Rotation during map interaction causes crash.

**Fix:** Use `rememberSaveable` or AndroidView lifecycle management.

### 32. Form state lost on rotation — `SettingsScreen.kt`
Multiple `mutableStateOf` variables reset on configuration change.

**Fix:** Use `rememberSaveable` for user-editable form fields.

---

## LOW (8 issues)

### 33. `NumberFormat` created on every call — `CurrencyUtils.kt`
Expensive object allocation on each format call. Cache with `ThreadLocal` or LRU.

### 34. `GreenIncome` identical to `GreenPrimary` — `Theme.kt:17`
Redundant color constant. Either differentiate or consolidate.

### 35. Three near-identical notification functions — `ExpenseNotificationHelper.kt`
DRY violation. Extract common builder logic.

### 36. Arbitrary 100KB threshold for Tesseract data — `ArmenianOcrService.kt:90`
Should verify file integrity with checksum, not file size.

### 37. Hardcoded savings percentages — `SubscriptionPlan.kt:59-65`
Will drift from actual pricing if prices change.

### 38. `formatWithSign(0.0)` returns `"+$0.00"` — `CurrencyUtils.kt`
Convention is `"$0.00"` without sign for zero.

### 39. Synchronous `onReset()` blocks navigation — `SmsScanScreen.kt:178`
If reset is slow, the UI hangs until it completes.

### 40. `ollamaService.errorMessage!!` race — `LocalAiService.kt:167`
Value could become null between the null-check and the `!!` access. Use `let { }`.
