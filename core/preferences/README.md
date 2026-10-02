# Preferences

`:core:preferences` provides two singleton AndroidX `DataStore<Preferences>` instances
for Android and iOS. Use the unencrypted store for ordinary settings and the encrypted
store for credentials that can be replaced by signing in again.

| Injection qualifier | Store name | Contents |
| --- | --- | --- |
| `@UnencryptedPreferences` | `chat.preferences_pb` | Plain Preferences protobuf |
| `@EncryptedPreferences` | `chat.encrypted.preferences_pb` | Authenticated, encrypted Preferences protobuf |

## Usage

Add `implementation(projects.core.preferences)` to the consuming module's `commonMain`
dependencies, and add `PreferencesModule` to the `includes` of the application's root
Koin module (`AppModule`). A Gradle dependency alone registers nothing.

Inject the qualified DataStore and use its normal `data` and `edit` APIs:

```kotlin
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import br.com.weslleycampos.chat.core.preferences.qualifiers.EncryptedPreferences
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class SessionPreferences(
    @EncryptedPreferences private val preferences: DataStore<Preferences>,
) {
    private val tokenKey = stringPreferencesKey("token")

    val token = preferences.data.map { it[tokenKey] }

    suspend fun saveToken(token: String) {
        preferences.edit { it[tokenKey] = token }
    }

    suspend fun clearToken() {
        preferences.edit { it.remove(tokenKey) }
    }
}
```

Place the consumer in a package scanned by its Koin module. For ordinary settings,
inject `@UnencryptedPreferences` instead. Consumers do not need a custom serializer
or a platform-specific preferences API.

## Platform storage

| Platform | Preferences location | Encryption | Key storage |
| --- | --- | --- | --- |
| Android | `Context.noBackupFilesDir` | AES-256-GCM | Android Keystore |
| iOS | Application Support directory, under `chat/`, excluded from backup | AES-256-CBC with PKCS#7 padding and HMAC-SHA256 | Keychain generic password |

Both files, encrypted or not, live where the platform does not back them up, so
neither store survives a device restore.

The iOS key contains 64 random bytes: 32 for AES and 32 for HMAC. It uses
`AfterFirstUnlockThisDeviceOnly` accessibility and is not synced to iCloud or restored
to another device. A stored key with the wrong length is regenerated.

Android key material remains in Keystore. The iOS key is loaded into application memory.

## Authentication and format

`EncryptingSerializer` takes additional authenticated data (AAD) through its
constructor. Each factory supplies its store name as UTF-8 bytes. The serializer
prepends the format version to that AAD, binding ciphertext to both its version and
store identity. Any additional encrypted store must supply its own distinct AAD;
otherwise, files encrypted with the same key could be exchanged between stores.

The serialized bytes are:

```text
Android: version(1) || IV(12) || ciphertext || GCM tag(16)
iOS:     version(1) || IV(16) || ciphertext || HMAC tag(32)
```

Sizes are in bytes. The version byte is currently `1`. Empty input and unsupported
versions are treated as corruption. There is no fallback for unversioned ciphertext.

iOS authenticates the following message before decrypting:

```text
HMAC-SHA256(macKey, aadLength(8) || aad || IV || ciphertext)
```

`aadLength` is an eight-byte, big-endian byte count of the complete AAD, including
the version. This prevents shifting bytes between AAD and the encrypted payload
while retaining a valid tag. Tags are compared in constant time.

## Lost keys and corruption

The encrypted DataStore installs `ReplaceFileCorruptionHandler { emptyPreferences() }`.
When a missing key is regenerated and existing ciphertext no longer authenticates,
the store replaces its contents with encrypted empty preferences. Corrupt data and
unsupported formats follow the same recovery path. Reads then return an empty store,
and writes can save a new token, including when a write is the first operation.

Recovery clears **all preferences in the encrypted store**. The application should
interpret a missing token as a signed-out session; the module does not perform login
or navigation. The unencrypted store is unaffected by encrypted-store recovery.

The unencrypted DataStore installs the same handler: a corrupt `chat.preferences_pb`
is replaced with empty preferences, so ordinary settings fall back to their defaults
instead of every read failing.

Credential access failures are propagated rather than classified as authentication
failures, so an inaccessible Keystore or Keychain never replaces an existing key or
wipes the store.

Providers cache their keys. Missing-key recovery takes effect when the provider next
loads its key, normally after an app restart. Lost keys cannot recover old ciphertext,
and recovery requires successfully persisting the replacement data.

## Dependency injection details

All platform storage factories return `Storage<Preferences>` and use `@Single`
without explicit `binds`. The internal `@EncryptedStorage` and `@UnencryptedStorage`
qualifiers select the storage injected into the two DataStore factories. Everything
specific to the encrypted store lives in the `encrypted` package.

`PreferencesModule` is declared once in `commonMain`, yet each platform gets its own
storage factories. This works because Kotlin Multiplatform does not compile
`commonMain` separately and link it to platform code: each target has one
compilation that includes both source sets.

| Target compilation | Sources | Definitions collected by `@ComponentScan` |
| --- | --- | --- |
| Android | `commonMain` + `androidMain` | Both DataStore factories + Android storage factories |
| iOS | `commonMain` + `iosMain` | Both DataStore factories + iOS storage factories |

The Koin compiler plugin runs inside each of those compilations, so the module is
generated once per target with the factories that target contains. The common
DataStore factories only request a qualified `Storage<Preferences>`, and every
platform provides one, so no `expect`/`actual` declarations are needed. A new
platform target must provide both qualified storage factories in its own source set.
On Android, the storage factories take a `Context`, so the application must call
`androidContext(...)` when starting Koin.

The iOS storage factories use explicit `@Qualifier(Type::class)` annotations; the
Android storage and common DataStore factories use the shorthand. With Koin
compiler plugin 1.2.1, the custom annotation shorthand on two `iosMain` factories
returning the same type produces duplicate native symbols; `commonMain` is unaffected.
Consumers can still use the custom qualifier annotations shown above.

## Tests

Run from the repository root:

```bash
./gradlew :core:preferences:iosSimulatorArm64Test
./gradlew :core:preferences:detekt
```

The tests run on the iOS simulator, so they require Xcode. There is no Android host
test target.

- `EncryptingSerializerTest` (common) covers the serializer: round trip, version byte
  bound to the AAD, empty input, unsupported version, and authentication failures
  mapped to `CorruptionException`.
- `IosCryptoAeadTest` covers the iOS cipher: round trip, tampered tag, wrong AAD, and
  bytes shifted between IV and AAD.

Not covered by tests: the Android Keystore provider, the Keychain key storage (a
unit-test binary has no Keychain entitlement, so it needs the app as host), the
file-name constants, and the Koin bindings. Detekt's `autoCorrect` is on, so the
`detekt` task may rewrite sources.
