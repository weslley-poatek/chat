# Local database

## Goal

Give Chat one on-device database that every later row adds its tables to, so conversations,
messages and everything else the app keeps survive restarts. Nothing is synced to a server.

## Frames

None: this row has no screen.

## Acceptance criteria

Each criterion is one observable behaviour with at least one test that checks it.

- [x] Given a new in-memory `ChatDatabase` built with the bundled driver, when it opens, then it is
  on the current version (`DATABASE_VERSION`, 1), on the Android host and the iOS simulator —
  `ChatDatabaseTest.inMemoryDatabaseOpensOnTheCurrentVersion`
- [x] Given that database, when the version 1 table is queried, then the query runs and returns no
  rows — `ChatDatabaseTest.placeholderTableExistsAndIsEmpty`
- [x] Given the compiled database, when its version and identity hash are read, then
  `schemas/<ChatDatabase>/<version>.json` exists and records the same identity hash —
  `ExportedSchemaTest.currentVersionIsExportedWithTheCompiledIdentityHash`
- [x] Given the committed version 1 schema, when Room's `MigrationTestHelper` creates version 1 from
  it on the iOS simulator, then the `placeholder` table exists —
  `MigrationTestHelperTest.createsVersionOneFromTheExportedSchema`
- [x] Given `CoreDatabaseModule`, when Koin is asked for `ChatDatabase` twice on the iOS simulator,
  then it returns one instance whose on-disk file opens on the current version —
  `CoreDatabaseModuleTest.koinProvidesOneChatDatabaseThatOpensOnTheCurrentVersion`. The Android
  binding needs a `Context`, which host tests do not have, so only `:androidApp:assembleDebug`
  compiling it covers it.

## Data contract

- **Module and package:** `:core:database`, `br.com.weslleycampos.chat.core.database`. Entities go in
  `….entities`, DAOs in `….daos`.
- **Database:**

  ```kotlin
  internal const val DATABASE_VERSION = 1

  @Database(entities = [PlaceholderEntity::class], version = DATABASE_VERSION, exportSchema = true)
  @ConstructedBy(ChatDatabaseConstructor::class)
  abstract class ChatDatabase : RoomDatabase()

  internal expect object ChatDatabaseConstructor : RoomDatabaseConstructor<ChatDatabase>
  ```

- **Models:** `PlaceholderEntity(id: Long)`, table `placeholder`, never read or written.
- **Storage:** version 1. Android stores `chat.db` at `Context.getDatabasePath("chat.db")`, and iOS in
  the app's Documents directory. Both use `BundledSQLiteDriver()` and
  `setQueryCoroutineContext(Dispatchers.IO)`, with no destructive fallback. The schema is exported to
  `core/database/schemas/br.com.weslleycampos.chat.core.database.ChatDatabase/1.json`.
- **Koin binding:** `createChatDatabase(context: Context)` (Android) and `createChatDatabase()` (iOS),
  both `@Single`, bound as `ChatDatabase` in `CoreDatabaseModule`, which `AppModule` includes. Later
  rows bind each DAO with `@Single internal fun <name>Dao(database: ChatDatabase) = database.<name>Dao()`.
- **Behaviour:** the database opens on first injection and stays open for the process. Each schema
  change raises the version, adds an `@AutoMigration`, and commits the new schema JSON. The module
  guide, `core/database/README.md`, has the steps.
- **Tests:** `inMemoryChatDatabase()` (an `expect` function in `commonTest`, with actuals in
  `androidHostTest` and `iosTest`) gives DAO tests an empty in-memory database. `MigrationTestHelper`
  runs in `iosTest`, reading schemas from `ROOM_SCHEMA_DIRECTORY`.

## Decided

- Decided: Room 3.0.3 (`androidx.room3`), the latest stable Room. It is the multiplatform Room line,
  publishes Android, iOS and JVM artifacts, and builds with Kotlin 2.4.20, KSP 2.3.12 and AGP 9.1.1.
  Room 2.8.5 is the latest `androidx.room` and was not needed. Room 3 generates Kotlin only, through
  KSP, which suits a Kotlin-only app. 3.1.0 is still alpha.
- Decided: `androidx.sqlite` 2.7.1, the latest stable and the version Room 3.0.3 depends on.
  2.8.0 is still alpha.
- Decided: KSP 2.3.12, the latest release. KSP 2.3 is no longer tied to one Kotlin version, it
  supports AGP 9's built-in Kotlin and the Android KMP library plugin, and its minimum AGP is 8.12.
- Decided: version 1 has one table, `placeholder`, with only an `id` column. Room refuses a database
  without entities, and this table makes no claim about the data later rows design. It stays empty
  and is never dropped, so no later migration has to deal with it.
- Decided: the version lives in `internal const val DATABASE_VERSION`, which `@Database` and the
  tests read, so the version tests need no edit when a later row raises it.
- Decided: `chat.room` adds the Room compiler to each target's KSP configuration (`kspAndroid`,
  `kspIosArm64`, `kspIosSimulatorArm64`), not to `ksp(...)`, which KSP deprecates for multiplatform
  projects.
- Decided: on the Android host-test classpath, `chat.room` substitutes `sqlite-bundled-jvm` for
  `sqlite-bundled-android`. The Android artifact only carries `.so` files for devices, so the host JVM
  failed with `UnsatisfiedLinkError`. The desktop artifact has the same classes and carries natives
  for macOS, Linux and Windows.
- Decided: `chat.room` passes the schema directory's absolute path to every test task as
  `ROOM_SCHEMA_DIRECTORY` (`SIMCTL_CHILD_ROOM_SCHEMA_DIRECTORY` for the simulator, which strips the
  prefix), because a simulator test process does not start in the module directory.
- Decided: `MigrationTestHelper` is wired for the iOS simulator only (`room3-testing` on `iosTest`).
  On Android it needs an `Instrumentation`, which host tests do not have. Later rows test each
  auto-migration in `iosTest`. Room generates the migration from the same schema files for both
  platforms and checks the migrated schema on every open, Android included.
- Decided: `chat.room` passes `-Xexpect-actual-classes`, because `@ConstructedBy` needs an
  `expect object` in every Room module and Kotlin otherwise warns that expect/actual classes are Beta.
- Decided: Room's runtime is `api` in `chat.room`. A consumer cannot call a DAO getter on a database
  whose `RoomDatabase` supertype it cannot see.

## Out of scope

- The real tables (conversations, messages, memory, personas, settings): each later row adds its own
  entities, DAOs and auto-migrations.
- Repositories and the `:data` modules that read the database.
- Encrypting the database file, backups and export.
- Migration tests on Android devices (`androidDeviceTest`), which need an emulator.
