# History

## Goal

The user opens History from the chat and finds any conversation kept on the device: pinned ones
first, then today's, yesterday's and earlier ones, newest first. They search titles and their own
messages, swipe a row to pin, archive or delete it, and tap a row to reopen the conversation. Pins,
archives and deletes stay on the device and survive restarts; nothing is synced.

## Frames

Every line number refers to `.claude/screens/design/ctx Prototype.dc.html`; `frames.md` §2.3, §3
("Conversation / HistoryItem") and §6 (quirks 5, 6, 8) describe them.

| Frame | States | Design source |
| --- | --- | --- |
| `History` bar | back chevron, title "History", Memory, Settings | lines 256–261 |
| `History` status row | `synced` / `syncing` / `offline` / `local` in the prototype; replaced, see Decided | lines 262–264; `syncMap` 846–847 |
| `History` search | empty, typed with "×" clear, filtered, no matches (`no matches for “<query>”`) | lines 265–271, 297; filter 833–834; bindings 902–903 |
| `History` groups | "pinned", "today", "yesterday", "earlier"; an empty group is skipped | lines 272–275; `groupsOrder` 835–836, `hGroups` 845 |
| `History` row | "▲" when pinned, title, model dot and short name, folder chip, extra, time; swipe closed / dragging / open; "pin" / "unpin", "archive", "delete" | lines 276–294; `hRow` 837–844; `swipeMove` / `swipeEnd` 795–797 |
| `History` footer | "swipe left on a row to pin, archive or delete" | line 298 |
| History data | `HISTORY` (ten seed items: title, model, folder, time, day, pinned, extra) | lines 557–568 |

## Acceptance criteria

Each criterion is one observable behaviour with at least one test that checks it. The data
criteria run on the Android host and the iOS simulator unless marked iOS.

### Database (`:core:database`, version 4)

- [x] Given a new in-memory database, when it opens, then it is on version 4, and
  `schemas/…ChatDatabase/4.json` records its identity hash —
  `ChatDatabaseTest.inMemoryDatabaseOpensOnTheCurrentVersion`,
  `ExportedSchemaTest.currentVersionIsExportedWithTheCompiledIdentityHash`
- [x] Given a version 3 database with conversations and messages, when it migrates, then version 4
  validates and keeps every row, each conversation neither pinned nor archived (iOS) —
  `MigrationTestHelperTest.migratesVersionThreeToFourKeepingEveryConversationNeitherPinnedNorArchived`
- [x] A new conversation is neither pinned nor archived, and "recent" leaves archived ones out —
  `ConversationDaoTest.aNewConversationIsNeitherPinnedNorArchived`,
  `ConversationDaoTest.recentConversationsLeaveTheArchivedOnesOut`
- [x] History is every conversation that is not archived, newest first, the later id on a tie,
  pinned ones in that order too, and its count is theirs —
  `HistoryDaoTest.historyIsEveryConversationThatIsNotArchivedNewestFirstTheLaterIdWinningATieAndCountsThem`
- [x] The search matches titles and user messages ignoring ASCII case, never replies, matches the
  models it is given, and takes `%`, `_` and `\` literally —
  `HistoryDaoTest.searchMatchesTitlesAndTheUsersMessagesIgnoringTheCaseOfAsciiLetters`,
  `HistoryDaoTest.searchDoesNotLookInReplies`, `HistoryDaoTest.searchMatchesTheConversationsOnTheModelsItIsGiven`,
  `HistoryDaoTest.searchTreatsPercentUnderscoreAndBackslashAsThemselves`, `LikePatternsTest.*`
- [x] Pinning rewrites only `pinned` and leaves the order alone; archiving hides a conversation from
  every list but keeps it and its messages; deleting removes it and its messages; each says
  whether it changed anything — `ConversationDaoTest.pinningRewritesOnlyPinnedLeavesTheOrderAloneAndSaysWhetherItChangedAnything`,
  `ConversationDaoTest.archivingHidesAConversationFromEveryListKeepsItAndItsMessagesAndSaysWhetherItChangedAnything`,
  `ConversationDaoTest.deletingAConversationDeletesItAndItsMessagesAndSaysWhetherItDeletedAnything`
- [x] A reply's progress write says whether the reply is still there —
  `MessageDaoTest.updatingAReplySaysWhetherTheReplyIsStillThere`
- [x] Koin provides `HistoryDao` on the one database (iOS) —
  `CoreDatabaseModuleTest.koinProvidesTheDaosAndATransactionRunnerOnThatDatabase`

### Data (`:data:chat`)

Grouping

- [x] "pinned" comes first whatever its day, then "today", "yesterday" and "earlier" —
  `HistoryGroupsTest.pinnedComesFirstWhateverItsDayThenTodayYesterdayAndEarlier`
- [x] An empty group is left out, and no conversations are no groups —
  `HistoryGroupsTest.aGroupWithNoConversationIsLeftOutAndNoConversationsAreNoGroups`
- [x] Inside a group the most recently updated comes first, the later id on a tie, whatever the
  input order — `HistoryGroupsTest.insideAGroupTheMostRecentlyUpdatedComesFirstAndTheLaterIdOnATie`
- [x] The day is the local date of `updatedAt` in the given time zone (UTC, São Paulo, Tokyo) —
  `HistoryGroupsTest.theDayIsTheLocalDateOfTheLastMessageInTheGivenTimeZone`
- [x] At local midnight "today" becomes "yesterday", and a midnight later "earlier"; the day before
  is found across a month, a year and a daylight-saving change —
  `HistoryGroupsTest.atLocalMidnightTodayBecomesYesterdayAndAMidnightLaterEarlier`,
  `HistoryGroupsTest.yesterdayIsTheDayBeforeAcrossTheEndOfAMonthAndOfAYear`,
  `HistoryGroupsTest.theDayTheClocksGoBackIsStillOneDay`
- [x] A conversation updated after "now" (the clock moved back) counts as today —
  `HistoryGroupsTest.aConversationUpdatedAfterNowCountsAsToday`

The list and its search

- [x] History is every conversation that is not archived, most recently updated first; a send
  moves one to the top — `RoomChatRepositoryHistoryTest.historyIsEveryConversationThatIsNotArchivedTheMostRecentlyUpdatedFirst`
- [x] A blank query lists everything and a query is trimmed —
  `RoomChatRepositoryHistoryTest.aBlankQueryListsEverythingAndAQueryIsTrimmed`,
  `HistorySearchTest.theQueryIsTrimmedAndABlankOneListsEverything`
- [x] A query matches titles, the user's own messages and the model's short name, whatever their
  case, and never the canned replies —
  `RoomChatRepositoryHistoryTest.aQueryMatchesTitlesTheUsersOwnMessagesAndTheModelsShortNameWhateverTheirCase`,
  `RoomChatRepositoryHistoryTest.aQueryDoesNotSearchTheCannedReplies`,
  `HistorySearchTest.aModelMatchesWhenItsShortNameContainsTheTextWhateverItsCase`,
  `HistorySearchTest.theFullNameAndTheProviderAreNotTheShortName`
- [x] A query reaches the database as an escaped contains pattern with the matching models —
  `RoomChatRepositoryHistoryTest.aQueryIsSearchedAsAnEscapedContainsPatternWithTheModelsWhoseShortNameHasIt`
- [x] History emits on every send, pin, archive and delete, and only when it changes; a search
  stays quiet while replies stream unless its matches change —
  `RoomChatRepositoryHistoryTest.historyEmitsOnEverySendPinArchiveAndDeleteAndOnlyWhenItChanges`,
  `RoomChatRepositoryHistoryTest.aSearchStaysQuietWhileRepliesStreamUnlessItsMatchesChange`
- [x] The count is the conversations History lists without a query —
  `RoomChatRepositoryHistoryTest.theCountIsTheConversationsHistoryListsWithoutAQuery`

Pin, archive, delete

- [x] Pinning keeps a conversation's place in "recent" and History and its `updatedAt`, and says
  whether it changed anything — `RoomChatRepositoryHistoryTest.pinningKeepsAConversationsPlaceAndUpdatedAtAndSaysWhetherItChangedAnything`
- [x] Archiving leaves a conversation out of History, its search and "recent" (the next one moves
  in), keeps it and its messages, and it still opens by id —
  `RoomChatRepositoryHistoryTest.archivingLeavesAConversationOutOfHistoryAndRecentButKeepsItAndItsMessages`
- [x] A message sent in an archived conversation brings it back to the top, pinned as it was —
  `RoomChatRepositoryHistoryTest.aMessageSentInAnArchivedConversationBringsItBackToTheTopAsItWas`
- [x] Deleting removes the conversation and its messages, a later send to it sends nothing, and it
  says whether it deleted anything —
  `RoomChatRepositoryHistoryTest.deletingAConversationRemovesItAndItsMessagesAndSaysWhetherItDeletedAnything`
- [x] Deleting a conversation whose reply thinks or streams stops that reply at its next tick,
  harmlessly, and other replies keep streaming —
  `RoomChatRepositoryHistoryTest.deletingAConversationWhileItsReplyThinksStopsTheReplyOnItsFirstTick`,
  `RoomChatRepositoryHistoryTest.deletingAConversationWhileItsReplyStreamsStopsItAndLeavesTheOthersStreaming`,
  `ReplyStreamerTest.aStreamWhoseReplyIsGoneStopsOnTheWriteThatFindsNothingAndEndsQuietly`
- [x] Archiving a conversation whose reply streams lets the reply finish —
  `RoomChatRepositoryHistoryTest.archivingAConversationWhileItsReplyStreamsLetsTheReplyFinish`
- [x] Rows read with their pinned and archived flags —
  `EntityMappersTest.aConversationRowReadsWithItsPinnedAndArchivedFlags`
- [x] Koin provides `RoomChatRepository` on `HistoryDao` too —
  `DataChatModuleTest.koinProvidesOneRoomChatRepositoryOnTheDatabasesDaos`

### UI (`:feature:history`, `:core:navigation`, `:feature:chat`)

<!-- PLACEHOLDER for the feature agent: the screen, its previews and its UI criteria (search box,
groups, rows, swipe closed / dragging / open, pin / archive / delete with their toasts, no
matches, the empty state on first launch, the status row, opening a conversation, the History
button in Chat), each with the test that checks it. -->

_To be written by the row 5 feature agent._

## Data contract

- **Module and package:** `:data:chat`, `br.com.weslleycampos.chat.data.chat` (models in `….models`).
  It now exposes `kotlinx-datetime` as `api`, for `groupHistory`'s `TimeZone`.
- **Repository:** `ChatRepository` gains

  ```kotlin
  interface ChatRepository {
      // …row 3 and row 4 as they were; observeRecentConversations() now leaves archived ones out
      fun observeHistory(query: String = ""): Flow<List<Conversation>>
      fun observeConversationCount(): Flow<Int>
      suspend fun setPinned(conversationId: Long, pinned: Boolean): Boolean
      suspend fun archive(conversationId: Long): Boolean
      suspend fun delete(conversationId: Long): Boolean
  }
  ```

- **Models:**
  - `Conversation(…, updatedAt, pinned: Boolean = false, archived: Boolean = false)`; both default
    to `false`, so earlier named-argument calls still compile.
  - `enum class HistorySection { PINNED, TODAY, YESTERDAY, EARLIER }`, in History's order.
  - `data class HistoryGroup(section: HistorySection, conversations: List<Conversation>)`, never
    empty.
  - `fun groupHistory(conversations: List<Conversation>, now: Instant, timeZone: TimeZone):
    List<HistoryGroup>` (`kotlin.time.Instant`, `kotlinx.datetime.TimeZone`).
- **Storage:** Room version 4 with `AutoMigration(from = 3, to = 4)`, schema `4.json`: `pinned` and
  `archived` on `conversations`, `INTEGER NOT NULL DEFAULT 0`. New `HistoryDao` (`observeAll()`,
  `search(pattern, modelIds)`, `observeCount()`), `ConversationDao.setPinned`, `setArchived` and
  `delete` returning the rows changed, `ConversationDao.observeRecent` without archived rows,
  `MessageDao.updateReply` returning the rows changed, and `containsPattern(text)` for `LIKE … ESCAPE
  '\'`. `HistoryDao` is bound in `CoreDatabaseModule`.
- **Koin binding:** `@Single internal fun chatRepository(conversationDao, historyDao, messageDao,
  transactions, models): ChatRepository` → `RoomChatRepository`, in `ChatBindings.kt`. Inject
  `ChatRepository` only; `ModelRepository` is unchanged.
- **Behaviour:**
  - Every read emits on collection and again on every change that alters it, never the same value
    twice in a row (`distinctUntilChanged`).
  - `observeHistory("")` is every conversation that is not archived, most recently updated first,
    the later id on a tie, pinned ones included in that order. It reads only `conversations`, so a
    streaming reply does not re-run it. It emits on a send (the conversation moves to the top), a
    new conversation, a pin, an archive, a delete and a model choice.
  - `observeHistory(query)` trims the query; blank lists everything. Otherwise it keeps the
    conversations whose title, one of whose user messages, or whose model's `shortName` contains
    it, in the same order. Title and message text are matched in SQL with `LIKE '%…%' ESCAPE '\'`
    (`%`, `_`, `\` literal; A–Z case-insensitive, other letters as typed); the short name in Kotlin,
    ignoring case ("gpt" → `gpt5`, "4" → sonnet-4.5, opus-4.1, llama-4). Replies are never
    searched. While a query is set, Room re-runs the search on every write to `messages` (each
    28 ms tick of a streaming reply); it emits only when the matches change.
  - `observeConversationCount()` is the size of `observeHistory("")`: every conversation not
    archived.
  - `observeRecentConversations()` is the three most recently updated conversations that are not
    archived.
  - `setPinned(id, pinned)` writes `pinned` and returns `true`; `false` and no write for an unknown
    id or one already so. `updatedAt`, "recent" and History's order do not move.
  - `archive(id)` sets `archived` and returns `true`; `false` for an unknown id or one already
    archived. History, its search, the count and "recent" leave it out; `observeConversation(id)`
    still emits it (`archived = true`) and `observeMessages(id)` its thread; a reply on its way
    finishes. A later `send(id, …)` clears `archived` and moves it to the top, pinned as it was.
    Nothing lists archived conversations and there is no unarchive call.
  - `delete(id)` deletes the conversation and, through the foreign key's cascade, every message,
    and returns `true`; `false` for an unknown or already deleted id. Its observers then read `null`
    and an empty thread, and `send(id, …)` returns `null`. A reply still thinking or streaming stops
    at its next tick: the stream's write changes no row, so it ends without writing again or
    failing.
  - Sends, choices, pins, archives and deletes share one `Mutex`, so none lands between a send's
    check and its writes.
  - `groupHistory(list, now, timeZone)` puts pinned conversations in `PINNED` whatever their day,
    the rest in `TODAY`, `YESTERDAY` or `EARLIER` by the local date of `updatedAt` in `timeZone`
    against `now`'s, a date after today counting as `TODAY`. Groups come in that order, empty ones
    left out, each newest first, the later id on a tie, whatever the input order.
- **What the feature builds on:**
  - The list: `observeHistory(query)` from the search box's text (e.g. `flatMapLatest`), grouped
    with `groupHistory(list, clock.now(), timeZone)` in the device's time zone. Grouping is not a
    Flow of its own: regroup when the local date changes (a minute ticker or on resume), since no
    database change happens at midnight.
  - Group labels are UI copy: `PINNED` "pinned", `TODAY` "today", `YESTERDAY` "yesterday",
    `EARLIER` "earlier" (the prototype lowercases them).
  - A row: "▲" when `pinned`; `title` on one line; dot in `model.palette`, `model.shortName`; time
    from `updatedAt`, `HH:MM` in `TODAY` and `YESTERDAY` and "Sep 13" in `EARLIER` (frames §3), as
    `:feature:chat`'s `formatClockTime` and `formatRecentTime` write them. No folder chip and no
    extra (see Decided).
  - Actions: "pin" / "unpin" → `setPinned(id, !conversation.pinned)`; "archive" → `archive(id)`,
    toast "archived"; "delete" → `delete(id)`, toast "deleted". Each returns `false` when nothing
    changed (a double tap, a row already gone).
  - Opening: a tap opens the conversation in Chat by id (`ChatEntry(conversationId)`), which reads
    it with `observeConversation` / `observeMessages`; no new data call. A conversation archived or
    deleted while Chat shows it: Chat's `observeConversation` emits it archived, or `null`.
  - No matches: `observeHistory(query)` empty for a non-blank query. First launch: it is empty for
    a blank query (no demo conversations are seeded).
  - Status row: if the feature keeps one, `observeConversationCount()` is the honest number to
    show; it says nothing about sync.
  - Accent: History shows no conversation, so it wears the root's default-model palette.

## Decided

- Decided: folders are out of scope and there is no `folder` column. The frame shows a chip when a
  folder is set, but nothing in the prototype sets one, and a column nothing can write would always
  be `null`. A later row that adds a way to file conversations adds the column with its own
  migration. The search does not look for folders either.
- Decided: archived means hidden, not gone. An archived conversation is left out of History, its
  search, its count and the Chat "recent" list, but stays in Room with its messages, still opens by
  id, and a reply on its way finishes into it. There is no archive screen, since the prototype has
  none, so there is no unarchive call either.
- Decided: a message sent in an archived conversation brings it back (clears `archived`), at the top
  of History and "recent", pinned as it was. With no archive screen, a conversation the user keeps
  writing in would otherwise vanish for good once they leave it, as a mail client brings an
  archived thread back when it gets a new message.
- Decided: delete removes the conversation and its messages (the foreign key cascades), with no
  confirmation and no undo, as the prototype. A reply still on its way stops at its next tick: the
  streamer stops when `MessageDao.updateReply` reports no row changed, so it neither writes again
  nor fails, whatever deleted the row (row 10's "delete account" too). Cancelling a tracked job per
  conversation would need a map kept in step with every stream; the row count cannot go stale.
- Decided: the search matches the title, the user's own messages and the model's short name, the
  placeholder's "search titles and messages" for what the user wrote (frames quirk 6: the prototype
  matched title, short name and folder only). Replies are not searched: they are canned, the same
  three in every conversation, so a word from them would match nearly every conversation, and their
  blocks are a JSON column that `LIKE` would match on its keys ("type", "text"). When a real
  provider writes replies, a plain-text column can add them.
- Decided: matching is `LIKE '%…%' ESCAPE '\'` in SQL, with `%`, `_` and `\` escaped by
  `containsPattern`, so a query never acts as a wildcard; the short name is matched in Kotlin and
  passed as `modelId IN (…)`, since the catalogue is not in the database. SQLite's `LIKE` ignores
  case for A–Z only; full Unicode folding needs ICU or a lowercase copy of every text, which is not
  worth it for local data. The query is trimmed (the prototype's `trim()`), not otherwise changed.
- Decided: a search reads `messages`, so Room re-runs it on every write there, a streaming tick
  included; `distinctUntilChanged` keeps it from emitting. One indexed `EXISTS` over a person's own
  messages is cheap, and the blank-query list reads only `conversations`, so a streaming reply
  never re-runs it.
- Decided: History groups by the local date of `updatedAt`, the latest message, not `createdAt`:
  the row's time is its last activity, "recent" sorts on it, and a conversation continued today
  belongs under "today".
- Decided: the grouping is a pure function in `:data:chat` (`groupHistory`), taking `now` and the
  time zone, rather than a grouped Flow: "today" changes at midnight with no database change, and a
  function is easy to test across midnights and zones and can be shared by later rows. The
  repository's Flow is the flat list.
- Decided: a conversation updated after `now`'s date (the device's clock moved back) counts as
  "today", rather than falling into "earlier".
- Decided: pinning and archiving do not move `updatedAt`, so neither reorders "recent" or History as
  if the user had chatted; only a send does. Pinned conversations keep that order inside "pinned".
- Decided: `setPinned`, `archive` and `delete` return whether they changed anything and change
  nothing for an unknown id, as `choose` does; a double tap on an action is harmless.
- Decided: History's lists are a separate `HistoryDao` on the same table: `ConversationDao` keeps
  the single-row reads and the writes, and stays under detekt's function limit for interfaces.
- Decided: no new index. A person's conversations are a small table that `ORDER BY updatedAt` sorts
  at once, the search's `EXISTS` uses the existing `messages(conversationId)` index, and an index on
  `conversations` would cost every send a write for no measurable gain.
- Decided: the prototype's synced / syncing / offline row is not sync (orchestrator). Data offers
  `observeConversationCount()` for an honest replacement, such as how many conversations are on the
  device; the feature agent decides the copy, or drops the row.
- Decided: the line-2 "extra" ("⇄ gpt-5" for compare, "1 image" for attachments) is not built:
  compare is row 7 and attachments row 8, which add what it needs.
- Decided: opening a conversation needs no data call: `observeConversation(id)` and
  `observeMessages(id)` already exist, and the prototype's `loadSeed` (quirk 8) is not copied, so
  each row opens its own thread. New chats appear in History on their first send (quirk 8 fixed).

## Out of scope

- Folders, the folder chip and filing a conversation: no frame sets a folder.
- An archive screen, unarchiving and undo: the prototype has none.
- The line-2 extra: compare (row 7) and attachments (row 8).
- Sync and its status: nothing syncs (orchestrator).
- Searching replies: canned, see Decided.
- Memory and Settings, which History's bar opens: rows 9 and 10. Exporting and wiping history:
  row 10.
