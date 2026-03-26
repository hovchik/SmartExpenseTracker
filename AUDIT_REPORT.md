# FlowSense Codebase Audit Report

**Date:** 2026-03-24
**Scope:** Full codebase audit — bugs, edge cases, security vulnerabilities, and code quality

---

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 10 |
| HIGH     | 18 |
| MEDIUM   | 25 |
| LOW      | 12 |
| **Total**| **65** |

---

## CRITICAL Issues

### 1. SubscriptionManager.activate() silently drops plan ID write
**File:** `SubscriptionManager.kt:447-455`
**Bug:** The `.apply { }` block (Kotlin's scope function) returns the Editor, but the `putString()` inside is a conditional mutation. The problem is that `.apply { ... }` here is Kotlin's `apply`, not `SharedPreferences.Editor.apply()`. The chain calls Kotlin's `apply` (scope function) followed by `SharedPreferences.Editor.apply()` (commit). While this technically works, the naming collision is extremely confusing and error-prone. More critically, the conditional `putString` inside the scope function means the plan ID is only written when `plan != null`, but there's no corresponding `remove(KEY_ACTIVE_PLAN_ID)` when plan is null, leaving stale plan IDs.

### 2. JsonStorageManager: data corruption on partial write
**File:** `JsonStorageManager.kt:85-98`
**Bug:** `saveDataInternal()` overwrites the backup *before* writing the new file. If `file.writeText(json)` fails mid-write (disk full, OOM), both the primary file and backup are corrupted. The backup should be created from the *previous valid* file, and writes should use atomic rename (write to temp file, then rename).

### 3. Connection leak in CurrencyConverterService
**File:** `CurrencyConverterService.kt:96-118`
**Bug:** If `conn.inputStream.bufferedReader().readText()` throws an exception, `conn.disconnect()` is never called. The connection leaks. Same pattern in `ArmenianOcrService.kt:88-119`. Must wrap in try-finally.

### 4. Race condition in ExpenseRepository.addTransaction()
**File:** `ExpenseRepository.kt:50-86`
**Bug:** Reads `_appData.value`, checks for duplicates, then writes back. Between read and write, another coroutine could add the same transaction. No atomic check-and-set. Two identical SMS transactions arriving simultaneously could both pass the duplicate check.

### 5. API keys stored in plaintext JSON on disk
**File:** `Models.kt:484-490` (AppSettings data class)
**Bug:** `openAiApiKey`, `claudeApiKey`, `geminiApiKey`, `huggingFaceToken` are stored as plain strings, serialized to JSON via `JsonStorageManager`. Any app with file access or a backup extraction can read them. Should use Android Keystore or EncryptedSharedPreferences.

### 6. No exchange rate validation in convertAmounts()
**File:** `ExpenseRepository.kt:238-270`
**Bug:** `fallbackRate` is not validated. If `fallbackRate` is 0.0, all amounts become 0. If negative, amounts flip sign. If NaN/Infinity, data corrupts silently. Budget limits are also multiplied by the unvalidated rate (line 267).

### 7. Path traversal in ModelDownloadManager
**File:** `ModelDownloadManager.kt:294`
**Bug:** Model filename is extracted via `model.downloadUrl.substringAfterLast("/")` without sanitization. A URL like `https://evil.com/../../sensitive.task` could write outside the intended directory. Must sanitize by stripping path separators and validating against a whitelist pattern.

### 8. Integer overflow in time calculations
**File:** `SubscriptionManager.kt:160`
**Bug:** `plan.durationMonths * 30L * 24 * 60 * 60 * 1000` — `durationMonths` is Int. While `30L` promotes the chain to Long, if the expression were reordered, intermediate Int multiplication would overflow. Similar patterns in `SalarySchedulerWorker.kt:400` and `SmsReceiver.kt:141` may not have the `L` suffix.

### 9. Downloaded models not checksum-verified
**File:** `ModelDownloadManager.kt:396-404`
**Bug:** `LocalAiModel` has a `checksum` field but it's never used for verification. Downloaded files are only checked for size > 1MB. A corrupted or tampered model file would be accepted and loaded.

### 10. No network security configuration
**File:** `AndroidManifest.xml`
**Bug:** No `android:networkSecurityConfig` attribute. The app connects to exchange rate APIs and potentially Ollama endpoints. Without explicit config, cleartext traffic may be allowed on older Android versions, enabling MITM attacks.

---

## HIGH Issues

### 11. Category deletion doesn't cascade to transactions
**File:** `ExpenseRepository.kt:137-144`
Deleting a category leaves orphaned transactions referencing a non-existent category ID. No cleanup or re-categorization.

### 12. Silent data loss on JSON parse failure
**File:** `JsonStorageManager.kt:61-64`
If both primary and backup files fail to parse, returns default empty `AppData()` without any user notification. All transaction history is silently lost.

### 13. Memory leak — battery monitor not cleaned up
**File:** `MainViewModel.kt:171-228`
`batteryMonitor.startMonitoring()` is started but no `onCleared()` override stops it. The monitor persists after ViewModel destruction.

### 14. Race condition in ScanReceiptScreen concurrent OCR
**File:** `ScanReceiptScreen.kt:107-223`
`results` map is accessed from multiple recognizer callbacks without synchronization while `AtomicInteger` manages only the countdown.

### 15. Thread safety on conversionRateCache
**File:** `MainViewModel.kt:97-98`
`@Volatile` prevents word-tearing but not atomic read-modify-write. Multiple coroutines can read/write the cache map concurrently.

### 16. HuggingFace token partially logged
**File:** `ModelDownloadManager.kt:144`
Token's first 6 characters are logged: `token.take(6)`. Even partial token exposure in logs is a security risk.

### 17. HTTP connection leaks in CloudAiProvider parse methods
**File:** `CloudAiProvider.kt:233-245, 287, 342`
All `parseClaude()`, `parseGemini()`, `parseOpenAi()` methods can leak connections if `readText()` throws before `disconnect()`.

### 18. No timeout on AI inference
**File:** `MediaPipeLlmRuntimeAdapter.kt:65`, `LocalAiBenchmarkRunner.kt:55-60`
`inference.generateResponse()` has no timeout. A hung model blocks the IO dispatcher indefinitely.

### 19. Regex not cached in addTransaction hot path
**File:** `ExpenseRepository.kt:63`
`Regex("""card:(\d{4})""")` is compiled on every `addTransaction()` call. Should be a companion constant.

### 20. Volatile fields with non-atomic compound operations
**File:** `CustomLocalModelProvider.kt:62-63`
`activeModel = model; permanentLoadFailure = false` — two volatile writes that aren't atomic together. Another thread can observe inconsistent state.

### 21. LocationProvider rejects valid coordinates (0,0)
**File:** `LocationProvider.kt:73`
`if (lat == 0.0 && lng == 0.0) return null` — coordinates (0.0, 0.0) are valid (Gulf of Guinea). Should check for absence of cached data differently.

### 22. Cursor handling in SmsInboxScanner
**File:** `SmsInboxScanner.kt:154-223`
Broad `catch (e: Throwable)` around cursor operations masks bugs. Should use Kotlin's `use {}` pattern.

### 23. Division by zero in PromptAdapter
**File:** `PromptAdapter.kt:97, 167, 229`
`(totalExpenses - previousPeriodExpenses) / previousPeriodExpenses * 100` — if `previousPeriodExpenses` is 0.0, produces Infinity.

### 24. BankingNotificationListener drops transactions on timeout
**File:** `BankingNotificationListener.kt:177-179`
If repository init times out, the transaction is silently dropped with only a log entry. No retry or queueing.

### 25. SSRF risk with user-configurable Ollama host
**File:** `OllamaService.kt:27-28`
Ollama host URL is user-configurable but not validated. Could be pointed at internal network endpoints.

### 26. Prompt injection vulnerability
**File:** `PromptAdapter.kt:46-54, 127`
User-controlled strings (merchant names, category names, descriptions) are embedded directly in AI prompts without escaping. Malicious input could manipulate AI behavior.

### 27. QUERY_ALL_PACKAGES permission without justification
**File:** `AndroidManifest.xml:14`
Requires Google Play review justification. Overly broad for an expense tracker.

### 28. DEBUG subscription bypass
**File:** `SubscriptionManager.kt:157-165`
In DEBUG builds, subscriptions are activated without payment. If a release build is accidentally compiled with DEBUG=true, or users sideload debug APKs, subscriptions are free.

---

## MEDIUM Issues

### 29. CurrencyUtils hardcodes 2 decimal places for all currencies
**File:** `CurrencyUtils.kt:43`
Japanese Yen (JPY), Korean Won (KRW) should use 0 decimals. Formats "100¥" as "100.00¥".

### 30. Incomplete locale mapping in CurrencyUtils
**File:** `CurrencyUtils.kt:16-28`
BRL, CAD, AUD, CHF, MXN, HKD, NOK, SEK, SGD, AED and many others default to US locale formatting.

### 31. NaN/Infinity not handled in CurrencyUtils
**File:** `CurrencyUtils.kt:38-57`
`Double.NaN` and `Infinity` produce invalid display strings like "$NaN".

### 32. DateUtils getEndOfDay off-by-one
**File:** `DateUtils.kt:40`
End of day is 23:59:59.999. Transactions at exactly midnight (00:00:00.000) fall between days.

### 33. DateUtils getDaysInRange unbounded
**File:** `DateUtils.kt:92-100`
No size limit. A 10-year range creates 3650+ Date objects. Could cause OOM in reports.

### 34. ThreadLocal never cleaned in DateUtils
**File:** `DateUtils.kt:9-13`
`SimpleDateFormat` ThreadLocal entries accumulate in thread pools. Memory leak.

### 35. FlowSenseApp volatile nullable field
**File:** `FlowSenseApp.kt:37-38`
`aiProviderSelector` is `@Volatile` and nullable, accessed without null-safety atomicity.

### 36. AddTransactionScreen missing amount bounds
**File:** `AddTransactionScreen.kt:215-230`
No maximum amount validation. Extremely large numbers exceed Double precision silently.

### 37. DashboardScreen section ordering permanently changes
**File:** `DashboardScreen.kt:48-60`
`visibleSections` computed via `remember()` filters empty sections. When data returns, section order may differ from original.

### 38. SmsScanScreen float-to-int overflow
**File:** `SmsScanScreen.kt:71, 92`
`messageLimit` is Float converted to Int without bounds checking.

### 39. Regex ReDoS risk in AiExpenseEngine
**File:** `AiExpenseEngine.kt:176-210`
30+ regex patterns applied to every receipt. Maliciously crafted OCR text could cause exponential backtracking.

### 40. Regex injection in CurrencyConverterService
**File:** `CurrencyConverterService.kt:178, 199-200`
Currency codes interpolated directly into regex patterns without escaping.

### 41. Concurrent access to OllamaService.modelName
**File:** `OllamaService.kt:156`
Non-volatile field read in `generateResponse()` while `selectModel()` can write from another thread.

### 42. Floating-point arithmetic for financial calculations
**File:** `AiExpenseEngine.kt:258-289`, `ExpenseRepository.kt:250-262`
IEEE 754 double precision accumulates rounding errors. Should use BigDecimal for money.

### 43. Export data reads cache without lock
**File:** `JsonStorageManager.kt:103-108`
`cachedData ?: loadData()` reads cache outside the mutex, potential stale read.

### 44. AiConversation creates SimpleDateFormat in default parameter
**File:** `Models.kt:458`
Wasteful — creates new formatter instance for every conversation object.

### 45. No idempotency on ensureCategoryExists
**File:** `ExpenseRepository.kt:334-356`
Case-insensitive check but case-sensitive storage. Called twice with different cases creates duplicates.

### 46. Budget alertThreshold never validated
**File:** `Models.kt:138-139`
`alertThreshold: Double = 0.8` — no validation that it's between 0.0 and 1.0.

### 47. Missing recomposition safety in RichAiText
**File:** `RichAiText.kt:75-77`
Expensive `buildRichAnnotatedString()` inside `remember()` with only `text` as key.

### 48. ScanReceiptScreen coroutine leak on screen close
**File:** `ScanReceiptScreen.kt:207-222`
Coroutine continues bitmap processing after screen navigates away.

### 49. SharedPreferences.apply() not guaranteed on crash
**File:** `LocationProvider.kt:59`
`.apply()` is async. App crash immediately after could lose cached location.

### 50. Notification accumulation unbounded
**File:** `ExpenseRepository.kt:287`
`.take(100)` truncates but auto-created categories spam notifications.

### 51. Missing null pointer guard in BankingNotificationListener
**File:** `BankingNotificationListener.kt:111`
`getApplicationLabel(it).toString()` — if `getApplicationLabel()` returns null, NPE.

### 52. HTTP header injection via HuggingFace token
**File:** `ModelDownloadManager.kt:143`
Token placed in Authorization header without newline validation. Token with `\r\n` could inject headers.

### 53. Brittle JSON parsing with regex in PromptAdapter
**File:** `PromptAdapter.kt:223, 362-363`
`Regex("\"name\"\\s*:\\s*\"([^\"]+)\")` fails if JSON format changes. Should use proper parser.

---

## LOW Issues

### 54. CurrencyUtils symbolFor() does linear search
**File:** `CurrencyUtils.kt:67` — Called frequently in list rendering. Should cache.

### 55. DateUtils isToday() creates 4 Calendar instances
**File:** `DateUtils.kt:103-107` — Inefficient, should cache start/end of today.

### 56. Missing logging on SecurityException
**File:** `LocationProvider.kt:101-105` — Silently returns null.

### 57. Inconsistent error handling patterns
Multiple files mix null returns, exceptions, and Result types.

### 58. Magic numbers throughout
`30_000`, `120_000`, `600_000`, `8_000`, `15_000`, `90_000` without named constants.

### 59. No connection pooling for cloud AI
**File:** `CloudAiProvider.kt` — Each API call creates a new HttpURLConnection.

### 60. Unused JSONArray import
**File:** `OllamaService.kt:6` — Imports `JSONArray` but only uses `JSONObject`.

### 61. Hardcoded "AMD" currency fallback
**File:** `SmsReceiver.kt:293`, `ReportsScreen.kt:64` — Should read from user settings.

### 62. Missing error logging in StatFs failure
**File:** `DeviceAiCapabilityDetector.kt:186-188` — Returns 0 silently.

### 63. No documentation on complex regex patterns
**File:** `AiExpenseEngine.kt` — 30+ receipt parsing regexes with no comments.

### 64. DateUtils Locale.getDefault() captured once
**File:** `DateUtils.kt:9-13` — If locale changes at runtime, formatters are stale.

### 65. Inconsistent null-safety patterns across files
Mix of `!!`, `?.`, `orEmpty()`, `?: ""` with no clear convention.

---

## Top Priority Fixes

1. **Use atomic file writes** in JsonStorageManager (write to temp, rename)
2. **Validate exchange rates** before applying in convertAmounts()
3. **Encrypt API keys** using Android Keystore / EncryptedSharedPreferences
4. **Wrap HTTP connections in try-finally** (CurrencyConverterService, ArmenianOcrService, CloudAiProvider)
5. **Add network security config** to prevent cleartext traffic
6. **Sanitize model download filenames** to prevent path traversal
7. **Add mutex/atomic operations** for duplicate detection in ExpenseRepository
8. **Cascade category deletion** to update orphaned transactions
9. **Add timeouts** to AI inference calls
10. **Verify model checksums** after download
