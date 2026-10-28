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

The row adds to `ChatScreen` in `:feature:chat`: a tap on a message, its action row, the fork
banner, the read-aloud bar and the fork's toast, over the same `ChatUiState` from `ChatViewModel`.
Its previews (`ChatScreenPreviewParameterProvider`, 402 × 874) draw these states in light and dark
on the "Done" thread (the question at 14:01, its reply at 14:02 in 0.8 s):

| Preview | What it shows | Design source |
| --- | --- | --- |
| Selected · question | the user bubble selected: "copy", "⑂ fork here", "retry" under it | lines 208–215 |
| Selected · reply | the reply selected: "copy", "⑂ fork here", "read aloud" under it | lines 208–215 |
| Copied · reply | the reply's row reading "copied" | `copyAllLabel` 826 |
| Reading · playing | the bar under the reply: "❚❚", 42 %, "0:07 / 0:17", "×" | lines 176–183 |
| Reading · paused | "▶", 66 %, "0:11 / 0:17" | lines 176–183 |
| Fork banner | "⑂ forked from “useDebounce hook with leading edge” at message 2" and "×" under the top bar | line 117 |
| Toast · forked | the banner, and "forked · new conversation" over the composer | lines 531–534 |

Criteria run on the Android host and the iOS simulator. As in rows 3 to 5 there is no
`runComposeUiTest` (no Robolectric on the Android host): every tap, timer and navigation is
`MessageActions` state or a pure function, tested below, and the drawing is checked in the
previews and on a device.

Selection

- [x] A tap on a message selects it and shows its action row; a tap on it again hides it —
  `ChatViewModelMessageActionsTest.aTapSelectsAMessageAndShowsItsActionsAndATapOnItAgainHidesThem`
- [x] A tap on another message moves the selection, so one message at most is selected —
  `ChatViewModelMessageActionsTest.aTapOnAnotherMessageMovesTheSelectionSoOneAtMostIsSelected`
- [x] A reply thinking or streaming cannot be selected, copied, read or forked; once done it can —
  `ChatViewModelMessageActionsTest.aReplyThinkingOrStreamingCannotBeSelectedOrActedOn`,
  `ChatUiStateTest.onlyAMessageThatIsNotOnItsWayIsSelectable`
- [x] While another reply streams, the selection and a read stay as they are —
  `ChatViewModelMessageActionsTest.aSelectionAndAReadStayWhileAnotherReplyStreams`
- [x] Leaving the screen closes the row and pauses a read where it is, and "▶" plays on from there —
  `ChatViewModelMessageActionsTest.leavingTheScreenPausesTheReadAndClosesTheActionRow`

Copy

- [x] "copy" hands the clipboard the prototype's `msgText`: the user's text, or a reply's blocks
  joined by a blank line, code as it is and list items as "1. text"; an interrupted reply copies
  what it revealed — `MessageFormatsTest.aMessagesCopyTextIsItsTextOrItsBlocksJoinedByABlankLineWithNumberedItems`
- [x] "copy" reads "copied" for 1200 ms and leaves the row open —
  `ChatViewModelMessageActionsTest.copyHandsTheMessageTextToTheClipboardAndReadsCopiedFor1200Ms`
- [x] A message's "copied" and a code block's are one label with one time: the newer copy replaces
  the other — `ChatViewModelMessageActionsTest.aMessageCopyAndACodeCopyShareOneCopiedLabelAndItsTime`

Fork

- [x] "⑂ fork here" closes the row, forks once however often it is tapped, and replaces the chat
  with the fork (`[ChatEntry(fork)]`), whose chat shows its thread, its banner and "forked · new
  conversation" for 1400 ms, once —
  `ChatViewModelMessageActionsTest.forkHereReplacesTheChatWithTheForkWhichSaysForkedNewConversationFor1400Ms`
- [x] Koin hands every `ChatViewModel` the one `ChatToastRelay`, which carries that toast to the
  fork's chat — `ChatModuleTest.koinHandsEveryChatViewModelTheOneToastRelay`
- [x] A fork that forks nothing or fails leaves the chat as it was and the toast says "couldn’t
  fork, try again" — `ChatViewModelMessageActionsTest.aForkThatForksNothingOrFailsLeavesTheChatAndSaysSo`
- [x] The banner reads "⑂ forked from “<title>” at message <n>" ("untitled" for a blank title)
  while the fork's banner is not dismissed, stays through sends, and its "×" hides it for good —
  `MessageFormatsTest.theForkBannerNamesTheSourceAndTheMessageNumberOrUntitled`,
  `ChatUiStateTest.theForkBannerShowsOnlyWithAForkOrigin`,
  `ChatViewModelMessageActionsTest.theForkBannerSaysWhereTheForkCameFromUntilItsCrossHidesItForGood`
- [x] A new chat has no banner (superseded by row 9: there is no new-chat control; a fork that is
  gone while open leaves a new chat with no banner; spec 09) —
  `ChatViewModelMessageActionsTest.aForkGoneWhileOpenLeavesANewChatWithNoForkBanner`

Read aloud

- [x] "read aloud" closes the row and shows the bar under the reply with "❚❚", filling 0.6 % every
  100 ms until it is full after 167 ticks, where it stops with "▶" and stays —
  `ChatViewModelMessageActionsTest.readAloudShowsTheBarUnderTheReplyFillingPointSixPercentEvery100MsUntilFullWithPlay`,
  `MessageFormatsTest.theReadAloudBarFillsByPointSixPercentATickAndIsFullAfter167Ticks`,
  `MessageFormatsTest.theReadAloudBarIsFullOnceItHasPlayedEveryTick`
- [x] The time is the bar's own, to the nearest second: "0:07 / 0:17", and "0:17 / 0:17" when full —
  `MessageFormatsTest.theReadAloudTimeTracksTheBarsOwnTicksToTheNearestSecond`
- [x] "❚❚" pauses and "▶" plays on; "read aloud" on the reply being read does the same and keeps its
  row open; "▶" on a full bar reads it again from the start —
  `ChatViewModelMessageActionsTest.playAndPauseHoldTheBarAndPlayOnAFinishedReadStartsItAgain`
- [x] One reply is read at a time, and a user message has no "read aloud" —
  `ChatViewModelMessageActionsTest.onlyOneReplyIsReadAtATime`
- [x] The bar's "×", "new chat", another conversation and the conversation's deletion stop the read
  and clear the selection (superseded by row 9: "new chat" is gone; the others still do; spec 09) —
  `ChatViewModelMessageActionsTest.readingStopsOnItsCrossOnAnotherConversationAndWhenTheConversationIsGone`

Retry

- [x] "retry" puts the user message's text in the composer in place of the draft, closes the row and
  sends nothing; a reply has no "retry" —
  `ChatViewModelMessageActionsTest.retryPutsTheUserMessageInTheComposerInPlaceOfTheDraftWithoutSendingIt`

Row 10's hook

- [x] A reply that has just finished streaming in the open thread goes to `onReplyDone`, which reads
  it only while `autoRead` answers `true` (`false` until row 10) —
  `ChatViewModelMessageActionsTest.aReplyThatFinishesStartsNoReadWhileAutoReadIsOff`,
  `ChatViewModelMessageActionsTest.theReplyDoneHookReadsAReplyThatJustFinishedWhenAutoReadIsOn`,
  `ChatViewModelMessageActionsTest.aReplyHasJustFinishedOnlyWhenItWasOnItsWayAndIsNowDone`

Previews

- [x] The previews cover every state above in light and dark —
  `ChatUiStateTest.previewsCoverEveryStateInLightAndDark`

On a device (not automatable here): a tap shows no ripple and moves nothing; the row's 150 ms
fade-in, and the row and the bar scrolling into view at the end of the thread; TalkBack and
VoiceOver reading "Show actions" / "Hide actions", "fork here", "copied" as it changes, "Play" /
"Pause", "Stop reading" and "Dismiss"; the clipboard on Android and iOS; back from a fork leaving
the app; History over a read, which shows "▶" on return; light and dark on both.

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
    prototype's `showActions`). The UI replaces the chat with the fork and toasts a failure; see
    Decided (UI).
  - The fork banner shows while `conversation.fork != null && !fork.bannerDismissed`: "⑂ " +
    `"forked from “${fork.sourceTitle}” at message ${fork.messageNumber}"`. Titles are never blank
    here (a send needs text), so the prototype's "untitled" fallback does not show; a feature may
    keep it for safety. "×" → `dismissForkBanner(conversationId)`.
  - "retry", "copy" and "read aloud" need no data call (see Decided).

## UI contract

What later rows extend, in `:feature:chat` (`br.com.weslleycampos.chat.feature.chat`).

- **Screen:** `ChatScreen(…, onCopyCode, onDismissForkBanner, messageActions:
  MessageActionCallbacks, modifier)`. `MessageActionCallbacks(onSelect, onCopy, onFork, onReadAloud,
  onRetry, onPlayPause, onStopReading)`, each `{}` by default, for the previews. `ChatEntryProvider`
  remembers it on `viewModel.messageActions` and its `player`, copies through the platform
  clipboard, and calls `onLeave()` from a `LifecycleStartEffect` when the entry stops.
- **State:** `ChatUiState` gains `selectedMessage: Long?`, `copiedMessage: Long?`, `readAloud:
  ReadAloudUi?` and `forkOrigin: ForkOrigin?`, with `forkBanner: String?`. `MessageUi.Assistant`
  gains `inProgress` (thinking or streaming), and every `MessageUi` has `selectable`.
  `ReadAloudUi(messageId, ticks, playing)` with `progress`, `label` and `finished`.
- **Actions:** `ChatViewModel(…, times, forks: ForkRepository, relay: ChatToastRelay)` exposes
  `messageActions: MessageActions`: `onSelect(id)`, `onCopy(id, copy)`, `onFork(id)`,
  `onReadAloud(id)`, `onRetry(id)`, `onDismissForkBanner()`, `onLeave()`, `onReplyDone(id)` and
  `player: ReadAloudPlayer` (`messageId`, `onPlayPause()`, `onStop()`). `ChatToastRelay` is a Koin
  `@Single` (`post(message)`, `take()`). Row 3's "copied" is now a `TimedValue<CopiedLabel>`
  (`OfCode(key)`, `OfMessage(id)`) that sets `copiedBlock` or `copiedMessage`.
- **Formats:** `MessageUi.copyText()`, `forkBannerText(origin)`, `readAloudProgress(ticks)`,
  `readAloudLabel(ticks)` and `formatReadTime(duration)`; `ReadAloudTick` (100 ms),
  `ReadAloudPermillePerTick` (6) and `ReadAloudTicks` (167).
- **Row 10 (auto read):** `MessageActions`' `autoRead: () -> Boolean` (`{ false }` today) and
  `onReplyDone(messageId)`, called once for each reply that finishes streaming in the open thread
  (`finishedReply(previous, current)`). Row 10 passes its "Read replies aloud" preference as
  `autoRead` where `ChatViewModel` builds `MessageActions`; no other change is needed.
- **Row 7 (compare pairs), built:** a pair message has no action row and no read aloud, as in the
  prototype. `MessageUi.ComparePair` answers `selectable = false`: `ChatThread` gives it no tap
  handler, `MessageActions` refuses every action on it (`actionable`), and `finishedReply` never
  names it, so auto read skips it. The toast is now `TimedValue<ChatToastUi>` (spec 07).

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
  player left until "×". The label was planned as `round(41 × readPct / 100)` seconds of a fixed
  "0:41" (frames quirk 3); the UI reads the bar's own time instead (see Decided (UI)). Starting a
  read clears the selection. Nothing is stored; "Read replies aloud" (auto read) is Settings,
  row 10.
- Decided: "copy" is UI-only: the user's text, or a reply's blocks joined by blank lines with list
  items as "1. text" (the prototype's `msgText`), through the clipboard as row 3's code "copy",
  reading "copied" for 1200 ms.
- Decided: no new index: `messagesThrough` reads by the `messages(conversationId)` index and the
  primary key.
- Decided (UI): frames quirk 3: the bar keeps the prototype's tick, +0.6 % every 100 ms, so it is
  full after 167 ticks (16.7 s), and its time is the bar's own: ticks × 100 ms of 0:17, each to the
  nearest second with a half rounding up ("0:07 / 0:17" at 42 %, "0:17 / 0:17" when full). The
  prototype's fixed "0:41" never matched its own 16.7 s bar, so the time ran 2.5 times too fast.
- Decided (UI): read aloud is visual only: no speech engine, no audio session, nothing stored. The
  bar and its time are the whole of it, as `ReadAloudPlayer`'s documentation says. Its buttons are
  "Play" / "Pause" and "Stop reading" to screen readers, as they control the bar.
- Decided (UI): one reply is read at a time, as the prototype's single `reading`. "read aloud" on
  another reply reads it from the start in place of the first and closes the row; on the reply
  being read it plays or pauses and keeps the row open, as `toggleRead`. A full bar stops with "▶"
  and stays until its "×"; "▶" on it reads again from the start (the prototype's `toggleRead` set
  `readPlaying` on a full bar with no timer, which stuck on "❚❚").
- Decided (UI): what stops a read: its "×", "new chat", a conversation from "recent", the
  conversation's deletion (the chat becomes a new chat) and a fork (its entry and ViewModel go).
  Leaving the screen pauses it where it is and closes the row: History over the chat, the app in the
  background, a rotation (`LifecycleStartEffect`'s stop calls `onLeave()`). The prototype's `go()`
  clears `selectedMsg` and lets the timer run behind other screens, where nobody sees it; here
  coming back shows "▶" at the same place.
- Decided (UI): selection follows the prototype's `select`: a tap selects a message, a tap on the
  selected one deselects it, a tap on another moves the selection. A tap elsewhere, a scroll or a
  send leaves it. "⑂ fork here", "retry" and starting a read close the row; "copy" leaves it open
  reading "copied".
- Decided (UI): a reply thinking or streaming gets no tap handler at all, not a disabled one, as
  `showActions` needs `!streaming && !thinking`; `MessageActions` refuses it too, so a late tap does
  nothing. While another reply streams, the selection and a read stay. The thread still snaps to
  its end on each tick (row 3), and the action row and the bar bring themselves into view when they
  appear, so neither opens off screen at the end of the thread.
- Decided (UI): accessibility: the bubble or reply body is `clickable` with no ripple (the frame
  shows none), with the click label "Show actions" / "Hide actions" and no role, as it is text and
  not a button. The glyphs are hidden from screen readers: "⑂ fork here" reads "fork here", the
  banner reads its text, "▶" / "❚❚" read "Play" / "Pause", and the two "×" read "Stop reading" and
  "Dismiss". "copy" / "copied" is a polite live region. The action buttons are `ChatButton`
  Secondary Small, 30 tall as in the frame. The player's buttons fill its 36 height, and the
  banner's "×" is 44 wide over the banner's height; both keep the frame's glyph positions.
- Decided (UI): copy per block: a paragraph is its text, code is its text with no fence or language,
  and a numbered list is its items as "1. text" on their own lines; blocks are joined by a blank
  line (`msgText`). An interrupted reply copies what it revealed, possibly nothing. The chat has one
  "copied" label, shared with the code block's, as the prototype's single `copiedKey`, and it reuses
  row 3's "copy" / "copied" strings.
- Decided (UI): a fork replaces the chat: `navigate(ChatEntry(fork)) { popUpTo<ChatEntry> {
  inclusive = true } }`, the rule History's rows use, so back from the fork leaves the app, as from
  any chat, and the source is a tap away in "recent" and History. Pushing the fork would stack a
  chat for each fork, which nothing else in the app does. Opening the fork in the same ViewModel,
  as "recent" does, was rejected: a new entry gives the fork a clean ViewModel (no draft, selection,
  read or "copied" from the source) and a key that names it, so a restore reopens the fork.
- Decided (UI): the "forked · new conversation" toast belongs to the fork's chat, a new entry with
  its own ViewModel, so `ChatToastRelay` (a Koin single) carries it: the source posts it before it
  navigates, and the next `ChatViewModel` takes it once, in `init`. It shows for
  `ChatToastDefaults.Duration` (1400 ms).
- Decided (UI): when `fork` returns `null` (nothing to fork, which the row never offers) or throws,
  the chat stays and the toast says "couldn’t fork, try again". A second tap while a fork runs does
  nothing, so one tap makes one fork.
- Decided (UI): the banner shows `conversation.fork` while it is not `bannerDismissed`, as the
  conversation is observed: "×" writes, and the banner goes when the conversation emits again, with
  no optimistic hide, so a failed write leaves it showing. It sits under the top bar, above the
  thread, and stays through sends. The prototype's "untitled" fallback is kept for a blank title.
- Decided (UI): "retry" replaces the draft, as the prototype's `retry` sets `draft`, and neither
  focuses the composer nor sends.
- Decided (UI): detekt's `TooManyFunctions` limit is 11. `ChatViewModel` is at 10 and gains none:
  the actions live in `MessageActions` (10), the timer in `ReadAloudPlayer`, the pure formats in
  `MessageFormats.kt` (`ChatFormats.kt` is near the file limit), and the composables in
  `ChatMessageActions.kt`.
- Decided (UI): the action row fades in over 150 ms with ease-out (the frame's `fadeIn 0.15s`); the
  bar and the banner appear at once, as in the frame. Previews draw the row fully opaque.

## Out of scope

- Real text-to-speech and voices: there is no speech engine; the player is a timer. Auto read:
  row 10.
- Regenerating a reply, resending on "retry", renaming a fork: the prototype has none (§2.11).
- Forking a compare pair: pair messages have no action row (row 7).
- Copying attachments into a fork: row 8 adds attachments and copies them with their message.
- Opening the source conversation from the banner: the design has no such link.
