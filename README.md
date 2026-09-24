# Yugram

A Telegram client for Android built with Kotlin and Jetpack Compose, powered by the official [TDLib](https://github.com/tdlib/td) (Telegram Database Library) 1.8.56. It targets **Android 8.0 (API 26)** through **Android 16 (API 36)**.

<p align="center">
  <strong>Kotlin</strong> &middot; <strong>Jetpack Compose</strong> &middot; <strong>Material 3</strong> &middot; <strong>TDLib</strong> &middot; <strong>Coroutines</strong>
</p>

---

## Features

- **Phone login** — country-code picker, SMS verification code, TDLib `authState` driven flow.
- **Chat list** — conversations with avatars (via Coil), last message preview, typing/online/last-seen status, unread badges, pinned & muted indicators, pull-to-load.
- **Chat room** — message bubbles (incoming/outgoing), send text messages, delivery status, attach/voice/emoji affordances, typing indicator.
- **New chat** — quick entry points (New Group, New Secret Chat, New Channel, Contacts, Saved Messages).
- **Profile** — user avatar, name, username, phone, bio.
- **Settings** — Account, Notifications, Privacy, Devices, Data, Appearance, Language, About (every item gives visible snackbar feedback).
- **Light & Dark theme** with the iconic Telegram blue palette.

## Screens

| Login | Chat List | Chat Room | Settings |
|-------|-----------|-----------|----------|
| Phone auth | Conversations | Messages | All responsive |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 1.9.x |
| UI | Jetpack Compose (BOM 2024.02), Material 3 |
| Architecture | Single-activity, MVVM (ViewModel + StateFlow) |
| Navigation | Navigation-Compose |
| Async | Kotlin Coroutines + Flow |
| Telegram | TDLib 1.8.56 (`com.github.tdlibx:td`) |
| Images | Coil-Compose |
| Build | Gradle Kotlin DSL, AGP 8.2.2 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 (Android 16) |

## Download

Signed release APKs are published on the **[Releases](../../releases/latest)** page. Grab the latest `Yugram-*.apk`, install it on your device, and sign in with your Telegram account.

> Enable **"Install from unknown sources"** on your device before installing.
>
> **Updating from an old debug build?** Early builds were debug-signed with a per-CI-runner key, so installing the signed release APK over them fails with *"App not installed"* (signature mismatch). **Uninstall the old app once**, then install the release APK — all future signed updates install cleanly over it.

## Build from source

### 1. Prerequisites

- **JDK 17**
- **Android SDK** with platform `android-36`
- A Telegram API key pair from <https://my.telegram.org> → *API development tools*

### 2. Provide your API credentials

TDLib needs an `api_id` and `api_hash`. Provide them in **either** of two ways:

**Option A — local `gradle.properties`** (recommended for local dev):  
Add to the project root `gradle.properties` (or your `~/.gradle/gradle.properties`):

```properties
TELEGRAM_API_ID=123456
TELEGRAM_API_HASH=0123456789abcdef0123456789abcdef
```

**Option B — environment variables** (how CI injects them):

```bash
export ORG_GRADLE_PROJECT_TELEGRAM_API_ID=123456
export ORG_GRADLE_PROJECT_TELEGRAM_API_HASH=0123456789abcdef0123456789abcdef
```

> If credentials are missing, the app won't crash — it shows an informative banner via `TelegramConfig.isConfigured`, but login won't work.

### 3. Build & run

```bash
# Debug APK
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk

# Install on a connected device
./gradlew installDebug
```

## Architecture

```
app/src/main/java/com/telegram/clone/
├── MainActivity.kt                  # Single-activity host + NavHost
├── TelegramCloneApplication.kt      # App init, TDLib bootstrap, notification channels
├── core/
│   ├── config/TelegramConfig.kt     # API credentials, graceful fallback
│   └── network/TDLibClientManager.kt# Singleton TDLib Client wrapper
├── data/
│   ├── model/MessageModels.kt       # TdApi -> UI mappers
│   └── repository/TelegramRepository.kt  # Cache + async TDLib calls
└── ui/
    ├── auth/        # LoginScreen + LoginViewModel
    ├── home/        # ChatListScreen + ChatListViewModel
    ├── chat/        # ChatRoomScreen + ChatRoomViewModel
    ├── newchat/     # NewChatScreen
    ├── profile/     # ProfileScreen
    ├── settings/    # SettingsScreen
    └── theme/       # Color / Type / Theme
```

### Key engineering notes

- **Crash-safe native init** — `TelegramCloneApplication` catches `Throwable` (not just `Exception`) so native-library load failures (`UnsatisfiedLinkError`) are logged instead of hard-crashing.
- **No deadlocks** — `TelegramRepository.getUser()` / `getChat()` use `CompletableDeferred` + `withTimeoutOrNull` instead of the deadlock-prone `Mutex(true)` pattern, so UI calls never hang forever.
- **Android 16 ready** — `windowOptOutEdgeToEdgeEnforcement` keeps the classic status/nav-bar background (screens aren't inset-aware yet).

## CI

Every push to `main` triggers the **Android Build** workflow (`.github/workflows/android-build.yml`) which builds a debug APK and uploads it as a workflow artifact. Version code/name derive from the GitHub run number.

## License

This project is for educational purposes. TDLib is licensed under the terms available at the [TDLib repository](https://github.com/tdlib/td). Telegram and the Telegram logo are trademarks of Telegram FZ-LLC. This project is not affiliated with or endorsed by Telegram.
