# Message actions

## Goal

The user taps a message to get its action row: copy it, fork the conversation there, have a reply
read aloud, or put a message of theirs back in the composer to retry it. A fork is a new
conversation that starts as a copy of the thread up to that message, keeps a banner saying where it
came from until its "×" is tapped, and lives on the device like any other conversation, in History
and "recent". The original conversation is never changed.

## Frames

Every line number refers to `.claude/screens/design/ctx Prototype.dc.html`; `frames.md` §2.2
("Banners", "Message"), §1.3 (state and timers), §2.9, §2.11 and §6 (quirks 3, 8) describe them.

| Frame | States | Design source |
| --- | --- | --- |
| `Chat` message selection | none, one message selected (a tap toggles it; a thinking or streaming reply has no row) | user bubble 158–160, assistant body 162; `select` / `showActions` 824–825 |
| `Chat` message action row | "copy" / "copied" (1200 ms), "⑂ fork here", "read aloud" (assistant only), "retry" (user only) | lines 208–215; `copyAll`, `fork`, `retry` 826–827; `msgText` 731; `copyText` 730 |
| `Chat` fork banner | hidden, "⑂ forked from “<title>” at message <n>" with "×" | line 117; `forkBanner`, `clearFork` 891; `fork` 723–728; cleared by `newChat` 890, `loadSeed` 680 |
| `Chat` read-aloud player | `reading` (shown under that reply), `readPlaying` ("❚❚") / paused or finished ("▶"), `readPct` 0–100, "0:12 / 0:41", "×" | lines 176–183; `startRead` 729; `reading` … `stopRead` 828–831 |
| `Toast` | "forked · new conversation" | lines 531–534; `toastMsg` in `fork` 727 |

## Acceptance criteria

Each criterion is one observable behaviour with at least one test that checks it. The data
criteria run on the Android host and the iOS simulator unless marked iOS.

### Database (`:core:database`, version 5)

- [x] Given a new in-memory database, when it opens, then it is on version 5, and
  `schemas/…ChatDatabase/5.json` records its identity hash —
  `ChatDatabaseTest.inMemoryDatabaseOpensOnTheCurrentVersion`,
  `ExportedSchemaTest.currentVersionIsExportedWithTheCompiledIdentityHash`
- [x] Given a version 4 database with conversations and messages, when it migrates, then version 5
  validates and keeps every row, each conversation no fork with its banner not dismissed (iOS) —
  `MigrationTestHelperTest.migratesVersionFourToFiveKeepingEveryConversationAsNoForkWithItsBannerNotDismissed`
- [x] A new conversation is no fork, and a fork's origin is stored with it —
  `ConversationDaoTest.aNewConversationIsNoForkAndAForksOriginIsStoredWithIt`
- [x] A thread up to a message is the conversation's messages up to and including it, in the
  order they were written, and nothing of another conversation —
  `ForkDaoTest.theMessagesThroughOneAreTheConversationsUpToAndIncludingItInTheOrderTheyWereWritten`
- [x] Copies take new ids in their order and keep every other column; they need an existing
  conversation — `ForkDaoTest.insertedCopiesTakeNewIdsInTheirOrderAndKeepEveryOtherColumn`,
  `ForkDaoTest.copiesNeedAnExistingConversation`
- [x] Dismissing the banner rewrites only its flag, leaves the order alone, and says whether it
  changed anything (not for a conversation that is no fork) —
  `ForkDaoTest.dismissingTheBannerRewritesOnlyItsFlagAndSaysWhetherItChangedAnything`
- [x] Koin provides `ForkDao` on the one database (iOS) —
  `CoreDatabaseModuleTest.koinProvidesTheDaosAndATransactionRunnerOnThatDatabase`

### Data (`:data:chat`)

- [x] A fork copies the thread up to the message, in order, with the same text, blocks, model,
  persona, status, latency and times, under new ids —
  `RoomForkRepositoryTest.aForkCopiesTheThreadUpToTheMessageInOrderWithTheSameContentUnderNewIds`
- [x] A fork is a new conversation on the source's title, model and persona, created and updated
  now, neither pinned nor archived whatever the source is —
  `RoomForkRepositoryTest.aForkIsANewConversationOnTheSourcesTitleModelAndPersonaCreatedNowNeitherPinnedNorArchived`
- [x] A fork records the source's title and the message's position counting from 1 over user
  messages and replies; a fork of a fork names the fork —
  `RoomForkRepositoryTest.aForkRecordsTheSourcesTitleAndThePositionOfTheMessageCountingFromOne`
- [x] The source is left as it was, rows and place in "recent" included —
  `RoomForkRepositoryTest.theSourceIsLeftAsItWas`
- [x] The banner reads the same after the source is deleted, and the fork keeps its thread —
  `RoomForkRepositoryTest.theBannerReadsTheSameAfterTheSourceIsDeletedAndTheForkKeepsItsThread`
- [x] An unknown conversation, or a message that is not one of its own, forks nothing and writes
  nothing — `RoomForkRepositoryTest.anUnknownConversationOrAMessageThatIsNotOneOfItsForksNothing`
- [x] A reply thinking or streaming is never copied: a fork at it is refused, a fork at the message
  before it works without it, and once the reply is done it forks —
  `RoomForkRepositoryTest.aReplyOnItsWayIsNeverCopiedButTheMessageBeforeItForksWithoutIt`
- [x] An interrupted reply is copied interrupted with what it revealed, and the fork accepts a send —
  `RoomForkRepositoryTest.anInterruptedReplyIsCopiedInterruptedWithWhatItRevealed`
- [x] A fork at a user message ends on it with no reply started; a send continues it with the next
  canned reply after the copied ones — `RoomForkRepositoryTest.aForkAtAUserMessageEndsOnItAndASendGoesOnWithTheNextCannedReply`
- [x] A fork tops History and "recent", counts, and the search finds its copied messages —
  `RoomForkRepositoryTest.aForkTopsHistoryAndRecentAndTheSearchFindsItsCopiedMessages`
- [x] Dismissing the banner is kept (a new repository on the same database reads it dismissed),
  does not move the conversation, and says whether it changed anything —
  `RoomForkRepositoryTest.dismissingTheBannerIsKeptAndSaysWhetherItChangedAnything`
- [x] A fork writes its conversation and its messages in one transaction —
  `RoomForkRepositoryTest.aForkWritesItsConversationAndItsMessagesInOneTransaction`
- [x] Rows read with their fork origin, and any other row as no fork —
  `EntityMappersTest.aForkedRowReadsWithItsOriginAndAnyOtherRowAsNoFork`
- [x] Koin provides one `RoomForkRepository` as `ForkRepository` —
  `DataChatModuleTest.koinProvidesOneRoomForkRepositoryOnTheDatabasesDaos`

### UI (`:feature:chat`)

<!-- PLACEHOLDER: the feature agent writes the UI criteria, previews table and UI contract here. -->

To be written by the feature agent: selection, the action row and its four actions ("copy" /
"copied", "⑂ fork here", "read aloud", "retry"), the fork banner and its "×", the read-aloud
player and its timer, the "forked · new conversation" toast, previews in light and dark, and the
tests that check each.

## Data contract

- **Module and package:** `:data:chat`, `br.com.weslleycampos.chat.data.chat` (models in `….models`).
- **Repository:** a new interface next to `ChatRepository`, which is unchanged apart from reading
  `Conversation.fork`:

  ```kotlin
  interface ForkRepository {
      suspend fun fork(conversationId: Long, messageId: Long): Long?
      suspend fun dismissForkBanner(conversationId: Long): Boolean
  }
  ```

- **Models:**
  - `data class ForkOrigin(sourceTitle: String, messageNumber: Int, bannerDismissed: Boolean = false)`
  - `Conversation(…, pinned, archived, fork: ForkOrigin? = null)`: `null` for every conversation
    that is not a fork; the default keeps earlier named-argument calls compiling.
- **Storage:** Room version 5 with `AutoMigration(from = 4, to = 5)`, schema `5.json`: on
  `conversations`, `forkedFromTitle TEXT` and `forkedAtMessage INTEGER` (nullable), and
  `forkBannerDismissed INTEGER NOT NULL DEFAULT 0`. New `ForkDao` (`messagesThrough(conversationId,
  messageId)`, `insertMessages(messages)`, `dismissBanner(id): Int`), bound in `CoreDatabaseModule`.
- **Koin binding:** `@Single internal fun forkRepository(conversationDao, forkDao, transactions):
  ForkRepository` → `RoomForkRepository` on `Clock.System`, in `ChatBindings.kt`. Inject
  `ForkRepository` only.
- **Behaviour:**
  - `fork(conversationId, messageId)` runs in one write transaction. It creates a conversation
    titled as the source, on the source's model and persona (its current choice), `createdAt` =
    `updatedAt` = now, not pinned or archived, with `forkedFromTitle` = the source's title and
    `forkedAtMessage` = the number of messages up to and including `messageId` (user messages and
    replies alike, from 1: the prototype's `i + 1`). It inserts copies of those messages in their
    order under new ids, each as stored: role, text, blocks, model, persona, status
    (`DONE` / `INTERRUPTED`), latency and `createdAt`. It returns the new id. The source is not
    changed, and nothing streams: a fork at a user message ends on it.
  - `fork` returns `null` and writes nothing when the conversation does not exist, when
    `messageId` is not one of its messages, or when a reply up to `messageId` is `THINKING` or
    `STREAMING` (only a conversation's last message can be, so that is a fork at the reply on its
    way; the message before it forks without it).
  - The fork is an ordinary conversation for `ChatRepository`: `observeConversation(id)` emits it
    with `fork`, `observeMessages(id)` its copies, it is first in `observeHistory()` and
    `observeRecentConversations()`, the search finds it by its copied messages, `send(id, …)`
    continues it (the canned replies rotate on from the copied replies), and `setPinned`,
    `archive` and `delete` work on it. Deleting or changing the source leaves the fork and its
    origin as they are.
  - `dismissForkBanner(id)` sets `forkBannerDismissed` and returns `true`; `false` and no write for
    an unknown id, a conversation that is no fork, or one already dismissed. `fork` keeps its
    origin with `bannerDismissed = true`, the next `observeConversation(id)` emission included,
    after a restart too. `updatedAt`, "recent" and History do not move.
- **What the feature builds on:**
  - "⑂ fork here" → `fork(conversationId, message.id)`; on an id, toast "forked · new
    conversation" and open the fork in Chat (`ChatEntry(newId)`, as History opens a row); on
    `null`, nothing to fork (the row is never offered on a thinking or streaming reply, as the
    prototype's `showActions`).
  - The fork banner shows while `conversation.fork != null && !fork.bannerDismissed`: "⑂ " +
    `"forked from “${fork.sourceTitle}” at message ${fork.messageNumber}"`. Titles are never blank
    here (a send needs text), so the prototype's "untitled" fallback does not show; a feature may
    keep it for safety. "×" → `dismissForkBanner(conversationId)`.
  - "retry", "copy" and "read aloud" need no data call (see Decided).

## Decided

- Decided: forking lives in a new `ForkRepository` (`RoomForkRepository`) instead of
  `ChatRepository`, which already has ten functions: two more would pass detekt's
  `TooManyFunctions` limit (11) on the interface and on `RoomChatRepository`. A fork needs none of
  `ChatRepository`'s stream, recovery or personas, only DAOs, a transaction and a clock. Its queries
  are a new `ForkDao`, as History's are `HistoryDao`, keeping `ConversationDao` and `MessageDao`
  under the same limit.
- Decided: a fork is one write transaction and takes no lock with sends. It copies only messages
  up to the one it is given, which a later send never changes (sends append), and it refuses a
  reply still being written; SQLite runs write transactions one at a time, so a delete either lands
  before (the fork returns `null`) or after (the fork keeps its copies).
- Decided: a reply thinking or streaming is never copied: `fork` returns `null` when one is in the
  copied thread. The prototype never offers the action row on such a reply (`showActions` needs
  `!streaming && !thinking`), and a copy could not stream on (no stream drives it) nor be honestly
  marked interrupted while the original is still going. A fork at the user message before it works,
  without the reply, as the prototype keeps `messages 0..i`.
- Decided: a fork at a user message ends on that message and starts no reply, as the prototype's
  `fork`; the next send follows it, so that thread has two user messages in a row. Replies keep
  their status as stored, so an interrupted reply is copied interrupted with what it revealed.
- Decided: the fork's title is the source's title. The app titles a conversation after its first
  message, and the fork's first message is the source's, so the title rule gives the same result;
  the prototype's `convTitle:null` only meant "titled by the next send", and here a fork is listed in
  History at once and needs a title then. The banner tells the two apart.
- Decided: the origin is stored on the fork (`forkedFromTitle`, `forkedAtMessage`) as a copy, not a
  live join or a foreign key, so the banner reads the same after the source is deleted, and a fork
  outlives its source with its own messages. No `forkedFromId` is kept: nothing in the design opens
  the source from a fork.
- Decided: the message number counts user messages and replies alike, from 1, the prototype's
  `i + 1` over its `messages` array. A compare pair (row 7) will count as one message, as the
  prototype's pair is one entry.
- Decided: the fork answers with the source conversation's current model and persona (its
  `choice`), like a send continuing it; each copied reply keeps the model and persona it was written
  with.
- Decided: copies keep their `createdAt`, so their times read as in the source; the fork itself is
  created and updated now, so it is first in History and "recent". It is neither pinned nor
  archived even when the source is; an archived source still forks, since it still opens by id.
- Decided: forks appear in History and "recent" like any conversation, fixing frames quirk 8
  ("forks do not create History items"): a fork that vanished on leaving it would lose the user's
  work.
- Decided: the banner's dismissal is persisted per conversation (`forkBannerDismissed`), so "×"
  holds when the fork is opened again from History or after a restart; the origin itself is kept
  for later rows (export, row 10). Dismissing does not move `updatedAt`. The banner stays through
  sends in the fork, as the prototype's `forkFrom` does. The prototype clears `forkFrom` in
  `newChat` and `loadSeed`: a new chat has no conversation and so no banner here, and opening a
  fork shows its banner until it is dismissed (`loadSeed` never loaded a fork).
- Decided: "retry" is UI-only, as the prototype: it puts the user message's text in the composer
  and clears the selection; it never resends or regenerates (frames §2.11).
- Decided: "read aloud" is UI-only, as the prototype: no speech engine, a timer of 100 ms ticks
  adding 0.6 % (about 16.7 s to 100 %), paused ticks skipped, stopping at 100 % with "▶" and the
  player left until "×"; the label is `round(41 × readPct / 100)` seconds of a fixed "0:41" (frames
  quirk 3). Starting a read clears the selection. Nothing is stored; "Read replies aloud" (auto
  read) is Settings, row 10.
- Decided: "copy" is UI-only: the user's text, or a reply's blocks joined by blank lines with list
  items as "1. text" (the prototype's `msgText`), through the clipboard as row 3's code "copy",
  reading "copied" for 1200 ms.
- Decided: no new index: `messagesThrough` reads by the `messages(conversationId)` index and the
  primary key.

## Out of scope

- The UI of this row (selection, action row, banner, player, toast): the feature agent, in the
  placeholder above.
- Real text-to-speech and voices: there is no speech engine; the player is a timer. Auto read:
  row 10.
- Regenerating a reply, resending on "retry", renaming a fork: the prototype has none (§2.11).
- Forking a compare pair: pair messages have no action row (row 7).
- Copying attachments into a fork: row 8 adds attachments and copies them with their message.
- Opening the source conversation from the banner: the design has no such link.
