# Navigation

`:core:navigation` owns the app's single [Navigation 3](https://developer.android.com/guide/navigation/navigation-3)
back stack for Android and iOS. A singleton `Navigator` edits it, feature modules contribute
their screens through `EntryProvider`s, and `rememberNavigator` saves and restores it.

| Type | Package | Role |
| --- | --- | --- |
| `Navigator` | `core.navigation` | `navigate(key) { options }` and `navigateUp()` on the shared `NavBackStack` |
| `NavOptions` | `core.navigation` | `launchSingleTop` and `popUpTo`, built by `navigate`'s options block |
| `EntryProvider` | `core.navigation.utils` | A feature's `entry<Key> { ... }` builder and the serializers of its keys |
| `EntriesAggregator` | `core.navigation.utils` | Every `EntryProvider` in the graph, as Koin collects them |
| `rememberNavigator` | `core.navigation.utils` | The navigator, saved across configuration changes and process death |
| `HomeEntry` | `core.navigation.entries.home` | The start key |

## Usage

Add `implementation(projects.core.navigation)` to the consuming module's `commonMain`
dependencies, and add `NavigationModule` to the `includes` of the application's root Koin
module (`AppModule`). A Gradle dependency alone registers nothing.

### Adding a screen

Declare the key under `entries/`, so both the screen that navigates to it and the module that
renders it can see it. Keys are compared by value: navigating to a key equal to the top does
nothing.

```kotlin
@Serializable
data class ConversationEntry(val id: String) : NavKey
```

Bind an `EntryProvider` in the module that owns the screen. Koin collects every one into
`EntriesAggregator`, which the hosts hand to `App()`.

```kotlin
@Single
class ConversationEntryProvider : EntryProvider {
    override fun serializerModule() = SerializersModule {
        polymorphic(NavKey::class) { subclass(ConversationEntry::class, ConversationEntry.serializer()) }
    }

    override fun entryBuilder(): EntryProviderScope<NavKey>.() -> Unit = {
        entry<ConversationEntry> { key ->
            val viewModel = koinViewModel<ConversationViewModel>()
            ConversationScreen(id = key.id, onNavigateUp = viewModel::navigateUp)
        }
    }
}
```

> [!IMPORTANT]
> Register every key in its provider's `serializerModule()`. A missing one navigates fine and only
> throws `SerializationException` when the back stack is saved, for example when the app goes to
> the background.

### Navigating

Handle navigation in a ViewModel. Inject `Navigator` and delegate to it, and pass
`binds = [<the ViewModel>::class]` so Koin doesn't also register the ViewModel as the app's
`Navigator`:

```kotlin
@KoinViewModel(binds = [ConversationViewModel::class])
class ConversationViewModel(val navigator: Navigator) : ViewModel(), Navigator by navigator
```

Keep screens stateless: they take callbacks such as `onNavigateUp: () -> Unit` and never obtain
a ViewModel, so previews and UI tests run without Koin.

`navigate` takes an options block, like Navigation 2's:

| Call | Back stack before → after |
| --- | --- |
| `navigate(B)` | `[A]` → `[A, B]`, and `[A, B]` stays `[A, B]` |
| `navigate(Page(2)) { launchSingleTop = true }` | `[A, Page(1)]` → `[A, Page(2)]` |
| `navigate(C) { popUpTo<B>() }` | `[A, B, X]` → `[A, B, C]` |
| `navigate(C) { popUpTo<B> { inclusive = true } }` | `[A, B, X]` → `[A, C]` |
| `navigate(C) { popUpTo(navBackStack.first()) { inclusive = true } }` | `[A, B, X]` → `[C]` |

`popUpTo` takes a key or a key type and pops to its topmost match; with no match it pops nothing.
`launchSingleTop` compares the key's type with the top that `popUpTo` left. `navigateUp()`
returns `false` instead of popping the root.

## Saved state

`rememberNavigator(startEntry)` remembers the navigator with a `Saver` that encodes its back
stack polymorphically, using the `SavedStateConfiguration` built from every provider's
serializers. On restore it asks Koin for the `Navigator` with the decoded stack: after a
configuration change Koin returns the live singleton, and after process death it builds a new one
from the restored stack. Either way, `NavDisplay` shows the stack the navigator edits.

Don't remember the stack a second time with `rememberNavBackStack` or `rememberSerializable`.
After recreation, navigation would then edit a stack that is no longer on screen.

## Tests

The navigator tests are in `commonTest`, so they run on the Android host and on the iOS simulator:

```bash
./gradlew :core:navigation:testAndroidHostTest
```

```bash
./gradlew :core:navigation:iosSimulatorArm64Test
```
