<a id="top"></a>

<div align="center">

<img src=".github/assets/banner.svg" alt="Chat: a Kotlin Multiplatform chat app for Android and iOS, shown in voice mode" width="100%">

<br>
<br>

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose_Multiplatform-1.12.1-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)
[![Android](https://img.shields.io/badge/Android-API_29%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#-getting-started)
[![iOS](https://img.shields.io/badge/iOS-18.2%2B-000000?style=for-the-badge&logo=apple&logoColor=white)](#-getting-started)

![Gradle](https://img.shields.io/badge/Gradle-9.5.1-02303A?style=flat-square&logo=gradle&logoColor=white)
![AGP](https://img.shields.io/badge/AGP-9.1.1-34A853?style=flat-square&logo=android&logoColor=white)
![Koin](https://img.shields.io/badge/Koin-4.2.2-F39C12?style=flat-square)
![JDK](https://img.shields.io/badge/JDK-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Detekt](https://img.shields.io/badge/Detekt-1.23.8-5C6BC0?style=flat-square)

**One Kotlin codebase. Two native apps. Shared UI, compile-time DI, and encrypted storage from day one.**

[Highlights](#-highlights) •
[Architecture](#%EF%B8%8F-architecture) •
[Getting started](#-getting-started) •
[Commands](#%EF%B8%8F-commands) •
[Build logic](#-build-logic) •
[Troubleshooting](#-troubleshooting)

</div>

<br>

> [!NOTE]
> **Status: foundation.** The build system, dependency injection, secure storage, the local database
> and sign-in with Google or an email and password are in place, and so is the chat data layer:
> conversations and messages in Room, with canned replies streamed into them, the model picker's
> favorites, personas and default model, History's data: the conversation list, its search,
> pins, archives and deletes, forks: a conversation copied up to a message, with where it came
> from, and compare mode: two models answering side by side, streaming at once, and going on with
> one of them. After sign-in the app opens the chat thread: send a message, watch the reply think, stream
> and finish, copy its code, and reopen a recent conversation. Tap a message to copy it, fork the
> conversation there (the fork says where it came from until its banner is dismissed), play a
> reply's read-aloud bar (visual only, nothing speaks), or put a message of yours back in the
> composer. The model chip opens the model picker: pick one of the eight models or a persona, star
> favorites, and the accent follows at once. Turn compare on there and pick a second model: each
> message is then answered by both, in two cards side by side that you swipe between, and
> "continue with this →" goes on with one of them. The History button opens every conversation on the
> device, grouped pinned, today, yesterday and earlier: search it, swipe a row to pin, archive or
> delete it, and tap one to reopen it. Voice, attachments, Memory and Settings come next.
> The voice mode screen in the banner comes from the design prototype, not from a build of the app.

## ✨ Highlights

<table>
  <tr>
    <td width="50%" valign="top">
      <h3>📱 Shared UI, native hosts</h3>
      The whole interface is Compose Multiplatform in <code>:app</code>. Android renders it from
      <code>MainActivity</code>, and iOS embeds it in SwiftUI through a static <code>App</code> framework.
    </td>
    <td width="50%" valign="top">
      <h3>🧱 Convention-plugin build</h3>
      Six <code>chat.*</code> plugins in <code>build-logic</code> own every SDK level, target and
      dependency set. A module's build file is just its <code>plugins { }</code> block.
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>💉 Compile-time checked DI</h3>
      Koin annotations plus the Koin compiler plugin generate the graph per target and report
      missing definitions when the code compiles, not at first injection.
    </td>
    <td width="50%" valign="top">
      <h3>🔐 Encrypted preferences</h3>
      <code>:core:preferences</code> ships plain and encrypted <code>DataStore</code> instances backed by
      Android Keystore and the iOS Keychain, with authenticated, versioned ciphertext.
    </td>
  </tr>
  <tr>
    <td width="50%" valign="top">
      <h3>🧭 Modular Navigation 3</h3>
      <code>:core:navigation</code> holds one saved back stack behind a <code>Navigator</code> with
      Navigation 2 style options, and each module adds its screens through an <code>EntryProvider</code>.
    </td>
    <td width="50%" valign="top">
      <h3>⚡ Fast, reproducible builds</h3>
      Configuration cache and build cache are on, versions live in one catalog, and the Gradle
      daemon runs on a pinned Azul Zulu 21 toolchain.
    </td>
  </tr>
</table>

## 🏛️ Architecture

```mermaid
flowchart TD
    subgraph hosts ["Platform hosts"]
        androidApp[":androidApp<br/><i>MainActivity · MainApplication</i>"]
        iosApp["iosApp<br/><i>SwiftUI · Xcode project</i>"]
    end

    app[":app<br/><i>Compose UI · root AppModule</i>"]
    db[":core:database<br/><i>ChatDatabase · Room · bundled SQLite</i>"]
    nav[":core:navigation<br/><i>Navigator · entry providers</i>"]
    prefs[":core:preferences<br/><i>plain + encrypted DataStore</i>"]
    ui[":core:ui<br/><i>ChatTheme · tokens · components</i>"]
    featureAuth[":feature:auth<br/><i>Splash · sign-in: Google, email</i>"]
    featureChat[":feature:chat<br/><i>Chat thread · composer · model picker · message actions</i>"]
    featureHistory[":feature:history<br/><i>History · search · swipe to pin, archive, delete</i>"]
    dataAuth[":data:auth<br/><i>AuthManager · Firebase Auth</i>"]
    dataChat[":data:chat<br/><i>ChatRepository · ModelRepository · ForkRepository · CompareRepository · canned streamed replies · history</i>"]

    androidApp -- "implementation" --> app
    androidApp -- "implementation" --> nav
    iosApp -- "App.framework (static)" --> app
    app -- "implementation" --> db
    app -- "implementation" --> nav
    app -- "implementation" --> prefs
    app -- "implementation" --> ui
    app -- "implementation" --> featureAuth
    app -- "implementation" --> featureChat
    app -- "implementation" --> featureHistory
    app -- "implementation" --> dataAuth
    app -- "implementation" --> dataChat
    featureAuth -- "implementation" --> dataAuth
    featureChat -- "implementation" --> dataChat
    featureHistory -- "implementation" --> dataChat
    dataChat -- "implementation" --> db
    dataChat -- "implementation" --> prefs
```

| Module | Role |
| --- | --- |
| [`:androidApp`](androidApp) | The Android application. Starts Koin with `androidContext(...)` and sets `AppHost(...)` as content. |
| [`iosApp`](iosApp) | The Xcode project. Calls `KoinKt.doInitKoin()` and wraps `MainViewController()` in SwiftUI. |
| [`:app`](app) | Shared Compose Multiplatform UI: `AppHost()`, which both hosts call, gives `App()` the back stack and the default model's accent from `AppViewModel`; `App()` renders the back stack in a `NavDisplay` inside the root `ChatTheme`. Holds the root Koin module, `AppModule`, which `includes` feature and core modules. |
| [`:core:database`](core/database) | `ChatDatabase`, the on-device Room database every conversation and message is stored in, on the bundled SQLite driver: the `conversations`, `messages` and `personas` tables, their DAOs and a `TransactionRunner`. [Read the module guide →](core/database/README.md) |
| [`:core:navigation`](core/navigation) | The shared `Navigator`, navigation keys, `EntryProvider` contract and back-stack saving. [Read the module guide →](core/navigation/README.md) |
| [`:core:preferences`](core/preferences) | Singleton `DataStore<Preferences>` for ordinary settings and for credentials. [Read the module guide →](core/preferences/README.md) |
| [`:core:ui`](core/ui) | The design system: `ChatTheme`, per-model palettes, typography, tokens and components. [Read the module guide →](core/ui/README.md) |
| [`:data:auth`](data/auth) | `AuthManager`: the signed-in user and Google sign-in. `EmailAuthManager`: email + password sign-in, account creation and password-reset emails. Both on Firebase Auth, with the Android SDK on Android and the iOS SDK through SwiftPM. |
| [`:data:chat`](data/chat) | `ChatRepository`: conversations and their messages as `Flow`s from Room, `send`, which saves the message and streams one of the design's canned replies into the database on a timer, with its latency, and `choose`, which changes a conversation's model, persona and compare mode; while a conversation compares two models, `send` answers with a compare pair, both sides streaming at once. For History, the list of conversations that are not archived and its search over titles, the user's messages and model names, `setPinned`, `archive` and `delete`, and `groupHistory`, which splits the list into pinned, today, yesterday and earlier. `ModelRepository`: the favorite models and the default model for new chats in the plain DataStore, and the personas in Room, with the design's three presets seeded. `ForkRepository`: "fork here", a new conversation copied from another one up to a message, which records where it came from for the fork banner, and the banner's dismissal. `CompareRepository`: a compare pair's "continue with this →", which goes on with one side's model alone. Also the model catalogue, `ModelCatalogue`. No model provider and no API keys. |
| [`:feature:auth`](feature/auth) | The Splash session gate and the sign-in screen: Google, email + password sign-in, account creation and the password-reset email, as modes of one screen. Opens the Google account picker: Credential Manager on Android, `GIDSignIn` on iOS. |
| [`:feature:chat`](feature/chat) | The chat thread, the first screen after sign-in: the empty state with "recent" conversations, messages with paragraph, list and syntax-coloured code blocks, the streaming caret and latency, "copy", the composer and new chat. A tap on a message shows its actions: "copy", "⑂ fork here", which opens the fork under a banner saying where it came from, "read aloud", a visual player with no speech, and "retry", which puts the message back in the composer. The model picker sheet: favorites, personas and every model, stars, and a pick that changes the conversation or the new chat. Compare mode: the picker's compare button and second model, the "sonnet-4.5 ⇄ gpt-5" chip, and each reply as a pair of cards, one per model, that stream at once in a swipeable carousel, with "continue with this →" on a finished card. The accent follows the conversation's model. The History button opens History. |
| [`:feature:history`](feature/history) | History: every conversation on the device in "pinned", "today", "yesterday" and "earlier", newest first, regrouped when the date changes; the search over titles, the user's messages and model names; the second model of a conversation that compares ("⇄ gpt-5"); rows that swipe left to pin, archive or delete, with the "archived" and "deleted" toasts, and the same actions for screen readers; a tap that reopens the conversation in the chat. The status row says how many conversations are on this device, since nothing syncs. |

<details>
<summary><b>How startup works on each platform</b></summary>

<br>

| | Android | iOS |
| --- | --- | --- |
| Entry point | `MainApplication.onCreate()` | `iOSApp.init()`, which also calls `FirebaseApp.configure()` and sets the `GIDSignIn` client ID |
| Koin start | `startKoin<AppModule> { androidContext(...) }` | `KoinKt.doInitKoin()` → `startKoin { module<AppModule>() }` |
| UI host | `MainActivity` → `setContent { ... }` | `ComposeView` → `MainViewControllerKt.MainViewController()` |
| Composition root | `AppHost(entries)`: `rememberNavigator(SplashEntry)` and the root accent, then `App(navBackStack, entryBuilders, palette = ...)` | The same, inside `ComposeUIViewController { ... }` |

Both hosts read the entry builders from `EntriesAggregator`. `MainActivity` injects it with
`by inject()`, and iOS gets it from `KoinPlatform.getKoin()`.

iOS uses the untyped `startKoin` on purpose: with Koin compiler plugin 1.2.1, the typed form in the
iOS compilation reports `KOIN-D002` for valid lookups across Gradle modules. See
[`Koin.kt`](app/src/iosMain/kotlin/br/com/weslleycampos/chat/Koin.kt).

</details>

## 🧰 Tech stack

| Layer | Library | Version |
| --- | --- | --- |
| Language | [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) | `2.4.20` |
| UI | [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) · Material 3 | `1.12.1` · `1.12.0-alpha03` |
| Navigation | [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) | `1.1.2` |
| Lifecycle | JetBrains `lifecycle-runtime-compose` · `lifecycle-viewmodel-compose` | `2.11.0` |
| Dependency injection | [Koin](https://insert-koin.io) · Koin compiler plugin | `4.2.2` · `1.2.1` |
| Storage | [AndroidX DataStore](https://developer.android.com/topic/libraries/architecture/datastore) Preferences (Okio) | `1.2.1` |
| Database | [Room](https://developer.android.com/kotlin/multiplatform/room) (`androidx.room3`) · [SQLite](https://developer.android.com/kotlin/multiplatform/sqlite) bundled driver | `3.0.3` · `2.7.1` |
| Serialization | [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) JSON, through its BOM, for the reply blocks column | `1.12.0` |
| Date and time | [kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime), for local message times and dates and History's days | `0.8.0` |
| Code generation | [KSP](https://github.com/google/ksp), for Room's compiler | `2.3.12` |
| Authentication | [Firebase Auth](https://firebase.google.com/docs/auth) · Android BoM · iOS SDK | `34.19.0` · `12.19.2` |
| Google sign-in | [Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg) · `googleid` · [GoogleSignIn-iOS](https://github.com/google/GoogleSignIn-iOS) | `1.6.0` · `1.2.1` · `10.0.0` |
| Static analysis | [Detekt](https://detekt.dev) + `detekt-formatting` | `1.23.8` |
| Build | Gradle · Android Gradle Plugin | `9.5.1` · `9.1.1` |

Every version lives in [`gradle/libs.versions.toml`](gradle/libs.versions.toml), including the Android
SDK levels (`compileSdk 37`, `targetSdk 37`, `minSdk 29`) and the app version.

## 🚀 Getting started

### Prerequisites

| Tool | Requirement |
| --- | --- |
| ☕ JDK | **21 or newer** to launch Gradle. The build stops early on anything older. |
| 🤖 Android | Android Studio or IntelliJ IDEA with the Kotlin Multiplatform plugin, and Android SDK 37. |
| 🍎 iOS | macOS with Xcode and the iOS 18.2+ SDK. An **Apple silicon** Mac is needed for the simulator, since the only simulator target is `iosSimulatorArm64`. |

> [!TIP]
> The JDK you install only launches Gradle. The build itself runs on the Azul Zulu 21 toolchain pinned in
> [`gradle/gradle-daemon-jvm.properties`](gradle/gradle-daemon-jvm.properties), which Gradle downloads on the
> first build, so that first build needs network access.

### Clone

```bash
git clone git@github.com:weslley-poatek/chat.git
```

```bash
cd chat
```

### 🔥 Firebase setup

Both apps need a Firebase project with **Google** and **Email/Password** enabled under
**Authentication → Sign-in method**.
The config files are local-only and gitignored, so download them from the Firebase console:

| | 🤖 Android | 🍎 iOS |
| --- | --- | --- |
| Register the app as | `br.com.weslleycampos.chat`, plus your debug SHA-1 from `./gradlew :androidApp:signingReport` | `br.com.weslleycampos.chat` |
| Download | `google-services.json` into [`androidApp/`](androidApp) | `GoogleService-Info.plist` into [`iosApp/iosApp/`](iosApp/iosApp) |
| Then | Nothing: the google-services plugin reads it at build time | Copy its `REVERSED_CLIENT_ID` into `GOOGLE_REVERSED_CLIENT_ID` in [`Config.xcconfig`](iosApp/Configuration/Config.xcconfig) |

> [!IMPORTANT]
> Without `google-services.json` the Android build fails, and without `GoogleService-Info.plist` the iOS
> app crashes on launch in `FirebaseApp.configure()`. An empty `GOOGLE_REVERSED_CLIENT_ID` crashes the
> iOS app when you tap **Continue with Google**. If you set `TEAM_ID`, register the iOS app as
> `br.com.weslleycampos.chat<TEAM_ID>`, the bundle identifier it produces.

### 🤖 Run on Android

Pick the `androidApp` run configuration in the IDE toolbar, or build from the terminal:

```bash
./gradlew :androidApp:installDebug
```

### 🍎 Run on iOS

```bash
open iosApp/iosApp.xcodeproj
```

Choose a simulator and press **Run**. The **Compile Kotlin Framework** build phase calls
`./gradlew :app:embedAndSignAppleFrameworkForXcode`, so Xcode builds the shared code for you.

> [!IMPORTANT]
> To run on a physical device, set `TEAM_ID` in [`iosApp/Configuration/Config.xcconfig`](iosApp/Configuration/Config.xcconfig).
> It feeds both the signing team and the bundle identifier.
>
> If Xcode fails with **"Unable to locate a Java Runtime"**, see [Troubleshooting](#-troubleshooting).

## ⌨️ Commands

| Task | Command |
| --- | --- |
| Build the Android debug APK | `./gradlew :androidApp:assembleDebug` |
| Install it on a connected device | `./gradlew :androidApp:installDebug` |
| Shared tests on the Android host | `./gradlew :app:testAndroidHostTest` |
| Shared tests on the iOS simulator | `./gradlew :app:iosSimulatorArm64Test` |
| Database tests on the Android host | `./gradlew :core:database:testAndroidHostTest` |
| Database tests on the iOS simulator | `./gradlew :core:database:iosSimulatorArm64Test` |
| Preferences tests on the iOS simulator | `./gradlew :core:preferences:iosSimulatorArm64Test` |
| Navigation tests on the Android host | `./gradlew :core:navigation:testAndroidHostTest` |
| Navigation tests on the iOS simulator | `./gradlew :core:navigation:iosSimulatorArm64Test` |
| UI tests on the Android host | `./gradlew :core:ui:testAndroidHostTest` |
| UI tests on the iOS simulator | `./gradlew :core:ui:iosSimulatorArm64Test` |
| Auth tests on the Android host | `./gradlew :data:auth:testAndroidHostTest :feature:auth:testAndroidHostTest` |
| Auth tests on the iOS simulator | `./gradlew :data:auth:iosSimulatorArm64Test :feature:auth:iosSimulatorArm64Test` |
| Chat data tests on the Android host | `./gradlew :data:chat:testAndroidHostTest` |
| Chat data tests on the iOS simulator | `./gradlew :data:chat:iosSimulatorArm64Test` |
| Chat screen tests on the Android host | `./gradlew :feature:chat:testAndroidHostTest` |
| Chat screen tests on the iOS simulator | `./gradlew :feature:chat:iosSimulatorArm64Test` |
| History screen tests on the Android host | `./gradlew :feature:history:testAndroidHostTest` |
| History screen tests on the iOS simulator | `./gradlew :feature:history:iosSimulatorArm64Test` |
| Static analysis for every module | `./gradlew detekt` |

> [!WARNING]
> Detekt runs with `autoCorrect = true`, so `detekt` and `check` may **rewrite source files**.
> Commit or stash your work first if you want to review the corrections as a separate diff.

## 🧱 Build logic

All shared configuration lives in the [`build-logic`](build-logic/convention/src/main/kotlin) included
build. Each module picks the conventions it needs:

| Convention plugin | What it sets up | `:androidApp` | `:app` | `:core:database` | `:core:navigation` | `:core:preferences` | `:core:ui` | `:data:auth` | `:data:chat` | `:feature:auth` | `:feature:chat` | `:feature:history` |
| --- | --- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `chat.android.application` | AGP application, Compose compiler, SDK levels, app id and version | ✅ | | | | | | | | | | |
| `chat.multiplatform.library` | KMP + Android KMP library target, Kotlin serialization, host and device tests, iOS `App` frameworks | | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `chat.compose.library` | Compose Multiplatform, Material 3, Navigation 3, lifecycle, per-module `Res` class | | ✅ | | ✅ | | ✅ | | | ✅ | ✅ | ✅ |
| `chat.room` | KSP and the Room Gradle plugin with schemas in `schemas/`, Room runtime and bundled SQLite driver, the Room compiler per target, and the driver and schema path its tests need | | | ✅ | | | | | | | | |
| `chat.koin` | Koin compiler plugin, plus runtime and annotations for the module type, and Koin Compose where Compose is on | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `chat.detekt` | Detekt with formatting rules, the shared [`detekt.yml`](detekt.yml) and HTML reports | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

`:androidApp` also applies the `com.google.gms.google-services` plugin, which turns `google-services.json`
into resources such as the `default_web_client_id` the Google account picker asks for.

### iOS SDKs through SwiftPM

`:data:auth` and `:feature:auth` declare the Firebase and GoogleSignIn iOS SDKs in a
`swiftPMDependencies { }` block, and Kotlin calls them directly through the generated
`swiftPMImport.Chat.*` packages. Xcode links them through
[`iosApp/KotlinMultiplatformLinkedPackage`](iosApp/KotlinMultiplatformLinkedPackage), which Gradle
generates. After adding or changing a `swiftPMDependencies` block, regenerate it and the Xcode project
reference from the repository root:

```bash
XCODEPROJ_PATH=$PWD/iosApp/iosApp.xcodeproj ./gradlew :app:integrateLinkagePackage
```

Pass an absolute path: a relative one is read from `app/`, which writes the package to
`app/iosApp/` instead.

### Adding a module

**1.** Register it in [`settings.gradle.kts`](settings.gradle.kts):

```kotlin
include(":feature:home")
```

**2.** Apply the conventions in `feature/home/build.gradle.kts`, then declare only what is unique to it:

```kotlin
plugins {
    alias(libs.plugins.chat.multiplatform.library)
    alias(libs.plugins.chat.compose.library) // only for modules with UI
    alias(libs.plugins.chat.koin)
    alias(libs.plugins.chat.detekt)
}
```

**3.** Add its Koin module to the `includes` of [`AppModule`](app/src/commonMain/kotlin/br/com/weslleycampos/chat/AppModule.kt).
A Gradle dependency alone registers nothing.

**4.** If it has screens, depend on `:core:navigation` and bind an `EntryProvider` for each one.
The [navigation guide](core/navigation/README.md#adding-a-screen) walks through it.

Names are derived from the Gradle path, so there is nothing to configure:

| Gradle path | Android namespace | Compose resources class |
| --- | --- | --- |
| `:app` | `br.com.weslleycampos.chat.app` | `br.com.weslleycampos.chat.app.resources.AppRes` |
| `:feature:home` | `br.com.weslleycampos.chat.feature.home` | `br.com.weslleycampos.chat.feature.home.resources.FeatureHomeRes` |

## 🔐 Secure storage

`:core:preferences` exposes two qualified `DataStore<Preferences>` instances. Inject
`@UnencryptedPreferences` for ordinary settings and `@EncryptedPreferences` for credentials that
can be replaced by signing in again.

| | 🤖 Android | 🍎 iOS |
| --- | --- | --- |
| Cipher | AES-256-GCM | AES-256-CBC + HMAC-SHA256, verified before decrypting |
| Key storage | Android Keystore | Keychain, `AfterFirstUnlockThisDeviceOnly` |
| File location | `noBackupFilesDir` | Application Support, excluded from backup |
| Bound to ciphertext | Format version + store name, as AAD | Format version + store name, as AAD |

If a key is lost or the file is corrupt, the encrypted store resets to empty, which the app should
treat as signed out. The [module guide](core/preferences/README.md) covers usage, the byte format,
recovery and DI details.

## 🗂️ Project structure

```text
chat/
├── androidApp/                    🤖 Android entry point
├── app/                           📱 Shared Compose UI and root Koin module
│   └── src/
│       ├── commonMain/            AppHost(), App(), AppViewModel (the root accent), AppModule
│       ├── iosMain/               MainViewController, initKoin()
│       └── commonTest/            AppEntriesTest, AppViewModelTest: keys survive a save; the root accent
├── core/
│   ├── database/                  🗄️ ChatDatabase on Room, with its exported schemas
│   ├── navigation/                🧭 Navigator, keys and entry providers
│   ├── preferences/               🔐 Plain and encrypted DataStore
│   └── ui/                        🎨 ChatTheme, tokens and components
├── data/
│   ├── auth/                      🔑 AuthManager and EmailAuthManager on Firebase Auth
│   └── chat/                      💬 ChatRepository, ModelRepository, ForkRepository, CompareRepository, canned streamed replies, models, personas, History's groups
├── feature/
│   ├── auth/                      👋 Splash gate and the sign-in screen (Google, email + password)
│   ├── chat/                      💬 The chat thread: empty state, messages, code blocks, composer, model picker, message actions
│   └── history/                   🗂️ History: grouped list, search, swipe to pin, archive or delete
├── iosApp/                        🍎 Xcode project and SwiftUI host
│   └── KotlinMultiplatformLinkedPackage/  📦 Generated SwiftPM package for the iOS SDKs
├── build-logic/
│   └── convention/                🧱 chat.* convention plugins
├── gradle/
│   ├── libs.versions.toml         📦 The single version catalog
│   └── gradle-daemon-jvm.properties
└── detekt.yml                     🧹 Shared static-analysis rules
```

## 🩺 Troubleshooting

<details>
<summary><b>Xcode: "Unable to locate a Java Runtime"</b></summary>

<br>

The Xcode build fails in the **Compile Kotlin Framework** build phase with:

```text
The operation couldn’t be completed. Unable to locate a Java Runtime.
Please visit http://www.java.com for information on installing Java.
```

**Cause.** That phase runs `./gradlew` through `/bin/sh`, and Xcode never loads `~/.zshrc`.
A JDK that only your shell knows about (SDKMAN, jenv, an `export JAVA_HOME` in `.zshrc`) is
invisible to it, so Gradle falls back to the macOS `java` stub, which only sees JDKs
registered with the system. The same build works in your terminal because the terminal
does load `.zshrc`.

**Check.** Run these from the repository root:

```bash
/usr/libexec/java_home -V
```

If it prints `Unable to locate a Java Runtime`, no JDK is registered with macOS and Xcode
cannot build. This reproduces what Xcode sees (no `.zshrc`, minimal `PATH`):

```bash
env -i HOME="$HOME" PATH=/usr/bin:/bin:/usr/sbin:/sbin ./gradlew --version
```

It fails with the same message while the problem exists, and prints the Gradle version once
it is fixed.

**Fix.** Register a JDK (21 or newer) with macOS. Pick one:

- Android Studio's bundled JDK, if you have Android Studio installed. No download needed.
  The link breaks if the app is moved or renamed, so repeat it then:

  ```bash
  mkdir -p ~/Library/Java/JavaVirtualMachines
  ln -s "/Applications/Android Studio.app/Contents/jbr" ~/Library/Java/JavaVirtualMachines/android-studio-jbr.jdk
  ```

- A system-wide Temurin JDK (asks for your admin password):

  ```bash
  brew install --cask temurin@21
  ```

- A JDK you already manage with SDKMAN. SDKMAN's folders are not macOS `.jdk` bundles, so this
  wraps one in a small bundle that points at it. Set `JDK` to a version listed by
  `sdk list java` that is installed:

  ```bash
  JDK=21.0.11-tem
  test -d "$SDKMAN_DIR/candidates/java/$JDK" || echo "Not found: $SDKMAN_DIR/candidates/java/$JDK (stop here)"
  V=${JDK%%-*}
  B=~/Library/Java/JavaVirtualMachines/sdkman-$JDK.jdk
  mkdir -p "$B/Contents/MacOS"
  ln -s "$SDKMAN_DIR/candidates/java/$JDK" "$B/Contents/Home"
  ln -s ../Home/lib/libjli.dylib "$B/Contents/MacOS/libjli.dylib"
  cat > "$B/Contents/Info.plist" <<EOF
  <?xml version="1.0" encoding="UTF-8"?>
  <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
  <plist version="1.0">
  <dict>
    <key>CFBundleIdentifier</key><string>sdkman.$JDK.jdk</string>
    <key>CFBundleName</key><string>SDKMAN $JDK</string>
    <key>CFBundleExecutable</key><string>libjli.dylib</string>
    <key>CFBundlePackageType</key><string>BNDL</string>
    <key>CFBundleInfoDictionaryVersion</key><string>7.0</string>
    <key>JavaVM</key>
    <dict>
      <key>JVMCapabilities</key><array><string>CommandLine</string></array>
      <key>JVMPlatformVersion</key><string>$V</string>
      <key>JVMVendor</key><string>SDKMAN</string>
      <key>JVMVersion</key><string>$V</string>
    </dict>
  </dict>
  </plist>
  EOF
  ```

  The bundle follows that one installed version: if you uninstall it with `sdk uninstall`,
  delete the bundle and repeat this with the version you keep.

Then run the two **Check** commands again: the first must list a JDK and the second must
print a Gradle version. Build from Xcode again afterwards; no project change is needed.

</details>

<br>

<div align="center">

<sub><a href="#top">↑ Back to top</a></sub>

</div>
