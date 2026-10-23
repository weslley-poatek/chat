# Compare mode

## Goal

The user turns compare on in the model picker, picks a second model, and every message they send is
answered by both models side by side, streaming at once. When one side's answer is done, "continue
with this →" goes on with that model alone. Compare is part of the conversation's model choice, so
it is kept with the conversation, after a restart too, and History shows the second model.

## Frames

Every line number refers to `.claude/screens/design/ctx Prototype.dc.html`; `frames.md` §2.2
("Compare pair", the "Compare" state, the composer and `send` / `startStream`), §2.7 (model picker),
§2.3 (History's extra), §3 (data model) and §6 quirk 12 describe them.

| Frame | States | Design source |
| --- | --- | --- |
| `Chat` compare pair | each side: thinking (a lone caret in the model's accent), streaming, done with "continue with this →"; the two sides finish on their own | lines 186–206; view model 813 (meta), 823 (`pair`, `keep`) |
| `Chat` send while comparing | `compareOn && compareId` at send time: a pair of `REPLIES[n]` and `REPLY_ALT[n]`; otherwise a single reply | `send` 683–695, `startStream` 696–722, `REPLY_ALT` 600–607 |
| `Chat` chip and composer | chip "sonnet-4.5 ⇄ gpt-5"; placeholder "ask sonnet-4.5 and gpt-5…" | `chipLabel` 888, placeholder 897 |
| `Model picker` compare states | "compare" / "compare · on"; hint "pick a second model to run beside sonnet-4.5" / "sonnet-4.5 ⇄ gpt-5 · tap another model to swap the second"; "◐" on the second model | header 484, hint 487, `mRow` 848–853, `toggleCompare` and `compareHint` 929–931 |
| `Toast` | "comparing sonnet-4.5 ⇄ gpt-5" (pick), "continuing with gpt-5" (keep) | `mRow.pick` 852, `keep` 823 |
| `History` row extra | "⇄ gpt-5" | seed `h4` 561; frames.md §2.3 |

## Acceptance criteria

Each criterion is one observable behaviour with at least one test that checks it. The data
criteria run on the Android host and the iOS simulator unless marked iOS.

### Database (`:core:database`, version 6)

- [x] Given a new in-memory database, when it opens, then it is on version 6, and
  `schemas/…ChatDatabase/6.json` records its identity hash —
  `ChatDatabaseTest.inMemoryDatabaseOpensOnTheCurrentVersion`,
  `ExportedSchemaTest.currentVersionIsExportedWithTheCompiledIdentityHash`
- [x] Given a version 5 database with conversations and messages, when it migrates, then version 6
  validates and keeps every row, each conversation not comparing and each message on no pair side
  (iOS) — `MigrationTestHelperTest.migratesVersionFiveToSixKeepingEveryConversationNotComparingAndEveryMessageOnNoPairSide`
- [x] A new conversation is not comparing, and a compare choice is stored with it —
  `CompareDaoTest.aNewConversationIsNotComparingAndACompareChoiceIsStoredWithIt`
- [x] Setting the compare rewrites only its two columns, leaves the order alone, and says whether
  it found the conversation —
  `CompareDaoTest.settingTheCompareRewritesOnlyItsTwoColumnsLeavesTheOrderAloneAndSaysWhetherItFoundTheConversation`
- [x] A pair read from its first side is its two sides in the order they were written —
  `CompareDaoTest.aPairFromItsFirstSideIsItsTwoSidesInTheOrderTheyWereWritten`
- [x] A reply keeps its pair side; a user message or a single reply has none —
  `MessageDaoTest.aReplyKeepsItsPairSideAndAUserMessageOrASingleReplyHasNone`
- [x] A compare pair counts as one reply — `MessageDaoTest.aComparePairCountsAsOneReply`
- [x] Updating several replies rewrites each only in its blocks, status and latency and says how
  many are still there —
  `MessageDaoTest.updatingSeveralRepliesRewritesEachOnlyInItsBlocksStatusAndLatencyAndSaysHowManyAreStillThere`
- [x] A pair is unfinished while either side thinks or streams —
  `MessageDaoTest.aPairIsUnfinishedWhileEitherSideThinksOrStreams`
- [x] The search matches the model a conversation is compared with, only while compare is on —
  `HistoryDaoTest.searchMatchesTheModelAConversationIsComparedWithOnlyWhileCompareIsOn`
- [x] Koin provides `CompareDao` on the one database (iOS) —
  `CoreDatabaseModuleTest.koinProvidesTheDaosAndATransactionRunnerOnThatDatabase`

### Data (`:data:chat`)

The compare choice

- [x] A model or persona row is not comparing; a choice compares with its second model only while
  compare is on, no persona answers and the second model is another model —
  `ModelChoiceTest.aModelOrPersonaRowIsNotComparing`,
  `ModelChoiceTest.aChoiceComparesWithItsSecondModelOnlyWhileCompareIsOnNoPersonaAnswersAndItIsAnotherModel`
- [x] The compare button turns compare on with the model and off dropping the second model, and
  the persona goes either way —
  `ModelChoiceTest.theCompareButtonTurnsCompareOnWithTheModelAndOffDroppingTheSecondModelAndThePersonaGoesEitherWay`
- [x] While comparing, a model row becomes the second model and the first model's own row changes
  nothing (`picked` is `null`) —
  `ModelChoiceTest.whileComparingAModelRowBecomesTheSecondModelAndTheFirstModelsOwnRowChangesNothing`
- [x] A persona row turns compare off; with compare off a model row picks that model alone —
  `ModelChoiceTest.aPersonaRowTurnsCompareOffAndWithCompareOffAModelRowPicksThatModelOnItsOwn`
- [x] A conversation's choice carries its compare, and it compares as its choice does —
  `ModelChoiceTest.aConversationsChoiceCarriesItsCompareAndItComparesAsItsChoiceDoes`
- [x] A comparing row reads with its compare, and a second model the catalogue no longer has as
  none — `EntityMappersTest.aComparingRowReadsWithItsCompareAndASecondModelTheCatalogueNoLongerHasAsNone`
- [x] The compare button and a second model are stored with the model in one transaction, and keep
  the conversation's place in "recent" and its `updatedAt` —
  `RoomChatRepositoryCompareTest.theCompareButtonAndASecondModelAreStoredWithTheModelInOneTransactionAndKeepThePlaceInRecent`
- [x] A persona turns compare off and turning compare off drops the second model, in the rows too —
  `RoomChatRepositoryCompareTest.aPersonaTurnsCompareOffAndTurningCompareOffDropsTheSecondModel`,
  `RoomChatRepositoryCompareTest.aPersonaChoiceIsStoredWithCompareOffWhateverItSaysAboutCompare`
- [x] A second model that is the model itself or not in the catalogue is refused by `choose` and by
  a first `send`, writing nothing —
  `RoomChatRepositoryCompareTest.aSecondModelThatIsTheModelItselfOrNotInTheCatalogueIsRefusedAndChangesNothing`
- [x] A conversation still compares after a restart, and its next send is a pair again —
  `RoomChatRepositoryCompareTest.aConversationStillComparesAfterARestartAndItsNextSendIsAPairAgain`

Pairs

- [x] A first send while comparing creates a comparing conversation answered by a thinking pair:
  sonnet's side first, then gpt-5's, with no persona, before `send` returns —
  `RoomChatRepositoryCompareTest.aFirstSendWhileComparingCreatesAComparingConversationAnsweredByAThinkingPair`
- [x] With compare on but no second model, a send is answered by the model alone —
  `RoomChatRepositoryCompareTest.withCompareOnButNoSecondModelASendIsAnsweredByTheModelAlone`
- [x] A stored compare that cannot pair (a persona, a retired second model, the model itself) is
  answered by a single reply —
  `RoomChatRepositoryCompareTest.aStoredCompareThatCannotPairIsAnsweredByASingleReply`
- [x] The two sides stream on the same ticks, `REPLIES[0]` and `REPLY_ALT[0]`, each done with its
  own latency (79 and 186 ticks of 28 ms at the slowest pace), one emission a tick —
  `RoomChatRepositoryCompareTest.aPairsSidesStreamOnTheSameTicksWithTheReplyAndItsAlternativeEachDoneWithItsOwnLatency`,
  `ReplyStreamerTest.aPairsSidesThinkOnceThenRevealOnTheSameTicksBothInOneWrite`,
  `ReplyStreamerTest.eachSideOfAPairIsDoneOnItsOwnTickWithItsOwnLatencyAndTheOtherPlaysOnAlone`
- [x] A send waits for both sides — `RoomChatRepositoryCompareTest.aSendWaitsForBothSidesOfThePair`
- [x] A pair counts as one reply in the rotation: side 0 plays `REPLIES[n]`, side 1 `REPLY_ALT[n]` —
  `RoomChatRepositoryCompareTest.aPairCountsAsOneReplyInTheRotationOfCannedReplies`,
  `CannedRepliesTest.theAlternativesRotateInStepWithTheReplies`
- [x] `REPLY_ALT` is the prototype's, block by block and to the character —
  `CannedRepliesTest.theAlternativesAreThePrototypesReplyAltBlockByBlockAndToTheCharacter`
- [x] A pair still playing when the app was closed has both sides interrupted when the repository
  starts (a side already done stays done) —
  `RoomChatRepositoryCompareTest.aPairStillPlayingWhenTheAppWasClosedHasBothSidesInterruptedWhenTheRepositoryStarts`
- [x] A pair whose stream is cancelled has both sides interrupted with what they revealed; one
  already done stays done — `RoomChatRepositoryCompareTest.aPairWhoseStreamIsCancelledHasBothSidesInterruptedWithWhatTheyRevealed`,
  `ReplyStreamerTest.aPairCancelledMidwayLeavesBothSidesInterruptedWithWhatTheyRevealed`,
  `ReplyStreamerTest.aPairCancelledAfterOneSideIsDoneInterruptsOnlyTheOtherSide`
- [x] Deleting a comparing conversation while its pair streams stops both sides at the next tick —
  `RoomChatRepositoryCompareTest.deletingAComparingConversationWhileItsPairStreamsStopsBothSides`,
  `ReplyStreamerTest.aPairWhoseRowsAreGoneStopsOnTheWriteThatFindsNothingAndEndsQuietly`
- [x] A pair send writes its conversation, the message and both sides in one transaction —
  `RoomChatRepositoryCompareTest.aPairSendWritesItsConversationTheMessageAndBothSidesInOneTransaction`
- [x] A pair's two rows read as one `ComparePair` on its first side's id and time, in thread order;
  a side without its partner reads as a reply of its own; a pair is in progress while either side
  is — `ThreadMappersTest.aPairsTwoRowsReadAsOneComparePairOnItsFirstSidesIdAndTime`,
  `ThreadMappersTest.singleRepliesAndPairsReadInTheOrderTheyWereWritten`,
  `ThreadMappersTest.aSideWithoutItsPartnerReadsAsAReplyOfItsOwn`,
  `ThreadMappersTest.aPairIsInProgressWhileEitherSideThinksOrStreams`

Continue with this →

- [x] Continuing with a side makes its model answer alone with compare off, and keeps the place in
  "recent", `updatedAt` and the pair as it is —
  `RoomCompareRepositoryTest.continuingWithTheSecondSideMakesItsModelAnswerAloneAndKeepsThePlaceInRecentAndThePair`,
  `RoomCompareRepositoryTest.continuingWithTheFirstSideKeepsTheModelAndTurnsCompareOff`
- [x] The next send is a single reply from that model, going on with the canned replies —
  `RoomCompareRepositoryTest.theNextSendAfterContinuingIsASingleReplyFromThatModelGoingOnWithTheCannedReplies`
- [x] Only a done side can be continued with, even while the other still streams, and the next
  send waits for that one — `RoomCompareRepositoryTest.aDoneSideCanBeContinuedWithWhileTheOtherStillStreamsAndTheNextSendWaitsForIt`,
  `RoomCompareRepositoryTest.anInterruptedSideCannotBeContinuedWith`
- [x] An unknown conversation, pair or side, or a model the catalogue no longer has, changes
  nothing — `RoomCompareRepositoryTest.aConversationPairOrSideThatIsNotThereOrAModelTheCatalogueNoLongerHasChangesNothing`
- [x] Continuing writes the model and the compare in one transaction —
  `RoomCompareRepositoryTest.continuingWritesTheModelAndTheCompareInOneTransaction`
- [x] Koin provides one `RoomCompareRepository` as `CompareRepository` —
  `DataChatModuleTest.koinProvidesOneRoomCompareRepositoryOnTheDatabasesDaos`

Fork and History

- [x] A fork after a pair copies both its sides and counts the pair as one message —
  `RoomForkRepositoryTest.aForkAfterAComparePairCopiesBothItsSidesAndCountsThePairAsOneMessage`
- [x] A fork at a pair, or at either side's id, is refused and writes nothing —
  `RoomForkRepositoryTest.aForkAtAComparePairOrEitherOfItsSidesIsRefused`
- [x] A fork of a comparing conversation compares as its source does, leaves a pair on its way
  behind, and its next send is a pair —
  `RoomForkRepositoryTest.aForkOfAComparingConversationComparesAsItsSourceDoesAndItsNextSendIsAPair`
- [x] History's rows carry the compared model (the "⇄ gpt-5" extra) and the search finds it only
  while comparing; continuing drops both —
  `RoomChatRepositoryCompareTest.historyShowsTheComparedModelAsItsExtraAndAQueryFindsItOnlyWhileComparing`,
  `RoomCompareRepositoryTest.continuingDropsHistorysExtraAndTheSearchByTheComparedModel`

### UI (`:feature:chat`, `:feature:history`)

<!-- FEATURE AGENT: replace this placeholder with the UI acceptance criteria (compare cards,
"continue with this →", the picker's compare states, chip, placeholder, toasts, History's extra,
previews), each naming its test. -->

_To be written by the feature agent._

## Data contract

- **Module and package:** `:data:chat`, `br.com.weslleycampos.chat.data.chat` (models in `….models`).
- **Repository:** a new interface next to `ChatRepository` and `ForkRepository`:

  ```kotlin
  interface CompareRepository {
      suspend fun continueWith(conversationId: Long, pairMessageId: Long, side: Int): Boolean
  }
  ```

  `ChatRepository`'s signatures are unchanged; `send`, `choose`, `observeMessages` and
  `observeHistory` carry compare through `ModelChoice`, `Message.ComparePair` and `Conversation`.
- **Models:**
  - `ModelChoice(model, persona = null, compareOn = false, compareWith: ChatModel? = null)`, with
    `comparing: ChatModel?` (`compareWith` while `compareOn`, no persona and not `model` itself),
    `toggledCompare(): ModelChoice` (the compare button) and `picked(row: ModelChoice): ModelChoice?`
    (a picker row's tap; `null` = no-op, the sheet stays open).
  - `Conversation(…, compareOn = false, compareWith: ChatModel? = null)`, with `choice` carrying both
    and `comparing: ChatModel?` (History's extra).
  - `Message.ComparePair(id, conversationId, sides: List<CompareSide>, createdAt)`, `isInProgress`;
    `CompareSide(id, model, blocks, status: ReplyStatus, latency: Duration?)`. `id` is the first
    side's row id; `sides[0]` is the conversation's model, `sides[1]` the compared one.
- **Storage:** Room version 6 with `AutoMigration(from = 5, to = 6)`, schema `6.json`: on
  `conversations`, `compareOn INTEGER NOT NULL DEFAULT 0` and `compareModelId TEXT`; on `messages`,
  `pairSide INTEGER` (`null`, `PAIR_FIRST_SIDE` = 0, `PAIR_SECOND_SIDE` = 1). New `CompareDao`
  (`setCompare(id, compareOn, compareModelId): Int`, `pairFrom(conversationId, firstSideId)`), and
  `MessageDao.updateReplies(progress)`; `countReplies` counts a pair once, and `HistoryDao.search`
  also matches `compareModelId` while `compareOn`.
- **Koin binding:** `@Single internal fun compareRepository(conversationDao, compareDao,
  transactions): CompareRepository` → `RoomCompareRepository`, in `ChatBindings.kt`;
  `chatRepository` gains `compareDao`. Inject `CompareRepository` only.
- **Behaviour:**
  - `choose(id, choice)` writes the model, persona and compare in one transaction: a persona is
    stored with compare off, compare off with no second model, compare on with or without one. It
    returns `false` and writes nothing for a second model that is the model itself or not in the
    catalogue. It does not move `updatedAt`.
  - `send(null, text, newChat)` creates the conversation on `newChat`'s compare as well, with the
    same rules and refusals (`null`). A conversation that exists sends on its own stored compare.
  - While the conversation's choice `comparing` is set, `send` saves the user's message and two
    `THINKING` rows, the conversation's model first, and returns. After one think delay (320–619 ms)
    both sides tick every 28 ms, one database transaction per tick, side 0 revealing
    `REPLIES[n]` and side 1 `REPLY_ALT[n]`, where n counts the conversation's replies with a pair
    once. Each side turns `DONE` on its own tick with its own latency (from the end of thinking);
    the other plays on alone. `observeMessages` emits one `ComparePair` per tick with both sides.
  - A send is refused (`null`) while either side is in progress. A restart marks both unfinished
    sides `INTERRUPTED`; a cancelled stream does the same; deleting the conversation stops both.
  - `continueWith(conversationId, pairMessageId, side)` sets the model to that side's model, no
    persona, compare off, in one transaction, and returns `true`; `false` and no write for an
    unknown conversation or pair, `side` outside 0..1, a side that is not `DONE` (the other side may
    still stream), or a model no longer in the catalogue. The pair stays in the thread as it is.
  - `ForkRepository.fork` refuses a pair (by its id or either side's), copies a pair before the
    message whole, counts it as one message, and gives the fork the source's compare.
  - History: `Conversation.comparing` is the "⇄ <short>" extra; the search also matches its short
    name. Only while compare is on: continuing, toggling off or a persona drops it.
- **What the feature builds on:**
  - Picker: the compare button → `choose(id, current.toggledCompare())`, or for a new chat keep the
    result as the next `newChat`. A row tap → `current.picked(row)`: `null` keeps the sheet open
    (the primary model while comparing); otherwise `choose` / keep it, and when the result's
    `comparing` is set toast `"comparing ${model.shortName} ⇄ ${comparing.shortName}"`. "◐" marks
    `choice.comparing`; "●" the model (or persona) as before.
  - Chip `"${model.shortName} ⇄ ${comparing.shortName}"`, placeholder `"ask ${a} and ${b}…"` and
    the hint ("pick a second model to run beside a" / "a ⇄ b · tap another model to swap the
    second") read `choice.compareOn` and `choice.comparing`.
  - Thread: `Message.ComparePair` → the two cards; a side with no blocks while in progress is the
    lone caret; "continue with this →" on a `DONE` side → `continueWith(conversationId, pair.id,
    index)`, toast `"continuing with ${side.model.shortName}"` on `true`. Pair meta
    `"a ⇄ b · time"`, no latency. A pair is not selectable (no action row, no read aloud, no fork).
  - History row: `conversation.comparing?.let { "⇄ ${it.shortName}" }`.

## Decided

- Decided: compare lives in `ModelChoice` (`compareOn`, `compareWith`) and is persisted per
  conversation (`compareOn`, `compareModelId`), so a new chat passes it like the model and persona,
  in `send(null, text, newChat = …)`, and `choose` writes it with the model. The prototype keeps it
  in transient chat state; storing it lets a reopened conversation compare as it was left.
- Decided: `choose` rules, from the prototype's `toggleCompare` and `mRow.pick`: a persona clears
  compare; turning compare off clears the second model; picking the primary model while comparing
  is a no-op (`picked` returns `null`, no call). Compare on before a second model is picked sends a
  single reply, as the prototype's `compareOn && compareId`.
- Decided: the data layer refuses what the picker cannot make (a second model equal to the first
  or outside the catalogue) instead of storing it, and reads a stored one that cannot pair (a
  persona, a retired second model) as a single reply.
- Decided: a pair is two assistant rows with `pairSide` 0 and 1, written one after the other in the
  send's transaction, so `observeMessages`' `ORDER BY id` keeps them together and in order with no
  new query. The two rows are grouped into one `Message.ComparePair`, in the place a reply takes,
  on the first side's id. Sides have no persona: compare and personas never mix.
- Decided: one think delay, then both sides on one 28 ms tick, as the prototype's one interval
  over two cursors; a tick of both sides is one `updateReplies` transaction, so observers see them
  grow together. Each side's latency is from the end of thinking to its own last tick, as `lat`;
  the prototype's pair meta shows none, but it is kept for a card that wants it.
- Decided: rotation counts a pair as one reply (`countReplies` counts side 0 only), as the
  prototype's `sentCount`: side 0 plays `REPLIES[n]` and side 1 `REPLY_ALT[n]`.
- Decided: "continue with this →" is a new `CompareRepository` (`RoomCompareRepository`) instead of
  `ChatRepository`, which has ten functions: an eleventh passes detekt's `TooManyFunctions` limit
  on the interface and on `RoomChatRepository`, as row 6 did with `ForkRepository`.
- Decided: `continueWith` needs a `DONE` side, as the prototype shows the button on `p.done`, even
  while the other side streams; the next send waits for that side. It neither records which side
  was kept nor moves `updatedAt` (it is a model choice), and the pair keeps both buttons, as the
  prototype draws them on every done side.
- Decided: a fork at a pair is refused (a pair has no action row, and a fork ending on one side
  would orphan its partner). A fork after a pair copies both sides; the pair counts as one message
  in the fork's number, as the prototype's one `pair` entry. The fork takes the source's compare
  with its model and persona, so it answers as its source would.
- Decided: History's extra is the conversation's current second model while compare is on
  (`Conversation.comparing`), matching the seed's "⇄ gpt-5"; the search matches its short name
  too, as it matches the model's.
- Decided: a new chat starts from `ModelChoice(defaultModel)`, so compare is off, as the
  prototype's `newChat` keeps it only within the session; the UI applies this. The Default model
  sheet (Settings) has no compare: its frames quirk 12 is not reproduced.

## Out of scope

- The compare cards, carousel, picker states, toasts and History's extra drawing: the feature
  agent's part of this row.
- Comparing more than two models, or a persona in a compare: the prototype has neither.
- Selecting, copying, reading aloud or forking a pair: it has no action row.
- Auto read of a pair: the prototype never auto-reads a pair (row 10).
