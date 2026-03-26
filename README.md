# FlowSense

AI-powered Android expense tracking app built with **Kotlin**, **Jetpack Compose**, and **Material 3**.

---

## Features

### 1. Multi-Source Expense Capture
- **OCR Receipt Scanning** — Camera or gallery image → ML Kit OCR → AI parses merchant, items, and totals
- **Banking SMS Detection** — Automatically intercepts banking SMS and extracts transaction details
- **Banking App Notifications** — Notification listener reads push alerts from Chase, Wells Fargo, Venmo, PayPal, Zelle, Cash App, and 10+ other apps
- **Manual Entry** — Quick-add with amount, description, category, and notes

### 2. AI-Powered Intelligence (Tri-Mode Architecture)
The app supports three AI execution modes:

**Cloud AI**
- Powered by Claude API for highest quality analysis
- Requires internet connection and API key
- Best accuracy for categorization and financial insights

**System Local AI**
- Uses official Android system AI runtimes when available
- Detects AICore / Gemini Nano support (Pixel 8+, Android 14+)
- Detects ML Kit GenAI APIs via Google Play Services
- Only uses officially supported, documented Android AI APIs
- No reliance on undocumented OEM APIs or proprietary models

**Custom Local Model**
- Download or import compatible local AI models
- Run on-device using MediaPipe LLM Inference runtime
- Built-in model catalog with Gemma 2 2B IT and Gemma 3 1B IT
- Supports pluggable runtimes (MediaPipe, LiteRT/TFLite)
- Model download manager with URL accessibility checks, resume support, and progress reporting

**AI Features:**
- **Auto-Categorization** — Multi-pass engine assigns categories with weighted scoring, enhanced by LLM when available
- **Smart Suggestions** — Analyzes spending patterns and generates optimization insights
- **Transaction Type Detection** — Automatically determines if a parsed message is income or expense
- **AiProviderSelector** — Automatically selects the best available provider (user preference > system AI > local model > cloud fallback)
- **PromptAdapter** — Ensures the same prompts work across all providers, adapting for runtime capabilities
- **Benchmark** — Built-in benchmarking measures inference latency, token speed, and memory usage
- **Privacy** — Local AI modes display: "All analysis is performed locally on your device. No data is sent to external servers."

**Local AI Setup Wizard:**
- Device capability detection (RAM, storage, ABI, AI runtime support)
- Recommended AI mode selection
- Model download with progress tracking
- Validation and test prompt
- Performance benchmark

### 3. Reports & Analytics
- **Daily Report** — Today's spending breakdown with category split
- **Weekly Report** — 7-day trend with bar chart visualization, comparison to prior week
- **Monthly Report** — Full month summary with top expenses, category breakdown, net balance, and avg daily spend
- **Period Comparison** — Percentage change vs. previous period
- **Category Breakdown** — Visual progress bars with percentage allocation

### 4. Data Management
- **Local JSON Storage** — All data persisted to a single JSON file (no cloud dependency)
- **Automatic Backups** — Backup file created before every save
- **Export/Import** — Export full dataset as JSON; import from file
- **Thread-Safe** — Mutex-protected concurrent read/write operations

---

## Architecture

```
FlowSense/
├── app/src/main/java/com/flowsense/app/
│   ├── ai/                       # NEW: Tri-mode AI architecture
│   │   ├── provider/             # AiProvider interface, CloudClaudeAiProvider, SystemAiProvider,
│   │   │                         #   CustomLocalModelProvider, AiProviderSelector, PromptAdapter
│   │   ├── capability/           # DeviceAiCapabilityDetector (RAM, storage, AI runtime detection)
│   │   ├── runtime/              # LocalModelRuntime interface, SystemAiRuntimeAdapter,
│   │   │                         #   LiteRtRuntimeAdapter, MediaPipeLlmRuntimeAdapter
│   │   ├── modelmanager/         # LocalModelManager, ModelInstaller, ModelCompatibilityValidator,
│   │   │                         #   ModelDownloadManager, LocalAiModel, ModelCatalog
│   │   ├── benchmark/            # LocalAiBenchmarkRunner (latency, tokens/sec, memory)
│   │   └── setupwizard/          # Compose setup wizard (7 screens: Intro, Compatibility,
│   │                             #   RecommendedMode, InstallOptions, Download, Import, Ready)
│   ├── data/
│   │   ├── model/                # Data classes: Transaction, Category, Budget, Report, AiModePreference
│   │   ├── json/                 # JsonStorageManager (Gson-based file I/O)
│   │   └── repository/           # ExpenseRepository (single source of truth with StateFlow)
│   ├── service/
│   │   ├── ai/                   # Legacy: AiExpenseEngine, LocalAiService, MediaPipeLlmService, OllamaService
│   │   ├── sms/                  # SmsReceiver (BroadcastReceiver for banking SMS)
│   │   ├── notification/         # BankingNotificationListener (NotificationListenerService)
│   │   └── ocr/                  # (ML Kit integration in ScanReceiptScreen)
│   ├── ui/
│   │   ├── screens/              # DashboardScreen, AddTransaction, Reports, Scanner, Settings
│   │   ├── viewmodel/            # MainViewModel with UiState + AI provider integration
│   │   └── theme/                # Material 3 theme with custom green/dark palette
│   ├── util/                     # DateUtils, CurrencyUtils
│   ├── MainActivity.kt           # Navigation host with setup wizard route
│   └── FlowSenseApp.kt           # Application class
```

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 1.9 |
| UI | Jetpack Compose + Material 3 |
| OCR | Google ML Kit Text Recognition |
| Storage | Gson → JSON file (local) |
| Camera | CameraX + FileProvider |
| Architecture | MVVM with StateFlow |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |

---

## Permissions Required

| Permission | Purpose |
|-----------|---------|
| `CAMERA` | OCR receipt scanning |
| `READ_SMS` / `RECEIVE_SMS` | Banking SMS detection |
| `POST_NOTIFICATIONS` | Alerts for budget overruns |
| Notification Access (system) | Banking app notification reading |

---

## Setup & Build

1. **Open** in Android Studio Hedgehog (2023.1+)
2. **Sync** Gradle dependencies
3. **Run** on device/emulator (API 26+)

For notification listener:
> Settings → Apps → Special app access → Notification access → Enable FlowSense

For SMS parsing:
> Grant SMS permissions when prompted at runtime

---

## JSON Data Format

All data is stored in `flowsense_data.json`:

```json
{
  "transactions": [
    {
      "id": "uuid",
      "amount": 42.50,
      "description": "Starbucks Coffee",
      "category": "Food & Dining",
      "type": "EXPENSE",
      "source": "OCR_SCAN",
      "timestamp": 1708000000000,
      "merchantName": "Starbucks",
      "tags": ["coffee", "morning"],
      "isRecurring": false
    }
  ],
  "categories": [...],
  "budgets": [...],
  "suggestions": [...],
  "settings": {
    "currency": "$",
    "smsParsingEnabled": true,
    "notificationListenerEnabled": false,
    "autoCategorizationEnabled": true
  },
  "lastUpdated": 1708000000000
}
```

---

## Default Categories

| Category | Type |
|----------|------|
| Food & Dining | Expense |
| Groceries | Expense |
| Transportation | Expense |
| Shopping | Expense |
| Entertainment | Expense |
| Bills & Utilities | Expense |
| Healthcare | Expense |
| Education | Expense |
| Rent & Housing | Expense |
| Salary | Income |
| Freelance | Income |
| Investment | Income |
| Other | Both |

---

## Monitored Banking Apps

The notification listener watches for push notifications from:
Chase, Wells Fargo, Bank of America, Citi, American Express, Discover, Capital One, USAA, Ally, PayPal, Venmo, Cash App, Zelle, Google Pay

---

## License

MIT License
