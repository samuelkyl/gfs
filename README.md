<p align="center">
  <img src="docs/images/app_icon.png" width="96" height="96" alt="gfs App Icon" style="border-radius: 24px;" />
</p>

<h1 align="center">gfs</h1>

<p align="center">
  <strong>Guilt-Free Spending Tracker for Android</strong><br>
  Once your fixed costs, savings, and investments are automated — this is the money you can spend completely guilt-free.
</p>

<p align="center">
  <a href="https://github.com/samuelkyl/gfs/releases/latest"><img src="https://img.shields.io/github/v/release/samuelkyl/gfs?label=download%20apk&color=006C4C" alt="Download APK" /></a>
  <a href="https://github.com/samuelkyl/gfs/actions/workflows/android-ci.yml"><img src="https://github.com/samuelkyl/gfs/actions/workflows/android-ci.yml/badge.svg" alt="Android CI" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License: MIT" /></a>
</p>

<p align="center">
  <img src="docs/images/demo.gif" width="300" alt="gfs App Demo" />
</p>

---

## ✨ Features

- **🎯 Available-to-Spend Dashboard**: Real-time guilt-free balance with color-coded pacing, daily allowance, and cycle countdown.
- **🔄 Smart Rollover & Caps**: Unspent budget carries over into the next cycle (up to a configurable cap, overflow banked as extra savings); deficits carry forward so you stay honest.
- **🍩 Spending Breakdown**: Interactive donut chart with customizable categories and color-coded tags.
- **📅 Historical Cycles**: Browse past billing cycles, review spending trends, or filter all-time activity.
- **💾 Offline & Private**: Zero network permissions, zero tracking. Cent-accurate offline accounting with JSON backup and restore.

---

## 📱 Screenshots

<p align="center">
  <img src="docs/images/dashboard_mockup.png" width="280" alt="gfs Dashboard" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="docs/images/breakdown_mockup.png" width="280" alt="gfs Breakdown Donut Chart" />
</p>

---

## 📦 Download

Download the latest signed APK from [**GitHub Releases**](https://github.com/samuelkyl/gfs/releases/latest). Requires **Android 8.0+** (API 26+).

---

## 🛠️ Tech Stack

- **UI**: Jetpack Compose & Material 3 (Edge-to-Edge, Dark Mode, Dynamic Color, Predictive Back)
- **Architecture**: Clean Architecture & MVI/UDF with Kotlin Coroutines & StateFlow
- **DI**: Dagger Hilt
- **Persistence**: Room Database (SQLite) & DataStore Preferences
- **Build**: Gradle 9, AGP 9.5, Kotlin 2.4, targetSdk 37

---

## 💻 Development

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Run lint checks
./gradlew lintDebug

# Build release APK
./gradlew assembleRelease
```

---

## 📄 License

Distributed under the [MIT License](LICENSE).
