# FlowSense

AI-powered Android expense tracking app built with **Kotlin**, **Jetpack Compose**, and **Material 3**.

---

## Features

### 1. Multi-Source Expense Capture
- **OCR Receipt Scanning** — Camera or gallery image → ML Kit OCR → AI parses merchant, items, and totals
- **Banking SMS Detection** — Automatically intercepts banking SMS and extracts transaction details
- **Banking App Notifications** — Notification listener reads push alerts from Chase, Wells Fargo, Venmo, PayPal, Zelle, Cash App, and 10+ other apps
- **Manual Entry** — Quick-add with amount, description, category, and notes

### 2. AI-Powered Intelligence
- **Auto-Categorization** — Keyword-based ML engine assigns categories (Food, Transport, Shopping, Bills, etc.) with weighted scoring
- **Smart Suggestions** — Analyzes spending patterns and generates up to 10 optimization insights:
  - High-spending category alerts
  - Subscription consolidation opportunities
  - Week-over-week spending spike detection
  - Budget overrun warnings
  - Dining pattern optimization
  - Weekend vs. weekday spending analysis
  - Savings rate improvement recommendations
- **Transaction Type Detection** — Automatically determines if a parsed message is income or expense

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
├── app/src/main/java/com/smartexpense/tracker/
│   ├── data/
│   │   ├── model/          # Data classes: Transaction, Category, Budget, Report, etc.
│   │   ├── json/           # JsonStorageManager (Gson-based file I/O)
│   │   └── repository/     # ExpenseRepository (single source of truth with StateFlow)
│   ├── service/
│   │   ├── ai/             # AiExpenseEngine (categorization, suggestions, parsing)
│   │   ├── sms/            # SmsReceiver (BroadcastReceiver for banking SMS)
│   │   ├── notification/   # BankingNotificationListener (NotificationListenerService)
│   │   └── ocr/            # (ML Kit integration in ScanReceiptScreen)
│   ├── ui/
│   │   ├── screens/        # DashboardScreen, AddTransaction, Reports, Scanner, Settings
│   │   ├── viewmodel/      # MainViewModel with UiState
│   │   └── theme/          # Material 3 theme with custom green/dark palette
│   ├── util/               # DateUtils, CurrencyUtils
│   ├── MainActivity.kt     # Navigation host with animated transitions
│   └── SmartExpenseApp.kt  # Application class
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

All data is stored in `smart_expense_data.json`:

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
