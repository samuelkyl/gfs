# AGENTS.md — Developer & AI Agent Guidelines for GFS

## 1. Project Overview & Philosophy

**GFS (Guilt-Free Spending)** is an offline-first Android personal finance application built on the *Conscious Spending Plan* (CSP) philosophy. Once fixed costs, automated savings, and investments are funded, the remaining discretionary budget can be spent completely guilt-free.

The app's primary purpose is to provide an immediate, unambiguous answer to:
> **"How much guilt-free money do I have left right now?"**

### Core Principles
- **Zero Friction**: 1-question onboarding; optional tags; no mandatory categorization.
- **100% Offline & Private**: Zero network permissions, no telemetry, zero third-party analytics.
- **Exact Cent Accounting**: All monetary values are stored and calculated strictly as exact integer cents (`Long`). Floating-point arithmetic (`Double`) is reserved solely for presentation formatting.
- **Pure Reactive Streams**: Read streams (such as `budgetInfoFlow` and `getCycleHistoryFlow`) are 100% side-effect-free queries. State mutations and rollover persistence occur strictly through explicit suspend operations (`reconcileRollover()`).
- **Clean Architecture Decoupling**: Database entities (`TransactionEntity`, `TagEntity`) never leak into the UI layer. Presentation components only observe domain models (`Transaction`, `Tag`, `BudgetInfo`, `CycleHistory`).

---

## 2. Tech Stack & Requirements

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.4 (strict compiler options, JVM 17 bytecode) |
| **Android Gradle Plugin** | AGP 9.5 |
| **Gradle** | Gradle 9.8.2 |
| **Minimum / Target SDK** | `minSdk 26` (Android 8.0) / `compileSdk 37` / `targetSdk 37` |
| **UI Framework** | Jetpack Compose (BOM 2026.09.00) with Material 3 |
| **Architecture** | Unidirectional Data Flow (UDF) / MVI with ViewModel and StateFlow |
| **Dependency Injection** | Dagger Hilt 2.60 |
| **Local Persistence** | Room 2.8.5 (SQLite v4) + Preferences DataStore 1.3.0 |
| **Serialization** | `kotlinx.serialization` (JSON export & backup restoration) |
| **Asynchrony** | Kotlin Coroutines & Flow |

---

## 3. Build & Test Commands

Commands require **JDK 21** or the **Android Studio JBR**:

```bash
# Set environment for macOS with Android Studio installed
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"

# Run unit tests
./gradlew testDebugUnitTest --console=plain

# Run instrumented tests on active emulator
./gradlew connectedDebugAndroidTest --console=plain

# Generate code coverage report
./gradlew createDebugCoverageReport --console=plain

# Run Android Lint checks (zero warnings allowed in CI)
./gradlew lintDebug --console=plain

# Assemble Debug APK
./gradlew assembleDebug

# Assemble Release APK (requires keystore/signing.properties or env vars)
./gradlew assembleRelease --no-configuration-cache
```

---

## 4. Architecture & Project Layout

```
app/src/main/java/ca/sleung/gfs/
├── GFSApplication.kt            # @HiltAndroidApp entry point
├── MainActivity.kt              # Single activity, edge-to-edge, predictive back
├── MainViewModel.kt             # Tracks onboarding state and app resume refreshes
├── data/
│   ├── local/
│   │   ├── db/
│   │   │   ├── AppDatabase.kt   # Room database v4 (entities: TransactionEntity, TagEntity)
│   │   │   ├── dao/             # TransactionDao, TagDao
│   │   │   └── entity/          # TransactionEntity, TagEntity (with toDomain() mappers)
│   │   └── preferences/         # BudgetPreferencesRepository (DataStore storing cents)
│   └── repository/
│       └── BudgetRepository.kt  # Single source of truth coordinating DB, DataStore, and Engine
├── di/
│   ├── AppModule.kt             # Coroutine dispatchers (@IoDispatcher, @DefaultDispatcher)
│   └── DatabaseModule.kt        # Room DB, DAOs, migrations (v1->2, v2->3, v3->4)
├── domain/
│   ├── engine/
│   │   └── CycleCalculationEngine.kt  # Pure domain cycle math in exact cents (Long)
│   └── model/
│       ├── BackupModels.kt      # GfsBackupData, BackupPreferences, BackupTag, BackupTransaction
│       ├── BudgetModels.kt      # BudgetInfo (active cycle summary in cents)
│       ├── CategoryBreakdown.kt # Tag slice for breakdown chart in cents
│       ├── CycleHistory.kt      # Centralized cycle history & breakdown aggregation
│       ├── Tag.kt               # Pure domain model for category tag
│       └── Transaction.kt       # Pure domain model for expense (amountCents: Long)
└── ui/
    ├── navigation/              # NavDestination (type-safe), GFSNavGraph
    ├── screens/
    │   ├── components/          # AddExpenseBottomSheet, EditExpenseBottomSheet, CategoryPieChart
    │   ├── dashboard/           # DashboardScreen, DashboardViewModel, DashboardUiState
    │   ├── history/             # HistoryScreen, HistoryViewModel, HistoryUiState
    │   ├── onboarding/          # OnboardingScreen, OnboardingViewModel
    │   └── settings/            # SettingsScreen, SettingsViewModel, SettingsUiState
    ├── theme/                   # Color, Theme, Type (accessible WCAG contrast palettes)
    └── util/                    # CurrencyFormatter (comma/dot decimal parsing & formatting)
```

---

## 5. Core Business Rules

### 5.1. Billing Cycle & Refill Anchoring
1. **Anchor Day**: Configurable day of month (1–31, default 1st).
2. **Short-Month Clamping**: When a month has fewer days than the anchor day (e.g. Feb 28/29 or 30-day months for anchor 31), refill clamps to the month's final calendar day.
3. **Midnight Date Refresh**: `MainViewModel.onAppResumed()` triggers `BudgetRepository.refreshCurrentDate()`, keeping cycle boundaries and `daysRemaining` accurate across midnight without requiring a restart.

### 5.2. Surplus Rollover & Extra Saved
1. **Surplus Rollover**: Positive remaining balance at cycle boundary rolls over up to `maxRolloverCap` (default `2×` monthly budget).
2. **Extra Saved**: Any surplus exceeding `maxRolloverCap` is routed into cumulative `totalExtraSaved` and displayed in Settings.
3. **Deficit Carryover**: Negative balance (overspending) carries forward in full as negative rollover, directly reducing the next cycle's available allowance.
4. **Historical Gap & Retroactive Walk**: `CycleCalculationEngine.resolveCycleRolloverCents` walks from the earliest recorded cycle to the current cycle using exact integer arithmetic, reconciling multi-month idle periods and retroactive edits or deletions.

### 5.3. Currency & Cent Precision Rules
1. **Integer Cent Persistence**:
   - `TransactionEntity.amount`: stored in SQLite as `INTEGER NOT NULL` (cents).
   - `UserPreferences`: stored in DataStore as `longPreferencesKey` (cents).
   - Domain operations use `Long` cents to prevent IEEE-754 precision loss.
2. **Hide Decimals Option (Default: ON)**:
   - Configurable in Settings (`hide_decimal_places` in DataStore).
   - When enabled, formats amounts to whole dollars rounded half-up (`$500`, `$494`, `-$35`), eliminating visual clutter.
   - When disabled, displays 2 decimal places (`$493.50`, `-$35.00`).
3. **Locale-Aware Number Input**:
   - `CurrencyFormatter.sanitizeAmountInput` and `CurrencyFormatter.parseAmount` accept both `.` and `,` as decimal separators.
   - Never use `String.toDoubleOrNull()` directly on user-entered currency inputs; always use `CurrencyFormatter.parseAmount()`.

---

## 6. Security, Privacy & Integrity Standards

1. **Zero Network Traffic**: Do NOT add `android.permission.INTERNET` or third-party tracking SDKs under any circumstances.
2. **Safe Backup Handling**:
   - Backup files are capped at 5 MB during SAF reading.
   - Pre-validate schema versions and numeric boundaries (`require(monthlyRefillAmount.isFinite())`) before touching persistent storage.
   - **Never store raw multi-megabyte JSON strings in Compose `rememberSaveable`** (violates Android's ~1MB IPC binder limit and causes `TransactionTooLargeException`). Store in ViewModel state.
   - Roll back Room entities if DataStore preferences restoration fails to maintain cross-store atomicity.
3. **Keystores & Secrets**:
   - Release signing keystores and properties are git-ignored (`keystore/`).
   - Never commit signing secrets or hard-code fallback cache-encryption keys in GitHub Actions.

---

## 7. Accessibility & UI Guidelines

1. **WCAG AA Contrast**:
   - Dark theme positive action text must meet >= 4.5:1 (use `onPositiveAccent` on `positiveAccent`).
   - Use `accessibleTagTextColor(tagColor, isDark)` for colored tag pills so text is legible against both light and dark backgrounds.
2. **Typography**:
   - Numerical amounts should use tabular figures (`TABULAR_FIGURES_FEATURE = "tnum"`) to prevent layout jitter during animation.
   - Never ellipsize currency amounts on the hero section; scale font size down or use auto-sizing.
3. **Destructive Actions**:
   - Expense deletion requires user confirmation dialog.
   - Tag deletion in Settings requires confirmation dialog (`dialog_delete_tag_title`) to prevent accidental deletion.
4. **Initial Loading State**:
   - Initial UiState must never flash fake default budget amounts (e.g. `$500 · 100%`). The dashboard handles `isLoading` with a clean loading placeholder until DataStore emits.
5. **Adaptive Layouts (Foldables & Tablets)**:
   - For viewports with width >= 600dp, the dashboard switches to an adaptive dual-pane layout: the Left Pane renders the Hero Card while the Right Pane provides a scrollable view of Recent Activity. Compact devices (< 600dp) maintain a unified single-column scroll.
6. **No Infinite Animations on Idle Screen (WCAG 2.2.2)**:
   - Avoid infinite looping transitions (`rememberInfiniteTransition`) on static dashboard views to preserve battery life and adhere to accessibility guidelines (WCAG 2.2.2 Pause, Stop, Hide). The cycle indicator status dot uses a clean, static accent-colored circle.

---

## 8. Development & Pull Request Checklist

Before submitting changes, ensure all of the following commands pass cleanly:

```bash
# 1. Run all unit tests
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest

# 2. Run all instrumented & migration tests
./gradlew connectedDebugAndroidTest

# 3. Run Android lint (must have 0 errors and 0 warnings)
./gradlew lintDebug

# 4. Check git status to ensure no temporary or generated files are unignored
git status
```
