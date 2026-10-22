# Chat thread

## Goal

After sign-in the user lands in a chat: they type a message, see a reply think, stream and finish
with its latency, and keep the conversation on the device, where it survives restarts. There is no
model provider, so every model answers with the design prototype's canned replies, streamed on a
timer by the data layer and saved to Room like any other message.

## Frames

Every line number refers to `.claude/screens/design/ctx Prototype.dc.html`; `frames.md` §2.2, §2.9
and §3 describe them.

| Frame | States | Design source |
| --- | --- | --- |
| `Chat` top bar | history button, model chip (full model name), new chat | lines 107–115; `chipLabel` 888, `newChat` 890 |
| `Chat` empty state | "new chat", model name and meta line, tips, "recent" (first three) | lines 120–140; `recentConvs` 892 |
| `Chat` message | user bubble; assistant `thinking`, streaming (caret), done with `lat`; blocks `p`, `code` (header with "copy" / "copied"), `list`; syntax highlighting | lines 142–184; meta 144–148; `tokenize` 610–624 |
| `Chat` composer | draft, send enabled / disabled while streaming | lines 220–249; input row 241–248 |
| Sending and streaming | think delay, 28 ms tick, block reveal, `lat`, `REPLIES` rotation | `send` 683–695, `startStream` 696–722, `partialBlock` / `blockLen` 625–629, `REPLIES` 588–599 |
| `Toast` | shown, hidden after 1400 ms | lines 531–534; `toastMsg` 660 |
| Model catalogue | the eight `MODELS` | lines 542–551 |

## Acceptance criteria

Each criterion is one observable behaviour with at least one test that checks it. The data
criteria below run on the Android host and the iOS simulator unless marked iOS.

### Database (`:core:database`, version 2)

- [x] Given a new in-memory database, when it opens, then it is on version 2 —
  `ChatDatabaseTest.inMemoryDatabaseOpensOnTheCurrentVersion`
- [x] Given the compiled database, then `schemas/…ChatDatabase/2.json` records its identity hash —
  `ExportedSchemaTest.currentVersionIsExportedWithTheCompiledIdentityHash`
- [x] Given a version 1 database from the committed schema, when it migrates, then version 2 validates and
  has `conversations` and `messages` (iOS) —
  `MigrationTestHelperTest.migratesVersionOneToTwoAddingConversationsAndMessages`
- [x] Given an inserted conversation, then it is observed by the id SQLite assigned, and an unknown id
  reads `null` — `ConversationDaoTest.anInsertedConversationIsObservedByTheIdSqliteAssigned`
- [x] Given several conversations, then "recent" is the most recently updated first, later id first
  on a tie, up to the limit — `ConversationDaoTest.recentConversationsAreTheMostRecentlyUpdatedFirstUpToTheLimit`,
  `ConversationDaoTest.settingUpdatedAtMovesAConversationToTheTopOfRecent`
- [x] Given messages written out of timestamp order, then a conversation's messages read in the order
  they were written — `MessageDaoTest.messagesAreObservedInTheOrderTheyWereWrittenWhateverTheirTimestamps`
- [x] Given a reply with `p`, `code` and `list` blocks, then they round-trip, and the column holds the
  prototype's JSON shape — `MessageDaoTest.replyBlocksRoundTripThroughTheirJsonColumn`,
  `MessageDaoTest.theBlocksColumnHoldsTheDesignPrototypesJsonShape`, `BlocksJsonTest.*`
- [x] Given a reply, when its progress is written, then only blocks, status and latency change —
  `MessageDaoTest.updatingAReplyRewritesOnlyItsBlocksStatusAndLatency`
- [x] Replies are counted per conversation, a reply is unfinished while thinking or streaming, and
  interrupting keeps the revealed blocks — `MessageDaoTest.repliesAreCountedPerConversation`,
  `MessageDaoTest.aReplyIsUnfinishedWhileItThinksOrStreams`,
  `MessageDaoTest.interruptingUnfinishedRepliesKeepsWhatTheyRevealed`,
  `MessageDaoTest.interruptingOneReplyLeavesTheOthersAndAFinishedOneAlone`
- [x] A message needs an existing conversation, and deleting a conversation deletes its messages —
  `MessageDaoTest.aMessageNeedsAnExistingConversation`, `MessageDaoTest.deletingAConversationDeletesItsMessages`
- [x] Writes across DAOs in one `TransactionRunner` block commit together or not at all —
  `TransactionRunnerTest.writesAcrossDaosCommitTogether`, `TransactionRunnerTest.aBlockThatThrowsLeavesNoWriteBehind`
- [x] Koin provides the DAOs and a transaction runner on the one database (iOS) —
  `CoreDatabaseModuleTest.koinProvidesTheDaosAndATransactionRunnerOnThatDatabase`

### Data (`:data:chat`)

- [x] The catalogue is the prototype's eight models with their exact values, the default is
  `sonnet`, and an unknown id reads as the default — `ModelCatalogueTest.*`
- [x] A title is the first message on one line, cut to 40 characters, never splitting a character —
  `ConversationTitlesTest.*`
- [x] The canned replies are the prototype's three `REPLIES` to the character, and a conversation's
  replies rotate through them — `CannedRepliesTest.*`
- [x] Text grows by 2–7 and code by 6–15 characters a tick; finished blocks stay whole and the next
  block appears on the following tick; list items appear one after another — `BlockRevealTest.*`
- [x] A reply thinks for 320–619 ms, streams one tick every 28 ms, and ends done with the streaming
  time as its latency —
  `ReplyStreamerTest.aReplyThinksForThreeHundredTwentyMillisecondsAtLeastThenStreamsOneTickEveryTwentyEight`,
  `ReplyStreamerTest.theThinkingDelayIsAtMostSixHundredNineteenMilliseconds`,
  `ReplyStreamerTest.aFinishedReplyIsDoneWithTheTimeItStreamedForAsItsLatency`,
  `ReplyStreamerTest.blocksAreRevealedInOrderAndOnlyEverGrow`
- [x] A stream that is cancelled (thinking or streaming) or whose write fails leaves the reply
  interrupted with what it revealed — `ReplyStreamerTest.aStreamCancelledMidwayLeavesTheReplyInterruptedWithWhatItRevealed`,
  `ReplyStreamerTest.aStreamCancelledWhileThinkingLeavesTheReplyInterruptedAndEmpty`,
  `ReplyStreamerTest.aStreamWhoseWriteFailsLeavesTheReplyInterruptedRatherThanStreaming`,
  `RoomChatRepositoryTest.aReplyWhoseStreamIsCancelledIsLeftInterruptedWithWhatItRevealed`
- [x] Given no conversation, when the user sends, then a conversation titled after the message is
  created on the default model — `RoomChatRepositoryTest.aFirstSendCreatesAConversationTitledAfterTheMessageOnTheDefaultModel`
- [x] When the user sends, then the trimmed message and a thinking reply are saved before `send`
  returns — `RoomChatRepositoryTest.sendSavesTheTrimmedMessageAndAThinkingReplyBeforeItReturns`
- [x] A reply goes thinking → streaming → done, and every tick that changes it reaches observers —
  `RoomChatRepositoryTest.aReplyGoesFromThinkingThroughStreamingToDoneAndEveryTickReachesObservers`
- [x] A conversation's replies rotate `REPLIES[0]`, `[1]`, `[2]`, `[0]`; a new conversation starts at
  `[0]` — `RoomChatRepositoryTest.aConversationsRepliesRotateThroughTheThreeCannedRepliesAndANewConversationStartsOver`
- [x] Replies never overlap in a conversation: a send while its reply thinks or streams sends
  nothing — `RoomChatRepositoryTest.aSendWhileTheLastReplyThinksOrStreamsSendsNothing`
- [x] Blank text or an unknown conversation sends nothing —
  `RoomChatRepositoryTest.blankTextOrAConversationThatDoesNotExistSendsNothing`
- [x] "Recent" is the three last-updated conversations, newest first, and a send moves one up —
  `RoomChatRepositoryTest.recentConversationsAreTheThreeLastUpdatedNewestFirstAndASendMovesOneUp`
- [x] A reply still playing when the app was closed reads as interrupted on the next start, and the
  conversation accepts a new message —
  `RoomChatRepositoryTest.aReplyStillPlayingWhenTheAppWasClosedIsInterruptedWhenTheRepositoryStarts`
- [x] After "new chat", the previous reply keeps streaming into its conversation while the new chat
  sends — `RoomChatRepositoryTest.aReplyKeepsStreamingIntoItsConversationWhileANewChatSends`
- [x] A conversation's observers do not emit while another conversation streams —
  `RoomChatRepositoryTest.aConversationsObserversStayQuietWhileAnotherConversationStreams`
- [x] A send writes its conversation and both messages in one transaction —
  `RoomChatRepositoryTest.aSendWritesItsConversationAndBothMessagesInOneTransaction`
- [x] Rows map to domain models and back — `EntityMappersTest.*`
- [x] Koin provides one `RoomChatRepository` as `ChatRepository` —
  `DataChatModuleTest.koinProvidesOneRoomChatRepositoryOnTheDatabasesDaos`

### UI (`:feature:chat`, `:core:ui`, `:core:navigation`, `:app`) — for the feature agent

<!-- The feature agent writes the UI criteria here: top bar, empty state and recent list, messages
     and blocks, caret, latency meta, syntax highlighting, code "copy" / "copied", composer, new chat,
     toast, ChatEntry replacing HomeEntry, starter code removed. -->

- [ ] _To be written by the feature agent._

## Data contract

- **Module and package:** `:data:chat`, `br.com.weslleycampos.chat.data.chat` (models in `….models`).
  Depends on `:core:database`. `DataChatModule` is in `AppModule`'s `includes`.
- **Repository:**

  ```kotlin
  interface ChatRepository {
      fun observeConversation(id: Long): Flow<Conversation?>
      fun observeMessages(conversationId: Long): Flow<List<Message>>
      fun observeRecentConversations(): Flow<List<Conversation>>
      suspend fun send(conversationId: Long?, text: String): Long?

      companion object { const val RECENT_CONVERSATIONS = 3 }
  }
  ```

- **Models:**
  - `Conversation(id: Long, title: String, model: ChatModel, createdAt: Instant, updatedAt: Instant)`
  - `sealed interface Message { id: Long; conversationId: Long; createdAt: Instant }`, with
    `Message.User(…, text: String)` and
    `Message.Assistant(…, model: ChatModel, blocks: List<Block>, status: ReplyStatus, latency: Duration?)`
  - `enum class ReplyStatus { THINKING, STREAMING, DONE, INTERRUPTED }` with `isInProgress`
    (thinking or streaming)
  - `sealed interface Block`: `Paragraph(text)`, `Code(language, text)`, `NumberedList(items: List<String>)`
  - `ChatModel(id, name, shortName, provider, contextWindow, cost, hue: Int)` and
    `object ModelCatalogue { DEFAULT_MODEL_ID = "sonnet"; models; default; find(id); get(id) }`.
    `shortName` is the prototype's `short` and `contextWindow` its `ctx`.
  - `Instant` and `Clock` are `kotlin.time`'s, stable since Kotlin 2.3.
- **Storage:** version 2 with `AutoMigration(from = 1, to = 2)`, schema `2.json`.
  - `conversations`: `id` (autoincrement), `title`, `modelId`, `createdAt`, `updatedAt` (epoch ms).
  - `messages`: `id` (autoincrement), `conversationId` (foreign key, cascade delete, indexed),
    `role` (`user` / `assistant`), `text` (user only), `blocks` (JSON array, assistant only),
    `modelId` (assistant only), `status` (`thinking` / `streaming` / `done` / `interrupted`),
    `latencyMillis`, `createdAt`.
  - DAOs `ConversationDao` and `MessageDao`; `TransactionRunner` for writes across DAOs. All bound
    in `CoreDatabaseModule`.
- **Koin binding:** `@Single internal fun chatRepository(conversationDao, messageDao, transactions): ChatRepository`
  in `ChatBindings.kt`, a `RoomChatRepository` with `Clock.System`, `Random.Default` and a
  process-long `CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
- **Behaviour:**
  - Reads emit the current value on collection and again on every change that alters it, never the
    same value twice in a row. Messages are oldest first, each user message followed by its reply.
  - `send` trims the text. With a `null` conversation id it creates the conversation (title rule
    below, model `sonnet`). It saves the user message and a `THINKING` reply in one transaction,
    starts the stream, and returns the conversation id at once. It returns `null` and writes
    nothing for blank text, an unknown conversation, or a conversation whose reply is in progress.
  - A reply thinks 320–619 ms, then every 28 ms reveals 2–7 characters of text or 6–15 of code,
    writing each tick, and ends `DONE` with `latency` = the time from the end of thinking to the
    last tick (the prototype's `lat`, shown as `"%.1fs"`).
  - "New chat" needs nothing from data: the UI drops its conversation id, shows the empty state with
    `ModelCatalogue.default` and `observeRecentConversations()`, and the next `send(null, …)`
    creates the conversation. A reply still streaming elsewhere finishes into its conversation.
  - On the first injection, every reply left thinking or streaming by an earlier run becomes
    `INTERRUPTED`, keeping its blocks; reads and sends wait for that.

## Decided

- Decided: the reply's blocks live in one `messages.blocks` TEXT column, a JSON array in the
  prototype's own shape (`{"type":"p","text":…}`, `{"type":"code","lang":…,"text":…}`,
  `{"type":"list","items":[…]}`), through a `@ColumnTypeConverter` (Room 3's name for
  `@TypeConverter`). A block type a version does not know is skipped, so a later row can add one.
  A separate blocks table would need a join and one write per block on every tick.
- Decided: `kotlinx-serialization-json` 1.12.0, the latest stable, built for Kotlin 2.4.10+, through
  its BOM. Room 3.0.3's migration code pulls `kotlinx-serialization-json-okio` 1.8.1, which failed to
  link against json 1.12.0 in `MigrationTestHelper` on iOS; the BOM aligns it.
- Decided: `role` and `status` are stored as lowercase values (`user`, `thinking`, …) through
  converters, not as enum names, so renaming a Kotlin constant cannot break stored rows, and the
  DAO's SQL can name them.
- Decided: ids are SQLite autoincrement `Long`s, and messages read in id order: SQLite never reuses
  or reorders them, so the order is stable even when two messages share a timestamp or the clock
  moves back.
- Decided: a conversation exists only once its first message is sent. "New chat" writes nothing, so
  an abandoned new chat leaves no empty conversation in "recent" or History.
- Decided: the title is the first message with every run of whitespace (line breaks included) made
  one space, trimmed, cut to the prototype's 40 characters without splitting a surrogate pair, then
  trimmed again. History and "recent" show it on one line, as the prototype's text does.
- Decided: a conversation keeps the model it was created on (`sonnet`) until row 4 lets the user
  change it; each reply records the model that wrote it.
- Decided: the `REPLIES` rotation uses the number of replies already in the conversation (interrupted
  ones included) instead of the prototype's in-memory `sentCount`, so it survives restarts and each
  conversation starts at `REPLIES[0]`.
- Decided: thinking lasts `320 + nextInt(300)` ms, 320–619 whole milliseconds, for the prototype's
  `320 + Math.random() * 300`.
- Decided: every 28 ms tick is written, as one UPDATE of one row, so the reply grows at the
  prototype's pace. Reads apply `distinctUntilChanged`, so a tick that shows nothing new (a list's
  trailing break) and writes in other conversations do not emit.
- Decided: the latency is stored in milliseconds and exposed as a `Duration`; the UI formats it as
  the prototype does, `(ms / 1000).toFixed(1) + "s"`.
- Decided: replies never overlap within a conversation; `send` refuses while one is in progress, as
  the prototype's `streaming` flag does. Conversations are independent: the prototype's `newChat`
  cancels the stream, but here the conversation is already saved, so its reply finishes into it.
- Decided: a reply that was thinking or streaming when the app was killed is marked `INTERRUPTED`
  on the next start with the blocks it revealed, and never resumes. A stream that is cancelled or
  fails is marked the same way; the stream scope's exception handler then drops the failure so a
  canned reply cannot crash the app.
- Decided: `send` returns `Long?` (the conversation id, or `null` when nothing was sent) instead of
  throwing, since a double tap can race the disabled send button.
- Decided: `updatedAt` moves on each send, not when the reply finishes; "recent" sorts on it, newest
  first, later id first on a tie.
- Decided: `TransactionRunner` in `:core:database` (`withWriteTransaction` on the database) runs a
  send's writes across both DAOs atomically, and the repository serialises sends with a `Mutex`.
- Decided: time, randomness and the stream scope are constructor parameters of
  `RoomChatRepository` and `ReplyStreamer`, so tests run on `runTest`'s virtual time with a fixed
  random source.
- Decided: `DataChatModuleTest` loads `DataChatModule` next to an annotated test module of fakes.
  The Koin compiler plugin rejects lookups whose dependencies come from a classic `module { }` or a
  later `loadModules`, and `CoreDatabaseModule` needs a `Context` on the Android host.

## Out of scope

- Personas, favorites, the model picker and a default-model preference: rows 4 and 10.
- Pinned, archived, folders, search, delete and the full History list: row 5.
- Fork, retry and read-aloud: row 6. Compare pairs and `REPLY_ALT`: row 7. Attachments: row 8.
  Memory and the remember flag: row 9. `autoRead`: row 10.
- The prototype's `SEED` / `loadSeed` demo conversation: no demo data is seeded (queue decision).
- Stopping a reply: the design has no stop control.
- Syntax highlighting (`tokenize`), "copy" / "copied", the caret and the toast are UI work for
  `:feature:chat` / `:core:ui`.
