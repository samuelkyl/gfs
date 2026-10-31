# GFS (Guilt-Free Spending) Design & Technical Specification

## 1. Overview & Philosophy
The **Guilt-Free Spending (GFS)** tracker is built around the *Conscious Spending Plan* (CSP).
Its core purpose is to provide an ultra-clean, distraction-free answer to one question:

> **"How much guilt-free money do I have left right now?"**

Once fixed costs, investments, and savings are automated, the remaining discretionary allowance can be spent completely guilt-free—with lightweight optional tagging for visual spending insights when desired, and zero mandatory categorization friction.

---

## 2. Core Business Rules

### 2.1. Budget Refill & Cycle Calculation
- **Monthly Refill Amount**: Configurable recurring discretionary allowance (e.g., \$500/month), rounded to cent precision using half-up decimal arithmetic (`CycleCalculationEngine.roundToCents`).
- **Refill Day of Month**: Configurable cycle anchor day (`1` – `31`).
  - *Short-Month Clamping Rule*: When a calendar month has fewer days than the anchor day (e.g., Feb 28/29 or 30-day months when anchor is `31`), the refill clamps to the last calendar day of that month.
  - *Overnight / Resume Refresh*: Resuming the app automatically refreshes the active calendar date (`MainViewModel.onAppResumed` → `BudgetRepository.refreshCurrentDate`) so `daysRemaining` and month rollovers stay accurate even if the app stays open across midnight.
- **Initial Setup**: First-time launch opens a single-question setup screen. Starting balance immediately receives the full monthly budget amount (no pro-rating).

### 2.2. Rollover & Maximum Cap
- **Surplus Rollover**: Any unspent budget (`> $0`) at the end of a cycle rolls over into the next cycle up to the configured **Maximum Rollover Cap** (default `2×` monthly budget).
- **Extra / Overflow Tracking**:
  - Any surplus exceeding `maxRolloverCap` is added to the cumulative `Total Extra Saved` counter.
  - Stored and viewable inside **Settings & Details** (with a confirmation-guarded *Reset Extra Saved* option), keeping the main dashboard uncluttered.
- **Multi-Month & Retroactive Resolution**:
  - `CycleCalculationEngine.resolveCycleRollover` steps chronologically from the earliest recorded cycle through all completed cycles up to the active cycle, handling multi-month idle gaps as well as retroactive edits, date moves, or deletions of historical expenses.

### 2.3. Overspending Guard & Deficit Carryover
- Both **Add Expense** and **Edit Expense** bottom sheets display a clear inline warning banner whenever a new or updated active-cycle expense exceeds the remaining balance.
- If a cycle ends with a negative balance (`< $0`), the full deficit carries forward into the next cycle and is deducted from the new month's refill.

### 2.4. Currency & Input Rules
- **Smart Summary Formatting**: Hero balance, progress totals, breakdown headers, and stat tiles display grouped whole dollars when exact (`$1,250`) and show 2 decimal places whenever fractional cents exist (`$493.50`).
- **Line-Item Precision**: Logged transaction rows always display exact 2-decimal amounts (`-$35.00`, `-$6.50`, `-$0.49`) with tabular numerals (`tnum`).
- **Input Bounds**: Currency inputs accept up to 7 whole digits and 2 decimal places (max `$9,999,999.99`); descriptions cap at `200` characters and tag names at `40` characters.

---

## 3. Screen Flows & User Experience

### 3.1. Main Dashboard ("At a Glance")
- **Header**: Crystal Pear app mark, *Guilt-Free Spending* title, and Settings action button.
- **Hero Display**:
  - **Continuous Color Interpolation**: Smoothly interpolates (`lerp`) across WCAG-accessible semantic tokens from **Emerald Green** (`100%` remaining) → **Warm Amber** (`50%` remaining) → **Alert Red** (`0%` remaining) in both Light and Dark themes.
  - **Cycle Pill Badge**: Pulsing status dot with pluralized countdown (`October · 14 days left` / `October · 1 day left`).
  - **Remaining Balance**: High-impact tabular display typography that scales gracefully for larger figures without multi-line clipping, merged into a single TalkBack accessibility announcement.
  - **Gradient Pacing Bar**: Smoothly animated pill progress bar with percentage and cycle budget summary.
- **Recent Activity**:
  - Borderless, read-only list lazily paginated in batches of `8` items as the user scrolls, with single-line ellipsis protection so long descriptions never clip currency amounts.
  - **View All →** navigates to **Spending History**.
- **Primary Action**: Prominent circular Floating Action Button (`+`) opening **Add Expense**.

### 3.2. Add Expense (Modal Bottom Sheet)
- Opens immediately from the `+` FAB with `FocusRequester` auto-focusing the decimal keypad.
- **Amount (\$)**: Starts blank (never pre-filled with `0`), decimal keyboard, tabular figures.
- **Description (Optional)**: Defaults to the selected tag name (or *"Guilt-Free Expense"*) when left blank; supports keyboard `Done` submission.
- **Tag (Optional)**: Horizontal row of accessible Material 3 `FilterChip`s (pre-seeded with *Eating Out*, *Shopping*, *Entertainment*, and *Coffee & Drinks*).

### 3.3. Spending History & Breakdown
- **Cycle Switcher**: Accessible `FilterChip` row switching between `Current Cycle`, deduplicated historical cycles (sorted newest-first), and `All Time`.
- **Spending Breakdown (`CategoryPieChart`)**: Animated donut chart with screen-reader summary and category legend displaying both dollar amounts and percentages.
- **Tap to Edit (`EditExpenseBottomSheet`)**:
  - Editable **Amount**, **Description**, **Date** (via Material 3 `DatePickerDialog` preserving time-of-day), and **Assigned Tag** (preserving legacy/deleted tags unless reassigned).
  - **Confirmation-Guarded Delete**: Tapping *Delete* prompts a confirmation `AlertDialog` before removing the transaction and reimbursing the cycle balance.

### 3.4. Settings & Details
- **Current Cycle Breakdown**: Stat tiles for *Monthly*, *Rollover* (`+` surplus / `-` deficit), and *Extra Saved*, plus confirmation-guarded *Reset Extra Saved*.
- **Customize Tags**: View tag count, remove any tag via accessible `InputChip`s, or create custom tags with case-insensitive duplicate prevention and accessible `Role.RadioButton` color swatches (with checkmark indicator).
- **Data Management & Backup**:
  - **Export (JSON)**: Writes `gfs_backup_YYYY-MM-DD.json` to app cache on `Dispatchers.IO` and shares a read-only `content://` URI via `FileProvider` (`EXTRA_STREAM`), avoiding Binder size limits and plaintext clipboard exposure.
  - **Import (JSON)**: Opens the system `OpenDocument` picker, reads up to `5 MB` off the Main thread, prompts an overwrite confirmation `AlertDialog`, validates schema version/bounds/formats, and atomically replaces tags + transactions inside a single SQLite transaction before restoring DataStore preferences and re-syncing form inputs.
- **Configuration**: Inline-validated inputs for *Monthly Refill Amount*, *Refill Day of Month (`1–31`)*, and *Max Rollover Cap*. Updating the refill day batch-realigns historical transaction `cycleKey`s in a single DAO update.

### 3.5. First Launch Setup (1-Question Frictionless Onboarding)
- Single prompt: *"What is your monthly guilt-free budget?"* (scroll- and IME-safe for portrait, landscape, and split-screen).
- Smart defaults: Refills on the `1st`, Max Rollover Cap set to `2×` monthly budget, starting balance initialized to `100%` of the monthly budget.

---

## 4. Technical Architecture
- **Package**: `ca.sleung.gfs`
- **UI Layer**: Jetpack Compose + Material 3, Dynamic Color with custom Emerald/Pear Light & Dark `ColorScheme` fallback, `LocalExtendedColors` WCAG-compliant semantic tokens, Dark-mode-aware platform `themes.xml`, Monochrome vector adaptive launcher icon, stateless `*Content` composables with `@PreviewLightDark`, and Predictive Back (`enableOnBackInvokedCallback="true"`).
- **Domain Layer**: Pure Kotlin (`CycleCalculationEngine`, `BudgetInfo`, `CategoryBreakdown`, `BackupModels`) with zero Compose UI dependencies.
- **Data Layer**:
  - **Room (`AppDatabase` v3)**: `TransactionEntity` (indexed on `timestamp` and `cycleKey`) and `TagEntity` (indexed on `orderIndex`), explicit `MIGRATION_1_2` & `MIGRATION_2_3`, exported Room schemas (`app/schemas/`), and `DatabaseTransactionRunner` for multi-DAO atomic writes.
  - **DataStore Preferences (`BudgetPreferencesRepository`)**: Corruption-resilient `ReplaceFileCorruptionHandler` storing budget settings and rollover state.
  - **Repository (`BudgetRepository`)**: Mutex-guarded rollover persistence, background `flowOn(defaultDispatcher)` computation, and lifecycle-driven date refresh.
