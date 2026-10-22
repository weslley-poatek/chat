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

### UI (`:feature:chat`, `:core:ui`, `:core:navigation`, `:app`)

The screen is `ChatScreen` in `:feature:chat`, stateless, over one `ChatUiState` from
`ChatViewModel`. Its previews (`ChatScreenPreviewParameterProvider`, 402 × 874) draw every state in
light and dark:

| Preview | What it shows | Design source |
| --- | --- | --- |
| Empty | "new chat", model name, meta line and tips; no "recent" | lines 120–140 |
| Empty · recent | the three latest conversations under "recent" | lines 132–139 |
| Thinking | the user bubble, then the lone blinking caret | lines 142–150, 178 |
| Streaming | the first paragraph with the trailing caret | lines 151–176 |
| Done | paragraph, list, code, meta with latency | lines 144–176 |
| Interrupted | the revealed blocks, meta ending "interrupted" | — (data's `INTERRUPTED`) |
| Long code block | a 12-line `ts` block still streaming, scrolling sideways | lines 167–172 |
| List | a reply ending in a numbered list | lines 157–163 |
| Composer · multi-line draft | the draft three lines tall | lines 241–248, `draftRows` 869 |
| Send disabled | a draft while the reply streams: Send in `--bg2` / `--fg3` | `sendStyle` 870 |
| Toast | "couldn’t send, try again" over the thread, the draft back | lines 531–534 |
| Copied · Gemini | "copied" in a code header, the Gemini accent | `copyText` 730 |

Criteria run on the Android host and the iOS simulator. Layout, colours and animation are checked
in the previews and on a device: there is no `runComposeUiTest`, see Decided.

Navigation and app

- [x] After sign-in, or with a signed-in user at launch, a new chat replaces the auth screen —
  `SignInViewModelTest.aValidSignInSignsInWithWhatWasTypedAndReplacesSignInWithANewChat`,
  `SignInViewModelTest.aValidSignUpCreatesTheAccountAndReplacesSignInWithANewChat`,
  `SignInViewModelTest.aGoogleTokenShowsAuthorizingUntilFirebaseAnswersThenReplacesSignInWithANewChat`,
  `SplashViewModelTest.aRestoredUserReplacesSplashWithANewChat`
- [x] `ChatEntry()` and `ChatEntry(42)` survive the back stack's save, next to every key the app
  registers — `ChatEntryProviderTest.aSavedBackStackRestoresANewChatAndAnOpenConversation`,
  `AppEntriesTest.theSavedBackStackRestoresEveryScreenTheAppShows`
- [x] Koin provides the entry provider, one `ChatTimes`, and a `ChatViewModel` for the entry it is
  given — `ChatModuleTest.koinProvidesTheEntryProviderAndOneChatTimes`,
  `ChatModuleTest.koinBuildsAChatViewModelForTheEntryItIsGiven`
- [x] `HomeEntry`, the starter `HomeScreen` and the template's `Greeting*`, `Platform*`,
  `AppLogic*Test` and `AppCommonTest` are gone, and `./gradlew detekt` passes with no failures —
  the build itself

Empty state and opening a conversation

- [x] A new chat shows the empty state on the default model with the three latest conversations,
  newest first, and their times — `ChatViewModelTest.aNewChatShowsTheEmptyStateOnTheDefaultModelWithTheThreeLatestConversations`,
  `ChatUiStateTest.onlyANewChatShowsTheEmptyState`,
  `ChatUiStateTest.aConversationIsARecentRowWithItsTitleAndLastUpdate`
- [x] A "recent" time is `HH:MM` for today in the device's time zone, else "Sep 13" —
  `ChatFormatsTest.aRecentRowShowsTheTimeForTodayAndTheDateBeforeThat`,
  `ChatFormatsTest.todayIsTheDeviceTimeZonesDay`, `ChatFormatsTest.chatTimesWriteInTheDevicesTimeZoneAgainstItsClock`
- [x] A tip's starred words are its emphasised runs — `ChatFormatsTest.aTipsStarredWordsAreItsEmphasisedRuns`
- [x] Tapping a "recent" row opens that conversation, with its model's accent from the tap, and a
  send continues it — `ChatViewModelTest.aRecentRowOpensThatConversationOnItsModel`,
  `ChatViewModelTest.aSendInAnOpenedConversationContinuesIt`
- [x] An entry with a conversation id opens it; one whose conversation is gone opens a new chat —
  `ChatViewModelTest.anEntryWithAConversationIdOpensThatConversation`,
  `ChatViewModelTest.anEntryForAConversationThatIsGoneOpensANewChat`
- [x] Each model's accent is the `ChatPalette` named after its id, the default for an unknown one —
  `ChatFormatsTest.everyModelHasThePaletteNamedAfterItsId`

Sending, new chat and the toast

- [x] Send empties the composer at once, creates the conversation, and the reply goes thinking →
  streaming with the caret → done with its latency —
  `ChatViewModelTest.aSendFromANewChatCreatesTheConversationAndFollowsItsReplyFromThinkingToDone`
- [x] Send is disabled for a blank draft and while the last reply thinks or streams, which the UI
  reads from the last message, not a flag of its own — `ChatViewModelTest.sendIsDisabledWhileAReplyIsOnItsWay`,
  `ChatViewModelTest.aBlankDraftCannotBeSent`, `ChatUiStateTest.sendNeedsTextAndNoReplyOrSendOnItsWay`,
  `ChatUiStateTest.aReplyIsInProgressOnlyWhileTheLastMessageIsThinkingOrStreaming`
- [x] A disabled Send is `--bg2` with a `--fg3` arrow, an enabled one the accent —
  `ChatIconButtonTest.aDisabledAccentButtonIsTheRaisedSurfaceWithTertiaryTextInEveryPalette`
- [x] When `send` returns `null` or throws, the text comes back to an untouched composer and the
  toast says "couldn’t send, try again" for 1400 ms; a newer toast replaces it and restarts the time —
  `ChatViewModelTest.whenNothingWasSentTheDraftComesBackAndTheToastShowsFor1400Ms`,
  `ChatViewModelTest.aSendThatFailsIsTreatedAsNothingSent`,
  `ChatViewModelTest.aDraftTypedWhileTheSendWasOnItsWayIsKeptWhenNothingWasSent`,
  `ChatViewModelTest.aNewToastReplacesTheShownOneAndRestartsItsTime`,
  `ChatToastTest.aToastShowsForTheDesignsFourteenHundredMilliseconds`
- [x] The toast inverts the screen's colours in every palette —
  `ChatToastTest.theToastInvertsTheScreenColorsInEveryPalette`,
  `ChatToastTest.providerCoversHiddenShortAndLongToastsInBothModes`
- [x] New chat returns to the empty state on the default model, keeps the draft and clears
  "copied"; a send that answers after it does not pull the old thread back —
  `ChatViewModelTest.newChatGoesBackToTheEmptyStateOnTheDefaultModelAndKeepsTheDraft`,
  `ChatViewModelTest.aSendThatAnswersAfterNewChatLeavesTheNewChatEmpty`
- [x] The composer is one line per line of the draft, from one to six —
  `ChatFormatsTest.theComposerIsOneLinePerLineOfTheDraftFromOneToSix`,
  `ChatUiStateTest.theComposerIsAsTallAsTheDraftsLines`

Messages and blocks

- [x] A user message shows its text and local `HH:MM` —
  `ChatUiStateTest.aUserMessageShowsItsTextAtItsLocalTime`,
  `ChatFormatsTest.clockTimesAreLocalTwentyFourHourHoursAndMinutes`
- [x] A done reply shows every block, no caret, and the meta "sonnet-4.5 · 14:02 · 0.8s" —
  `ChatUiStateTest.aDoneReplyShowsEveryBlockAndItsLatencyWithoutACaret`,
  `ChatFormatsTest.theReplyMetaJoinsModelTimeAndLatencyOrInterrupted`,
  `ChatFormatsTest.latencyIsInTenthsOfASecondWithAHalfRoundingUp`
- [x] While a reply streams only its last block has the caret, and a list never does —
  `ChatUiStateTest.onlyTheLastBlockOfAStreamingReplyHasTheCaret`,
  `ChatUiStateTest.aListHasNoCaretEvenWhileItStreamsLast`
- [x] A reply in progress with no block yet is the thinking caret —
  `ChatUiStateTest.aReplyInProgressWithNoBlockYetIsThinking`
- [x] An interrupted reply keeps what it revealed, without caret or latency, its meta ending
  "interrupted" — `ChatUiStateTest.anInterruptedReplyKeepsWhatItRevealedWithoutCaretOrLatency`
- [x] Code is coloured as the prototype's `tokenize` does: comments, strings (one still being
  typed included), numbers, capitalised types, `KW` keywords and calls, in `ChatColors.syntax` —
  `CodeTokensTest.*`
- [x] "copy" puts the block's text on the clipboard and its header reads "copied" for 1200 ms;
  another copy restarts that, and a block that is not code copies nothing —
  `ChatViewModelTest.copyHandsTheCodeToTheClipboardAndReadsCopiedFor1200Ms`,
  `ChatViewModelTest.anotherCopyRestartsTheCopiedTime`, `ChatViewModelTest.copyDoesNothingForABlockThatIsNotCode`,
  `ChatUiStateTest.aCodeBlockIsFoundByItsKey`
- [x] A streaming tick rebuilds only the reply that grew —
  `ChatViewModelTest.aStreamingTickRedrawsOnlyTheReplyThatGrew`,
  `ChatViewModelTest.theMessageCacheMapsEachUnchangedMessageOnce`
- [x] The previews cover every state above in light and dark, and other modules reach every
  `:core:ui` icon — `ChatUiStateTest.previewsCoverEveryStateInLightAndDark`,
  `ChatIconsTest.everyBundledIconIsReachableFromOtherModulesOnce`

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

## UI contract

What later rows extend, in `:feature:chat` (`br.com.weslleycampos.chat.feature.chat`) unless noted.

- **Key:** `@Serializable data class ChatEntry(val conversationId: Long? = null) : NavKey` in
  `:core:navigation`'s `entries.chat`; `null` is a new chat. `ChatEntryProvider` registers it.
- **Screen:** `ChatScreen(uiState, onHistoryClick, onModelClick, onNewChat, onOpenRecent,
  onDraftChange, onSend, onAttachClick, onVoiceClick, onCopyCode, modifier)`. `ChatEntryProvider`
  wires `onHistoryClick` (row 5), `onModelClick` (row 4), `onAttachClick` (row 8) and
  `onVoiceClick` (row 11) to `{}`, and wraps the screen in `ChatTheme(palette = uiState.model.palette)`.
- **State:** `ChatUiState(conversationId, model, messages: List<MessageUi>, recent:
  List<RecentConversationUi>, draft, replyInProgress, sending, copiedBlock: BlockKey?, toast:
  StringResource?)`, with `showsEmptyState`, `canSend` and `draftRows`. `MessageUi.User(id, time,
  text)` and `MessageUi.Assistant(id, time, modelShortName, latency, interrupted, thinking, blocks)`;
  `BlockUi.Paragraph`, `BlockUi.Code` (with `tokens`) and `BlockUi.NumberedList`, each with its
  `BlockKey(messageId, index)`.
- **ViewModel:** `ChatViewModel(@InjectedParam entry: ChatEntry, navigator, repository, times)`,
  `Navigator by navigator`, bound only to its own class: `onDraftChange`, `onSend`, `onNewChat`,
  `onOpenRecent(id)`, `onCopyCode(key, copy)`. Its toasts go through a private `showToast`.
- **`:core:ui`:** `ChatToast(message: String?, modifier)` (nothing while `null`) and
  `ChatToastDefaults.Duration` (1400 ms); `ChatIcons.Add`, `Back`, `Close`, `Memory`, `Menu`, `Mic`,
  `MicOff`, `NewChat`, `Search`, `Send`, `Settings`; `ChatIconButtonVariant.Accent`'s disabled look.
- **Formats:** `formatClockTime`, `formatRecentTime`, `formatLatency`, `assistantMeta`, `tokenize`
  and `ChatModel.palette` are public pure functions; `ChatTimes` (Koin single) reads the device's
  clock and time zone.

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
- Decided (UI): the accent is `ChatTheme(palette = uiState.model.palette)` around `ChatScreen` in
  `ChatEntryProvider`, so it follows the open conversation's model from the first frame. Row 4
  moves it to the app's root theme. `ChatModel.palette` is the `ChatPalette` entry named after the
  model's id (`gpt5` → `GPT5`), `ChatPalette.Default` for an id it does not know.
- Decided (UI): times are the prototype's `now()`, 24-hour local `HH:MM`, in the device's time
  zone, read on every format. A "recent" row shows `HH:MM` when the conversation was updated on
  today's local date, else the English month and day, "Sep 13", as the prototype's history items.
  `kotlinx-datetime` 0.8.0, the latest stable, converts `kotlin.time.Instant` to local dates.
- Decided (UI): the latency is tenths of a second, a half rounding up: 849 ms "0.8s", 850 ms
  "0.9s". A message's time is its `createdAt`; a reply's is the time of the message it answers.
- Decided (UI): "copy" writes plain text through Compose's `LocalClipboard`
  (`plainTextClipEntry`: `ClipData.newPlainText` on Android, `ClipEntry.withPlainText` on iOS).
  `ChatViewModel.onCopyCode(key, copy)` takes the write as a lambda, as SignIn takes its Google
  launcher, so the ViewModel stays platform-free. The header reads "copied" for 1200 ms and is a
  polite live region; a copy shows no toast, as in the prototype.
- Decided (UI): the only toast this row needs is "couldn’t send, try again", when `send` returns
  `null` or throws. The draft comes back if the composer is still empty, so nothing typed is lost.
  `ChatToast` (in `:core:ui`, for later rows' toasts) shows for `ChatToastDefaults.Duration`, 1400
  ms, fades in over 150 ms, and sits bottom centre over the thread, 24 dp above the composer (the
  prototype's 84 px from the bottom edge).
- Decided (UI): a "recent" row is the title on one line and the time; it opens that conversation in
  the same entry, taking its model from the recent list so the accent changes on the tap. "recent"
  is hidden when there is no conversation. Rows are at least 44 dp tall for touch (the prototype's
  36 px).
- Decided (UI): the only flag of the ViewModel's own is `sending`, while the call to `send` is on its
  way, so a double tap cannot send twice. Whether a reply is on its way is always the last message's
  `status.isInProgress`.
- Decided (UI): the thinking caret shows while a reply is in progress with no block yet. An
  interrupted reply shows what it revealed and no caret; one that revealed nothing shows its meta
  only.
- Decided (UI): the composer is a `BasicTextField` styled from the frame (`ChatTextField` is single
  line and 48 dp): at least as many lines as the draft has, six at most, then it scrolls. Enter adds
  a line; ⌘ / Ctrl + Enter sends from a hardware keyboard, as the prototype's `draftKey`.
- Decided (UI): the thread snaps to its end on every change of its messages (a send and each tick)
  and when it gets shorter (the keyboard opening), as the prototype's `scrollTop = scrollHeight`.
  The empty state scrolls when the keyboard leaves it too short. The screen pads for the safe area
  and the keyboard with `safeDrawingPadding()`.
- Decided (UI): until their rows, these do nothing: History (row 5), the model chip (row 4), "+"
  (row 8), a tap on the mic (row 11; hold to record is row 8), and a tap on a message (row 6, no
  handler at all). The tips keep the prototype's copy. The local-only banner is dropped.
- Decided (UI): the ViewModel lives in the entry's `ViewModelStore`, so the draft and the open
  conversation survive a configuration change. After process death the back stack restores the
  `ChatEntry` it was created with, usually a new chat: a conversation opened from "recent" is not
  written back to the key. `ChatViewModel` keeps `Navigator` for row 5's History.
- Decided (UI): `:core:ui` gains `ChatToast`, public `ChatIcons` handles (its `CoreUiRes` is
  internal, so other modules could not reach its drawables), and the Accent `ChatIconButton`'s
  disabled look: `--bg2` with `--fg3`, easing 150 ms between the two, instead of fading to 38 %.
  The model chip, code block, composer and top bar are used once and stay in `:feature:chat`.
- Decided (UI): no `runComposeUiTest`. The Android host has no Robolectric, so a Compose UI test
  cannot pass on both targets; the logic is tested through the ViewModel and pure functions, and the
  drawing through the previews.
- Decided (UI): Row 3 replaced the starter `HomeEntry` with `ChatEntry(conversationId: Long? = null)`
  in `core.navigation.entries.chat` and deleted the KMP template's starter code. `:app` keeps
  `AppEntriesTest`, which saves every key the app registers through the back stack's JSON.

## Out of scope

- Personas, favorites, the model picker and a default-model preference: rows 4 and 10.
- Pinned, archived, folders, search, delete and the full History list: row 5.
- Fork, retry and read-aloud: row 6. Compare pairs and `REPLY_ALT`: row 7. Attachments: row 8.
  Memory and the remember flag: row 9. `autoRead`: row 10.
- The prototype's `SEED` / `loadSeed` demo conversation: no demo data is seeded (queue decision).
- Stopping a reply: the design has no stop control.
- Syntax highlighting (`tokenize`), "copy" / "copied", the caret and the toast are UI work, in the
  UI criteria above.
