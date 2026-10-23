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

The screen is `HistoryScreen` in `:feature:history`, stateless, over one `HistoryUiState` from
`HistoryViewModel`. Its previews (`HistoryScreenPreviewParameterProvider`, 402 × 874) draw every
state in light and dark, in the root's default-model accent, from the prototype's ten `HISTORY`
items (no folders, no extras) at their times:

| Preview | What it shows | Design source |
| --- | --- | --- |
| Grouped | "pinned" (two "▲" rows, one "14:02", one "Sep 12"), "today", "yesterday", "earlier"; status "on this device · 10 conversations"; footer hint | lines 256–298 |
| Filtered | "gpt" in the box with "×"; only the two GPT-5 rows | lines 265–271, filter 833–834 |
| No matches | "kubernetes": `no matches for “kubernetes”`, no hint | line 297 |
| Empty | first launch: "no conversations yet", "on this device · no conversations" | — (see Decided) |
| Swiped open | a "today" row at −192 over "pin", "archive", "delete" | lines 277–281, `rowStyle` 839 |
| Swiped open · pinned | a pinned row open over "unpin", "archive", "delete" | `pinLabel` 838 |
| Toast | "archived" over the list, the archived row gone | lines 531–534, 843 |

Criteria run on the Android host and the iOS simulator. Layout, colours, the drag itself and its
animation are checked in the previews and on a device: there is no `runComposeUiTest`, as in rows
3 and 4 (no Robolectric on the Android host). The swipe's maths, the row actions and every state
are pure functions or ViewModel state, tested below.

Navigation

- [x] Chat's History button opens History over the chat, which stays underneath; a second tap opens
  it once — `ChatViewModelTest.theHistoryButtonOpensHistoryOverTheChatWhichStaysUnderneath`
- [x] Back (the chevron, system back, the iOS edge swipe) returns to the chat underneath as it was;
  History alone gives way to a new chat — `HistoryViewModelTest.backReturnsToTheChatUnderneathAsItWas`
- [x] A tap on a row opens its conversation in place of the chat underneath, from a conversation or
  a new chat — `HistoryViewModelTest.aTapOnARowOpensItsConversationInPlaceOfTheChatUnderneath`
- [x] `HistoryEntry` survives the back stack's save, next to every key the app registers —
  `HistoryEntryProviderTest.aSavedBackStackRestoresHistory`,
  `AppEntriesTest.theSavedBackStackRestoresEveryScreenTheAppShows`
- [x] Koin provides the entry provider, one `HistoryTimes`, and a `HistoryViewModel` on the app's
  navigator — `HistoryModuleTest.koinProvidesTheEntryProviderAndOneHistoryTimes`,
  `HistoryModuleTest.koinBuildsAHistoryViewModelOnTheAppsNavigator`

The list

- [x] History lists every conversation in "pinned", "today", "yesterday" and "earlier", newest
  first, under lowercase labels, with an empty group left out —
  `HistoryViewModelTest.historyListsEveryConversationInItsGroupNewestFirstWithTheCount`,
  `HistoryUiStateTest.theGroupsArePinnedTodayYesterdayAndEarlierUnderTheirLowercaseLabels`
- [x] A row shows "▲" when pinned, its title, its model's dot (the palette named after the model's
  id) and short name, and its time — `HistoryUiStateTest.aRowShowsThePinMarkItsTitleItsModelsDotAndShortNameAndItsTime`,
  `HistoryFormatsTest.everyModelHasThePaletteNamedAfterItsId`
- [x] A row's time is local `HH:MM` for today and yesterday (and after today, a clock moved back),
  else "Sep 13", in the device's time zone —
  `HistoryFormatsTest.aRowShowsTheClockTimeForTodayAndYesterdayAndTheDateBeforeThat`,
  `HistoryFormatsTest.aRowsDayAndTimeAreLocalToTheGivenTimeZone`,
  `HistoryFormatsTest.historyTimesReadTheDevicesClockAndTimeZoneOnEveryUse`
- [x] When the date changes while History is open, the rows move from "today" to "yesterday" and on
  to "earlier" within a minute — `HistoryViewModelTest.theGroupsMoveOnWhenTheDateChangesWhileTheScreenIsOpen`,
  `HistoryFormatsTest.theDaysEmitTodayAndTheNextDayWithinAMinuteOfMidnightOnlyOnce`
- [x] The status row is a hollow ring and "on this device" with the count History lists, whatever
  the query, following it as conversations come and go —
  `HistoryFormatsTest.theStatusSaysWhereHistoryIsKeptAndHowManyConversationsItLists`,
  `HistoryViewModelTest.theCountFollowsTheConversationsAsTheyComeAndGo`
- [x] First launch shows "no conversations yet" once the list is read, and nothing before —
  `HistoryViewModelTest.firstLaunchShowsTheEmptyStateOnceTheListIsReadAndNothingBefore`,
  `HistoryUiStateTest.noMatchesNeedsAReadEmptyListAndAQueryWhileTheEmptyStateNeedsNone`
- [x] The footer hint shows under rows only — `HistoryUiStateTest.theFooterHintShowsUnderRowsOnly`

Search

- [x] Typing filters the list at once, trailing spaces search nothing new, "×" shows while the box
  has text and clears it back to every conversation —
  `HistoryViewModelTest.aQueryFiltersTheListAtOnceAndClearingItListsEverythingAgain`,
  `HistoryUiStateTest.theClearMarkShowsWhileTheBoxHasText`
- [x] A query that matches nothing reads `no matches for “<trimmed query>”`, with no hint —
  `HistoryViewModelTest.aQueryThatMatchesNothingSaysSoWithTheTrimmedQueryAndNoHint`

Swipe and actions

- [x] A drag moves a row between closed and −192 (three 64 dp actions) and no further; released
  past −96 it opens, else it closes, over 180 ms on `cubic-bezier(.2,.8,.2,1)` —
  `HistorySwipeTest.anOpenRowSitsItsThreeActionsWidthToTheLeft`,
  `HistorySwipeTest.aDragMovesTheRowBetweenClosedAndOpenAndNoFurther`,
  `HistorySwipeTest.aReleasePastHalfTheActionsOpensTheRowAndOneShortOfItClosesIt`,
  `HistorySwipeTest.aRowRestsOpenOrClosed`, `HistorySwipeTest.aReleaseSettlesOverTheDesigns180MsCurve`
- [x] One row at most is open: a drag on another row closes the open one —
  `HistoryViewModelTest.onlyOneRowIsOpenAtATime`, `HistoryViewModelTest.aRowThatLeavesTheListIsNoLongerOpen`
- [x] A tap while a row is open closes it and opens nothing; a drag is never a tap (the drag
  consumes the pointer, so the release that opens a row neither opens nor closes it) —
  `HistoryViewModelTest.aTapWhileARowIsOpenClosesItAndOpensNothing`, the device check below
- [x] The actions are "pin" ("unpin" when pinned), "archive" and "delete", the same three as
  custom accessibility actions on the row, in `--bg3`/`--fg`, `--fg3`/`--bg` and `--danger`/`--bg`
  in every palette — `HistoryUiStateTest.aRowOffersPinOrUnpinThenArchiveAndDeleteToTouchAndScreenReadersAlike`,
  `HistoryUiStateTest.theActionsWearTheDesignsColoursInEveryPalette`
- [x] "pin" moves the row to "pinned" and "unpin" back to its day, each closing the row, with no
  toast — `HistoryViewModelTest.pinMovesARowToPinnedAndUnpinBringsItBackEachClosingTheRow`
- [x] "archive" hides the row and the toast says "archived" for 1400 ms; "delete" removes the
  conversation and says "deleted", a newer toast restarting the time —
  `HistoryViewModelTest.archiveHidesTheRowAndTheToastSaysArchivedFor1400Ms`,
  `HistoryViewModelTest.deleteRemovesTheConversationAndTheToastSaysDeletedReplacingAnArchivedToast`
- [x] An action on a row already gone, or one that fails, shows no toast and leaves the list as the
  database has it — `HistoryViewModelTest.anActionThatChangesNothingOrFailsShowsNoToast`
- [x] The previews cover every state above in light and dark —
  `HistoryUiStateTest.previewsCoverEveryStateInLightAndDark`

On a device (not automatable here): the drag's feel and its 180 ms settle; a vertical scroll never
moves a row and a horizontal drag never scrolls; back and the iOS edge swipe from History and from
an opened conversation; the keyboard over the list; light and dark on Android and iOS.

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

## UI contract

What later rows extend, in `:feature:history` (`br.com.weslleycampos.chat.feature.history`) unless
noted.

- **Key:** `@Serializable data object HistoryEntry : NavKey` in `:core:navigation`'s
  `entries.history`. `HistoryEntryProvider` registers it. Chat's History button
  (the extension `ChatViewModel.onOpenHistory()`) pushes it: `[ChatEntry(x)]` → `[ChatEntry(x), HistoryEntry]`.
  Back (`HistoryViewModel.onBack()`, the chevron, system back, the iOS edge swipe) pops it; a row
  (`onRowClick(id)`) calls `navigate(ChatEntry(id)) { popUpTo<ChatEntry> { inclusive = true } }`, so
  the stack becomes `[ChatEntry(id)]`.
- **Screen:** `HistoryScreen(uiState, onBack, onMemoryClick, onSettingsClick, onQueryChange,
  onClearQuery, onRowClick, onSwipeStart, onSwipeSettle, onRowAction, modifier)`.
  `HistoryEntryProvider` wires `onMemoryClick` (row 9) and `onSettingsClick` (row 10) to `{}`: the
  icons are drawn as the frame has them and do nothing yet. Rows 9 and 10 navigate from there to
  their own keys, and their back chevrons return here with `navigateUp()` (frames §4: Memory and
  Settings → History).
- **State:** `HistoryUiState(query, groups: List<HistoryGroupUi>, loaded, conversationCount: Int?,
  openRow: Long?, toast: StringResource?)` with `status`, `showsClearQuery`, `noMatchesFor`,
  `showsEmptyState`, `showsFooterHint` and `row(id)`. `HistoryGroupUi(section, rows)` with `label`;
  `HistoryRowUi(id, title, pinned, modelShortName, palette, time)` with `actions:
  List<HistoryRowActionUi(action, label)>`; `enum HistoryRowAction { TogglePin, Archive, Delete }`.
  `historyGroups(conversations, now, timeZone)` and `Conversation.toRowUi(now, timeZone)` build them.
- **ViewModel:** `HistoryViewModel(navigator, repository: ChatRepository, times: HistoryTimes)`,
  `Navigator by navigator`, bound only to its own class: `onQueryChange`, `onClearQuery`, `onBack`,
  `onRowClick(id)`, `onSwipeStart(id)`, `onSwipeSettle(id, open)`, `onCloseRow`,
  `onRowAction(id, action)`. Its toast goes through its own `TimedValue`.
- **Formats:** `historyRowTime(updatedAt, now, timeZone)`, `historyStatus(count)`; `HistoryTimes(clock,
  zone)` (Koin single) with `now()`, `timeZone()`, `today()` and `days()`, the local date re-read
  every `DayCheckInterval` (1 minute), emitting on change. The swipe's maths: `SwipeActionWidth`
  (64 dp), `SwipeOpenDistance` (192 dp), `clampSwipeOffset`, `swipeSettlesOpen`, `restingSwipeOffset`.
- **Shared with Chat, not moved:** the clock time, the "Sep 13" date and `ChatModel.palette` are
  repeated (internal) in `HistoryFormats.kt` rather than shared through a feature→feature dependency;
  see Decided. A third feature that needs them moves them to a shared module.

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

- Decided (UI): the status row says where History lives instead of sync: a hollow 7 px ring in
  `--fg3` (the prototype's "local only" indicator) and "on this device · 12 conversations", from
  `observeConversationCount()`; "1 conversation", "no conversations", and "on this device" alone
  until the count is read. It counts what History lists, whatever the query; archived
  conversations are not counted, as History does not show them. The row is kept rather than dropped:
  it keeps the frame's rhythm between the bar and the search box, and it is the one honest place to
  say that nothing leaves the device. The copy is a pure function (`historyStatus`), so it is tested
  like Chat's meta lines.
- Decided (UI): Memory and Settings are drawn in the bar as the frame has them, with their
  accessibility labels, and do nothing until rows 9 and 10 (`HistoryEntryProvider` passes `{}`).
- Decided (UI): Chat's History button pushes `HistoryEntry` over the chat, and back from History is
  `navigateUp()`, so the chat underneath is still there as it was, draft and open conversation
  included (frames §4: "current conversation unchanged"). If History is ever alone on the stack, back
  replaces it with a new chat rather than leaving nothing.
- Decided (UI): a row replaces the chat under History with `ChatEntry(id)`: `[ChatEntry(x),
  HistoryEntry]` becomes `[ChatEntry(id)]`. Chat stays the root of the signed-in app, back from the
  opened conversation leaves the app as from any chat, and History never piles up (the prototype's
  `go()` has no back stack either). The opened conversation gets a fresh chat entry, so a draft left
  in the chat underneath is not carried over (with an equal key, `ChatEntry(id)` again, Navigation 3
  may keep that entry's state; checked on a device).
- Decided (UI): the swipe is `Modifier.draggable` with the offset in a float state and the settle
  animated inside `DraggableState.drag`, so a new drag stops a settling row where it is. It follows
  `swipeMove` / `swipeEnd`: clamped between 0 and −192, opening past −96 by position alone (no fling
  velocity, as the prototype), 180 ms on `cubic-bezier(.2,.8,.2,1)`. `AnchoredDraggable` was not
  used because its fling behaviour adds a velocity threshold the design does not have. Right-to-left
  mirrors the drag and the offset, so the actions stay under the finger's side.
- Decided (UI): a drag consumes the pointer past touch slop, so it is never a tap: the release that
  opens a row neither opens the conversation nor closes the row (frames quirk 5). A tap while a row
  is open, on that row or another, only closes it: a tap meant to dismiss the actions should not
  navigate away under them (the prototype opened the other row's conversation). Starting a drag on
  another row closes the open one, so one row at most is open; a row that leaves the list (archived,
  deleted, filtered out) is no longer open. A vertical drag scrolls the list and never moves a row.
- Decided (UI): the actions are composed only while the row is moved, so screen readers never find
  hidden buttons; instead the row offers "pin"/"unpin", "archive" and "delete" as custom
  accessibility actions, from the same `HistoryRowUi.actions` list the buttons are drawn from.
- Decided (UI): every action closes the row at once. "archive" and "delete" toast "archived" and
  "deleted" only when the repository says it changed something, so a row already gone or a failed
  write shows nothing rather than a false toast; "pin" shows none, as the prototype.
- Decided (UI): no debounce on the search. The data is local, each keystroke is one indexed query,
  and `flatMapLatest` drops a stale one; the trimmed query is passed on distinct, so trailing spaces
  search nothing new. "no matches for “…”" shows the trimmed query.
- Decided (UI): first launch (no conversation and no query) reads "no conversations yet" in the
  no-matches style, where the prototype would have said `no matches for “”`. Neither message shows
  until the list is read. The footer hint shows only under rows, since it is about them.
- Decided (UI): the groups are rebuilt when the list emits and when `HistoryTimes.days()` reports a
  new local date, checked every minute while History is on screen, so rows move to "yesterday"
  within a minute of midnight and a time-zone change shows within a minute. Android's `delay` does
  not count deep sleep, so after the device wakes the check can lag by up to that minute.
- Decided (UI): a row's time is `HH:MM` for today and yesterday and "Sep 13" before that (no year,
  as Chat's "recent"), read from the row's own day, so a pinned row shows its time or date too.
- Decided (UI): `ChatTextField` does not fit the search box (48 tall on `--bg`, mono 14, no leading
  icon), so the box is a `BasicTextField` in `:feature:history` with `ChatTheme` tokens: 40 tall on
  `--bg2`, mono 13, the search icon, "×" as a 44 wide clear button that keeps the focus, and the
  keyboard's Search closing the keyboard.
- Decided (UI): `:feature:history` repeats `:feature:chat`'s clock time, "Sep 13" and model palette
  lookup (a few internal lines in `HistoryFormats.kt`, each tested) and its `TimedValue`, instead of
  depending on `:feature:chat` or moving them: History's time rule differs (yesterday shows a time),
  the chat's formatters are public API of spec 03, and `:core:ui` / `:data:chat` would gain a date or
  UI dependency for them. A third feature that needs them moves them to a shared module.
- Decided (UI): History sits inside the top and side safe areas; the list runs under the bottom
  inset with `inset + 12` of content padding, the keyboard included, so the last row scrolls above
  it. The toast sits 84 dp over that inset (frames §2.9). No `runComposeUiTest`, as rows 3 and 4.

## Out of scope

- Folders, the folder chip and filing a conversation: no frame sets a folder.
- An archive screen, unarchiving and undo: the prototype has none.
- The line-2 extra: compare (row 7) and attachments (row 8).
- Sync and its status: nothing syncs (orchestrator).
- Searching replies: canned, see Decided.
- Memory and Settings, which History's bar opens: rows 9 and 10 (the icons are drawn and inert).
  Exporting and wiping history: row 10.
- The row's dragging state as a preview: it is a moment between closed and open, checked on a
  device.
