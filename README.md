# SkipVox Android MVP — Voice-Activated Ad-Skipping App

Welcome to the Android MVP for **SkipVox**! This repository contains a fully-featured, continuous voice-controlled ad-skipping application built with Kotlin, modern Android Accessibility Services, Android SpeechRecognizer, and a Material 3 Jetpack Compose UI.

---

## 🚀 Key Features

1. **Accessibility Service Integration (`SkipAdAccessibilityService`)**:
   - Background service that monitors system UI events.
   - Dynamically scans active window content hierarchies for streaming platforms (e.g., YouTube, Hulu, etc.).
   - Matches and detects elements containing "Skip", "Skip Ad", "Ad", or specific button IDs.
   - Intelligently performs clicks on target nodes, recursively walking up the layout hierarchy to find clickable parent containers if necessary.

2. **Continuous Voice Skip Controller (`VoiceSkipController`)**:
   - Integrates `android.speech.SpeechRecognizer` for low-latency hands-free detection.
   - Implements continuous listening by automatically recovering from speech timeouts, client connection issues, or system errors using handler-based scheduled restarts.
   - Captures real-time results and partial speech data, matching trigger terms like "skip" or "skip ad".

3. **Modern UI Dashboard (`MainActivity`)**:
   - Dark theme designed with Material 3 and Jetpack Compose.
   - Master Toggle to activate/deactivate the skipper.
   - Integrated check cards for **Accessibility Service Permissions** and **Microphone Audio Permissions**, with deep links directly into System Settings to request access.
   - Real-time terminal-like live status screen showing speech recognition events and action logs (e.g., `"Listening for 'Skip' command..."`, `"Heard 'Skip' - Clicked Button!"`).

4. **Freemium with Google Play Billing**:
   - Standard users start on a Free Tier limited to **5 daily skips** (persisted in secure `SharedPreferences` across app restarts and resets).
   - **Google Play Billing Library v6.2.0** integrates real subscription purchasing.
   - Two subscription tiers: **Monthly ($2.99/mo)** and **Yearly ($19.99/yr)**.
   - Purchases are verified, acknowledged, and automatically restored on app relaunch.
   - Subscription management opens Google Play's native subscription settings.

---

## 🛠 Project Architecture

```
/home/team/shared/android/
  ├── build.gradle.kts                          # Root project build script
  ├── settings.gradle.kts                       # Root project multi-module definitions
  ├── gradle.properties                         # Global JVM and compiler properties
  ├── gradle/
  │   └── libs.versions.toml                     # Centralized Gradle Version Catalog (includes billing)
  └── app/
      ├── build.gradle.kts                      # Module-specific configuration (Compose, Target SDK 34, Min SDK 26)
      ├── proguard-rules.pro                    # Obfuscation rules
      └── src/main/
          ├── AndroidManifest.xml               # Target permissions, Activity, and Accessibility Service registration
          ├── res/
          │   ├── xml/
          │   │   └── accessibility_service_config.xml # Accessibility metadata (event triggers & flags)
          │   └── values/
          │       └── strings.xml               # App, service label, and security descriptions
          └── kotlin/com/skipvox/app/
              ├── MainActivity.kt               # Main Jetpack Compose UI & billing integration
              ├── SkipVoxState.kt               # Centralized state machine & SharedPreferences manager
              ├── VoiceSkipController.kt        # SpeechRecognizer engine (continuous listener)
              ├── SkipAdAccessibilityService.kt # Accessibility core (hierarchy scanning & automated clicking)
              └── BillingManager.kt             # Google Play Billing integration (subscriptions)
```

### Flow of Execution:
1. User opens **SkipVox** and turns on the master switch.
2. The user grants **Accessibility** and **Microphone** permissions.
3. The `SkipAdAccessibilityService` is started by the Android OS. It starts `VoiceSkipController` on a main-thread loop.
4. While the user is watching a video (e.g. on YouTube), the app runs in the background.
5. The user says **"Skip"** or **"Skip Ad"**.
6. `VoiceSkipController` detects the command and triggers a callback in `SkipAdAccessibilityService`.
7. The service grabs `rootInActiveWindow` and recursively scans the screen for standard skip text or YouTube-specific selectors.
8. If found:
   - If the user is on the Free Tier, the app checks if they have remaining skips today, consumes one, and clicks the button.
   - If the user is Premium (verified via Google Play Billing), the app clicks the button instantly without limit.
   - The UI status updates in real-time.

---

## 💻 Technical Details

- **Target SDK**: 34 (Android 14)
- **Min SDK**: 26 (Android 8.0 Oreo - matches modern background and SpeechRecognizer standards)
- **UI Framework**: Jetpack Compose (Material 3 theme)
- **Programming Language**: Kotlin 1.9.23
- **Dependency Management**: Gradle Version Catalog (`libs.versions.toml`) with Kotlin DSL (`.gradle.kts`)
- **Billing**: Google Play Billing Library 6.2.0 with subscriptions support

---

## 📲 How to Build & Test

### Prerequisites
- Android Studio (Iguana, Jellyfish, or newer recommended)
- Java SDK 17+ installed on your computer
- Google Play Console account with products configured (see below)

### Google Play Console Setup (Required for Billing)
Before the subscription dialog works with real purchases, you must configure products in Google Play Console:

1. Create a Google Play Console account and set up your app.
2. Under **Monetize > Products > Subscriptions**, create:
   - **Product ID**: `skipvox_premium_monthly` — $2.99/month
   - **Product ID**: `skipvox_premium_yearly` — $19.99/year
3. Publish the app to an Internal / Closed test track.
4. Add your test accounts (license testers) to the test track.
5. Build a signed APK/AAB and upload to the Play Console.
6. The billing flow will then work with real payment processing.

### Testing Without Google Play
For development/emulator testing (without real billing), the billing library will gracefully degrade:
- The billing connection will fail on an emulator without Google Play.
- The dialog shows "Connecting to Google Play..." and buttons remain disabled.
- The Accessibility Service, Voice Recognition, and Free Tier continue to work fully.

### Installation & Run Steps
1. Copy or transfer the `/home/team/shared/android/` directory to your local development machine.
2. Open Android Studio and select **File -> Open**. Navigate to and select the `android` folder.
3. Allow Gradle to sync and download all dependencies defined in `libs.versions.toml`.
4. Connect an Android phone or launch an Emulator (ensure it has Google Play Services installed for SpeechRecognizer).
5. Press **Run -> Run 'app'** (`Shift + F10`).

### On-Device Testing Process
1. On launch, tap **Grant Permission** for both Accessibility and Microphone.
2. Toggle the **Ad-Skipper Master Switch** to **ON**.
3. Open the YouTube app and find an ad-supported video.
4. When the "Skip Ad" button appears, say **"Skip ad"** clearly.
5. Watch the button get clicked hands-free! You can switch back to SkipVox at any time to inspect the console logs on the main dashboard status screen.
6. Trigger 5 skips on the free plan to test the daily limit, then tap **Upgrade to Premium** to purchase via Google Play Billing.

---

## 🔄 Subscription SKUs

| SKU ID | Type | Price | Billing Period |
|--------|------|-------|---------------|
| `skipvox_premium_monthly` | Subscription | $2.99 | Monthly |
| `skipvox_premium_yearly` | Subscription | $19.99 | Yearly |

Purchases are acknowledged automatically upon completion and restored each time the app launches or resumes.

---

## 📝 License
Proprietary — SkipVox Inc.