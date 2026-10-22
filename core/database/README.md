# Database

`:core:database` owns `ChatDatabase`, the app's single [Room](https://developer.android.com/kotlin/multiplatform/room)
database for Android and iOS. Conversations, messages and everything else Chat keeps are tables
here: they survive restarts and never leave the device.

| Type | Package | Role |
| --- | --- | --- |
| `ChatDatabase` | `core.database` | The `@Database`: its entities, version, auto-migrations and one getter per DAO |
| `DATABASE_VERSION` | `core.database` | The schema version, `1`. Tests compare against it, so they need no edit when it rises |
| `ChatDatabaseConstructor` | `core.database` | The `expect object` that `@ConstructedBy` names; Room's compiler writes the `actual` on each target |
| `CoreDatabaseModule` | `core.database` | The Koin module: the `ChatDatabase` singleton and every DAO |
| `PlaceholderEntity` | `core.database.entities` | Version 1's only table, `placeholder`. It stays empty and is never dropped |

The module uses Room 3 (`androidx.room3`), which is Kotlin-only and KSP-only: DAO functions are
`suspend` or return `Flow`, and there is no `SupportSQLiteDatabase`.

## Platform storage

| Platform | Database file | Builder |
| --- | --- | --- |
| Android | `Context.getDatabasePath("chat.db")` | `Room.databaseBuilder<ChatDatabase>(context, name)` |
| iOS | `chat.db` in the app's Documents directory | `Room.databaseBuilder<ChatDatabase>(name)` |

Both builders finish in `buildChatDatabase()`, which sets `BundledSQLiteDriver()`, so both platforms
run the SQLite that ships inside the app, and `setQueryCoroutineContext(Dispatchers.IO)`. There is no
`fallbackToDestructiveMigration`: a missing migration fails the first open instead of deleting the
user's history.

## Usage

Add `implementation(projects.core.database)` to the consuming module's `commonMain` dependencies.
`CoreDatabaseModule` is already in the `includes` of `AppModule`. Inject the DAO you need rather than
`ChatDatabase`:

```kotlin
@Single
class ConversationRepository(private val conversationDao: ConversationDao) {
    val conversations: Flow<List<ConversationEntity>> = conversationDao.observeAll()
}
```

## Adding a table

Every table, DAO and migration lives in this module, so each change is one commit here.

**1.** Declare the entity under `entities/`:

```kotlin
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updatedAt: Long,
)
```

**2.** Declare its DAO under `daos/`. Reads return `Flow`, writes are `suspend`, and both run on
`Dispatchers.IO`:

```kotlin
@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Upsert
    suspend fun upsert(conversation: ConversationEntity)
}
```

**3.** Add the entity to `ChatDatabase`, raise `DATABASE_VERSION` by one, add an `@AutoMigration`
from the previous version, and add the DAO getter:

```kotlin
@Database(
    entities = [
        PlaceholderEntity::class,
        ConversationEntity::class,
    ],
    version = DATABASE_VERSION, // now 2
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ],
    exportSchema = true,
)
@ConstructedBy(ChatDatabaseConstructor::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
}
```

Renaming or deleting a table or column needs an `AutoMigrationSpec` with `@RenameTable`,
`@RenameColumn`, `@DeleteTable` or `@DeleteColumn`. Room's compiler stops and says so when a change
is ambiguous.

**4.** Bind the DAO next to it, in `commonMain`, so `@ComponentScan` registers it:

```kotlin
@Single
internal fun conversationDao(database: ChatDatabase): ConversationDao = database.conversationDao()
```

**5.** Build once (any compile or test task) and commit the schema Room exports for the new version,
`schemas/br.com.weslleycampos.chat.core.database.ChatDatabase/2.json`, with the code.

> [!IMPORTANT]
> Never add a destructive migration fallback, and never edit or delete a schema JSON once it is
> committed. Room derives each auto-migration from the previous version's file, so a changed or
> missing one breaks every install still on that version. A change to entities without a new version
> rewrites the current file: if `git diff` shows a committed schema changing, raise the version
> instead.

To undo an uncommitted version bump, delete its JSON and run
`./gradlew :core:database:clean`, then build once with `--no-build-cache`. Room's KSP output keeps
schemas it wrote earlier, and the copy task would otherwise put the deleted file back.

## Tests

DAO tests go in `commonTest` and open an empty in-memory database with the app's driver and
dispatcher:

```kotlin
class ConversationDaoTest {
    private val database = inMemoryChatDatabase()
    private val dao = database.conversationDao()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun upsertedConversationIsObserved() = runTest {
        dao.upsert(ConversationEntity(id = "1", title = "Hello", updatedAt = 0))

        assertEquals(listOf("Hello"), dao.observeAll().first().map { it.title })
    }
}
```

`inMemoryChatDatabase()` is an `expect` function in `commonTest`, because Room declares
`Room.inMemoryDatabaseBuilder<ChatDatabase>()` per platform. Its actuals are in `androidHostTest`
and `iosTest`. The Android artifact of the bundled driver only carries `.so` files for devices, so
`chat.room` swaps in the desktop artifact on the host-test classpath.

Migration tests use Room's `MigrationTestHelper` in `iosTest`. It builds the old version from its
exported schema, runs the auto-migration, and checks the result against the new schema:

```kotlin
private val helper = MigrationTestHelper(
    schemaDirectoryPath = schemaDirectory,
    fileName = path,
    driver = BundledSQLiteDriver(),
    databaseClass = ChatDatabase::class,
    databaseFactory = ChatDatabaseConstructor::initialize,
)

@Test
fun conversationsTableIsAdded() = runTest {
    helper.createDatabase(version = 1).close()

    helper.runMigrationsAndValidate(version = 2)
}
```

`schemaDirectory` comes from `ROOM_SCHEMA_DIRECTORY`, which `chat.room` sets on every test task.
The Android host cannot run `MigrationTestHelper`: on Android it needs an `Instrumentation`, so it
only runs on a device. The auto-migration is generated from the same schema files on both
platforms, and Room checks the migrated schema on every open on Android too.

Run from the repository root:

```bash
./gradlew :core:database:testAndroidHostTest
```

```bash
./gradlew :core:database:iosSimulatorArm64Test
```

```bash
./gradlew :core:database:detekt
```

- `ChatDatabaseTest` (common) opens the in-memory database on the current version and queries its
  table.
- `ExportedSchemaTest` (common) checks that the schema for the current version is exported with
  the identity hash Room compiled.
- `MigrationTestHelperTest` (iOS) creates version 1 from its exported schema.
- `CoreDatabaseModuleTest` (iOS) gets the `ChatDatabase` singleton from `CoreDatabaseModule` and
  opens it on disk, then deletes the file.

Not covered by tests: the Android builder and its file location, since a host test has no
`Context`. The Koin compiler plugin does not catch a missing dependency of this module's
definitions when `:androidApp` compiles: a definition given a parameter nothing provides still
built. Detekt's `autoCorrect` is on, so the `detekt` task may rewrite sources.
