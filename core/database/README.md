# Database

`:core:database` owns `ChatDatabase`, the app's single [Room](https://developer.android.com/kotlin/multiplatform/room)
database for Android and iOS. Conversations, messages and everything else Chat keeps are tables
here: they survive restarts and never leave the device.

| Type | Package | Role |
| --- | --- | --- |
| `ChatDatabase` | `core.database` | The `@Database`: its entities, version, auto-migrations, column converters and one getter per DAO |
| `DATABASE_VERSION` | `core.database` | The schema version, `2`. Tests compare against it, so they need no edit when it rises |
| `ChatDatabaseConstructor` | `core.database` | The `expect object` that `@ConstructedBy` names; Room's compiler writes the `actual` on each target |
| `CoreDatabaseModule` | `core.database` | The Koin module: the `ChatDatabase` singleton, every DAO and the `TransactionRunner` |
| `TransactionRunner` | `core.database` | Runs DAO calls from more than one DAO as one write transaction |
| `ChatTypeConverters` | `core.database` | Stores enums as fixed lowercase values and reply blocks as a JSON array |
| `PlaceholderEntity` | `core.database.entities` | Version 1's only table, `placeholder`. It stays empty and is never dropped |
| `ConversationEntity` | `core.database.entities` | Version 2, `conversations`: one chat thread, its title, model and timestamps |
| `MessageEntity` | `core.database.entities` | Version 2, `messages`: a user message or a reply, its blocks, status and latency |
| `StoredBlock` | `core.database.entities` | One reply block in the `blocks` column: paragraph, code or numbered list |
| `ConversationDao`, `MessageDao` | `core.database.daos` | The queries on those two tables |

## Versions

| Version | Added | Migration |
| --- | --- | --- |
| 1 | `placeholder` | — |
| 2 | `conversations`, `messages` (foreign key to `conversations`, cascade delete, index on `conversationId`) | `AutoMigration(from = 1, to = 2)` |

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
`ChatDatabase`, and `TransactionRunner` when one change writes through more than one DAO:

```kotlin
class ConversationStarter(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val transactions: TransactionRunner,
) {
    val recent: Flow<List<ConversationEntity>> = conversationDao.observeRecent(limit = 3)

    suspend fun start(conversation: ConversationEntity, first: MessageEntity) = transactions.inWriteTransaction {
        val id = conversationDao.insert(conversation)
        messageDao.insert(first.copy(conversationId = id))
    }
}
```

`:data:chat`'s `RoomChatRepository` is the real consumer.

## Adding a table

Every table, DAO and migration lives in this module, so each change is one commit here.

**1.** Declare the entity under `entities/`. A column added to an existing table needs a
`@ColumnInfo(defaultValue = …)` so the auto-migration can fill the rows already there:

```kotlin
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val updatedAt: Long,
)
```

**2.** Declare its DAO under `daos/`. Reads return `Flow`, writes are `suspend`, and both run on
`Dispatchers.IO`:

```kotlin
@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Upsert
    suspend fun upsert(note: NoteEntity)
}
```

A column type SQLite has no affinity for needs a converter in `ChatTypeConverters`. Room 3 names
them `@ColumnTypeConverter` and `@ColumnTypeConverters`; KSP reports an unresolved
`@TypeConverter` only as "references a type that is not present".

**3.** Add the entity to `ChatDatabase`, raise `DATABASE_VERSION` by one, add an `@AutoMigration`
from the previous version, and add the DAO getter:

```kotlin
@Database(
    entities = [
        PlaceholderEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        NoteEntity::class,
    ],
    version = DATABASE_VERSION, // now 3
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
    exportSchema = true,
)
@ConstructedBy(ChatDatabaseConstructor::class)
@ColumnTypeConverters(ChatTypeConverters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao

    abstract fun messageDao(): MessageDao

    abstract fun noteDao(): NoteDao
}
```

Renaming or deleting a table or column needs an `AutoMigrationSpec` with `@RenameTable`,
`@RenameColumn`, `@DeleteTable` or `@DeleteColumn`. Room's compiler stops and says so when a change
is ambiguous.

**4.** Bind the DAO in `DatabaseBindings.kt`, in `commonMain`, so `@ComponentScan` registers it:

```kotlin
@Single
internal fun noteDao(database: ChatDatabase): NoteDao = database.noteDao()
```

**5.** Build once (any compile or test task) and commit the schema Room exports for the new version,
`schemas/br.com.weslleycampos.chat.core.database.ChatDatabase/3.json`, with the code.

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
class NoteDaoTest {
    private val database = inMemoryChatDatabase()
    private val dao = database.noteDao()

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun upsertedNoteIsObserved() = runTest {
        dao.upsert(NoteEntity(text = "Hello", updatedAt = 0))

        assertEquals(listOf("Hello"), dao.observeAll().first().map { it.text })
    }
}
```

`inMemoryChatDatabase()` is an `expect` function in `commonTest`, because Room declares
`Room.inMemoryDatabaseBuilder<ChatDatabase>()` per platform. Its actuals are in `androidHostTest`
and `iosTest`. The Android artifact of the bundled driver only carries `.so` files for devices, so
`chat.room` swaps in the desktop artifact on the host-test classpath.

The JSON column uses `kotlinx-serialization-json` through its BOM. Room's migration code, which
`MigrationTestHelper` reads schemas with, depends on an older `kotlinx-serialization-json-okio`
that fails to link against a newer JSON runtime; the BOM keeps the whole family on one version.

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
fun notesTableIsAdded() = runTest {
    helper.createDatabase(version = 2).close()

    helper.runMigrationsAndValidate(version = 3)
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
  version 1 table.
- `ExportedSchemaTest` (common) checks that the schema for the current version is exported with
  the identity hash Room compiled.
- `ConversationDaoTest` and `MessageDaoTest` (common) cover every query: observing by id, "recent"
  order and limit, message order, the blocks JSON, reply progress, reply counts, unfinished and
  interrupted replies, the foreign key and its cascade delete.
- `BlocksJsonTest` (common) covers the empty array and skipping a block type a version does not know.
- `TransactionRunnerTest` (common) commits writes from two DAOs together and rolls them back together.
- `MigrationTestHelperTest` (iOS) creates version 1 from its exported schema and migrates it to 2.
- `CoreDatabaseModuleTest` (iOS) gets the `ChatDatabase` singleton from `CoreDatabaseModule` and
  opens it on disk, then deletes the file; it also gets the DAOs and the `TransactionRunner`.

Not covered by tests: the Android builder and its file location, since a host test has no
`Context`. The Koin compiler plugin does not catch a missing dependency of this module's
definitions when `:androidApp` compiles: a definition given a parameter nothing provides still
built. Detekt's `autoCorrect` is on, so the `detekt` task may rewrite sources.
