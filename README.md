# GATE FOCUS — Updated Android Studio source project

Native Kotlin Android starter project for GATE Computer Science preparation.

## Features in this project
- Strict study-mode toggle.
- Accessibility-based blocking for selected distracting apps: Instagram, Threads, Facebook and Snapchat.
- YouTube is not blocked by default. Add individual GATE lecture video IDs to the in-app library; the app embeds those videos instead of opening the YouTube home feed.
- Focus timer presets (25/45/60 minutes), stopwatch, and curated video list.
- Telegram allowlist: enter approved GATE channel/chat display names, one per line.
- Telegram notification listener: during strict study mode, Telegram notifications whose visible title does not match an allowlisted name are dismissed.
- Telegram accessibility guard: attempts to leave a Telegram channel/chat screen when its visible header is not allowlisted.

## Build in Android Studio
1. Extract this ZIP.
2. In Android Studio choose **Open** and select the `GateFocus` folder (the folder containing `settings.gradle.kts`).
3. Install Android SDK Platform 35 if Android Studio asks.
4. Use JDK 17 for Gradle (Android Gradle Plugin 8.7.3 requires JDK 17).
5. Wait for Gradle sync.
6. Choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
7. The debug APK should be at `app/build/outputs/apk/debug/app-debug.apk`.

If Android Studio asks for a Gradle version, use Gradle 8.9 with Android Gradle Plugin 8.7.3. This source archive intentionally does not include a machine-specific Gradle wrapper JAR; Android Studio can create/use a wrapper or a locally installed Gradle distribution.

## First-run setup
1. Install and open GATE FOCUS.
2. Add exact visible names of your approved Telegram channels/chats in the Telegram allowlist.
3. Tap **Enable Telegram notification access** and allow GATE FOCUS in Android's Notification access settings.
4. Open Android Accessibility settings and enable GATE FOCUS.
5. Start strict study mode.

An empty Telegram allowlist means Telegram notifications are dismissed while filtering is enabled and strict study mode is active. Turn off the Telegram filter or add allowed names before starting study mode if you want to keep Telegram notifications.

## Important limitations and testing
- Telegram filtering is best-effort. Android exposes notification text, not a guaranteed stable Telegram channel ID. Notification formats, group notifications, privacy settings, localized app labels, and Telegram forks may affect title matching.
- The Telegram screen guard reads visible accessibility text and is not a secure lock. It may miss some channel screens or misidentify a screen after Telegram changes its UI. Test it on your exact Telegram version.
- Users can bypass app blocking by disabling permissions, force-stopping or uninstalling the app, using another device, or using a different Telegram client.
- YouTube videos are manually curated by video ID. A normal third-party app cannot reliably classify all videos or Shorts inside the official YouTube app. The embedded player may be affected by YouTube embed restrictions, ads, network access, and policy requirements.
- Accessibility and Notification access are sensitive permissions and must be granted manually. Review Google Play policies before distributing this app.
- This is a starter project, not a production-hardened or tamper-proof app. Test on your phone before relying on it.
