# Voice mode as home

## Goal

After sign-in the app opens on voice mode: the sphere listens, thinks and speaks on a timer, as the
design does, with the design's canned lines, since there is no microphone and no model. Every
exchange is saved on the device as a conversation, the user's transcript and the reply, so voice
sessions are listed in History, open there as text in the thread, and a typed conversation can go
on in voice mode where it left off. Picking a model on Voice also makes it the default for new
sessions, since Settings no longer has "Default model".

## Frames

Every line number refers to `.claude/screens/design/ctx Prototype.dc.html`; `frames.md` §2.6
(Voice mode), §1.3 (state and timers), §2.7 (model picker) and §3 (data model) describe them.

| Frame | States | Design source |
| --- | --- | --- |
| `Voice mode` | `voicePhase` `listening` (6500 ms) → `thinking` (2400 ms) → `speaking` (7000 ms) → `listening`, a tap skipping to the next phase; `voiceWords` 0…n, one more every 230 ms; `voiceMuted` true / false | markup 444–474; `startVoice` … `stopVoice` 737–753; transcript 855–858; bindings 922–926 |
| `Voice mode` transcript | listening: `USER_T` revealed word by word; thinking: `USER_T` whole; speaking: `MODEL_T` revealed word by word | `USER_T` / `MODEL_T` 608–609; 855–858 |
| `Voice mode` bar | History (left) → History; model chip (accent dot, short name, centred) → model picker; Settings (right), wired by row 10; no back chevron, since Voice is home (queue row 8) | lines 448–453 |
| `Model picker` from Voice | the chat sheet with no compare button; a model or persona row picked for the session | §2.7, markup 476–518 |
| `Voice mode` controls | mute / unmute, end, "Aa" (text) | lines 459–472, 924–926 |

## Acceptance criteria

Each criterion is one observable behaviour with at least one test that checks it. The data
criteria run on the Android host and the iOS simulator.

### Database (`:core:database`, still version 6)

No schema change: the database stays on version 6 and `6.json` is unchanged. Voice mode adds two
queries to `MessageDao`.

- [x] A conversation's user messages are counted, replies and pair sides not, and nothing of
  another conversation — `MessageDaoTest.userMessagesAreCountedPerConversationAndRepliesAreNot`
- [x] A conversation's last message is the one written last, whatever its timestamp; `null` for a
  conversation with none or no conversation —
  `MessageDaoTest.theLastMessageIsTheOneWrittenLastWhateverItsTimestamp`

### Data (`:data:chat`)

The canned lines

- [x] The first exchange is the prototype's `USER_T` and `MODEL_T` to the character (24 and 33
  words) — `VoiceLinesTest.theFirstExchangeIsThePrototypesUserTAndModelTToTheCharacter`
- [x] Three more exchanges follow, each line different from the others and short enough to reveal
  within its phase at one word every 230 ms (at most 28 words heard, 30 said) —
  `VoiceLinesTest.aFewMoreExchangesFollowEachLineDifferentAndShortEnoughToRevealWithinItsPhase`
- [x] A conversation's exchanges rotate through the lines in order, the fifth the first again —
  `VoiceLinesTest.aConversationsExchangesRotateThroughTheLinesInOrder`,
  `RoomVoiceRepositoryTest.exchangesRotateThroughTheLinesAndTheSessionMovesOnAsSoonAsListeningEnds`

The session

- [x] A new session plays the first exchange on the default model, follows the default, and saves
  nothing until a transcript is saved —
  `RoomVoiceRepositoryTest.aNewSessionPlaysTheFirstExchangeOnTheDefaultModelFollowsTheDefaultAndSavesNothing`
- [x] A conversation's session emits when a transcript is saved (its next exchange moves on) and
  when its model or persona changes, but not for a reply —
  `RoomVoiceRepositoryTest.theSessionEmitsWhenATranscriptIsSavedOrTheChoiceChangesButNotForTheReply`
- [x] A conversation that is not there, never created or deleted, has no session (`null`) and saves
  nothing — `RoomVoiceRepositoryTest.aConversationThatIsNotThereHasNoSessionAndSavesNothing`

Saving an exchange

- [x] The first transcript creates a conversation titled after it by the first-message rule
  ("So the debounce hook works, but I also w") on the default model, with the transcript as its
  user message — `RoomVoiceRepositoryTest.theFirstTranscriptCreatesAConversationTitledAfterItOnTheDefaultModel`
- [x] It starts on the persona or model picked before it, compare dropped, and leaves the default
  alone — `RoomVoiceRepositoryTest.theFirstTranscriptStartsOnThePersonaOrModelPickedBeforeItWithoutCompareAndLeavesTheDefaultAlone`
- [x] A new-session choice not in the catalogue or not stored saves nothing —
  `RoomVoiceRepositoryTest.aNewSessionChoiceThatIsNotInTheCatalogueOrNotStoredSavesNothing`
- [x] The reply is saved `DONE` and whole, one paragraph of the exchange's reply, from the
  conversation's model and persona, at its transcript's time, with the thinking time (2400 ms) as
  its latency, without moving the conversation —
  `RoomVoiceRepositoryTest.theReplyIsSavedDoneAndWholeAsOneParagraphFromTheModelAndPersonaWithTheThinkingTimeAsItsLatency`
- [x] A tap that skips thinking saves the reply with the shorter thinking time —
  `RoomVoiceRepositoryTest.aTapThatSkipsThinkingSavesTheReplyWithTheShorterThinkingTime`
- [x] A transcript and a reply are each one write transaction —
  `RoomVoiceRepositoryTest.aTranscriptAndAReplyAreEachWrittenInOneTransaction`

Continuing a conversation

- [x] A typed conversation goes on in voice mode from its exchanges (its user messages), keeps its
  title and moves to the top of History —
  `RoomVoiceRepositoryTest.aTypedConversationGoesOnInVoiceFromItsExchangesAndMovesToTheTopOfHistory`
- [x] A transcript in an archived conversation brings it back to History —
  `RoomVoiceRepositoryTest.aTranscriptInAnArchivedConversationBringsItBackToHistory`
- [x] A voice conversation reopened after a restart goes on with the rotation —
  `RoomVoiceRepositoryTest.aVoiceConversationReopenedAfterARestartGoesOnWithTheRotation`
- [x] A transcript while a typed reply is still thinking or streaming saves nothing; once it is
  done, the transcript is saved — `RoomVoiceRepositoryTest.aTranscriptWhileATypedReplyIsStillOnItsWaySavesNothing`
- [x] A reply an earlier run left thinking does not hold up the first transcript: it reads
  interrupted first — `RoomVoiceRepositoryTest.aReplyAnEarlierRunLeftThinkingDoesNotHoldUpTheFirstTranscript`
- [x] A comparing conversation is answered in voice by its model alone, a single reply, and keeps
  comparing in text —
  `RoomVoiceRepositoryTest.aComparingConversationIsAnsweredInVoiceByItsModelAloneAndKeepsComparingInText`

Ending early

- [x] A session ended while thinking keeps the transcript with no reply, nothing left thinking; the
  conversation is in History, its next exchange is the following line, and a typed send goes on
  from it —
  `RoomVoiceRepositoryTest.aSessionEndedWhileThinkingKeepsTheTranscriptWithNoReplyAndTheConversationGoesOnFromIt`
- [x] A session ended while speaking keeps the reply whole and done, and a turn is answered only
  once — `RoomVoiceRepositoryTest.aSessionEndedWhileSpeakingKeepsTheReplyWholeAndATurnIsAnsweredOnlyOnce`
- [x] A turn is not answered after a typed message or once its conversation is deleted —
  `RoomVoiceRepositoryTest.aTurnIsNotAnsweredAfterATypedMessageOrOnceItsConversationIsGone`

History

- [x] Voice conversations are listed in History, counted, in "recent" and found by their
  transcripts; they open as text (the user's message, then a done reply), and a typed send goes on
  with the next canned typed reply —
  `RoomVoiceRepositoryTest.voiceConversationsAreListedInHistoryAndOpenAsTextLikeAnyOther`

The model choice

- [x] A pick on a new session makes its model the default for new sessions and new chats; a
  persona's own model becomes the default, and the persona is passed with the first transcript —
  `RoomVoiceRepositoryTest.choosingOnANewSessionMakesItsModelTheDefaultForNewSessionsAndChats`
- [x] A pick in a conversation changes its model or persona and the default, and turns compare off —
  `RoomVoiceRepositoryTest.choosingInAConversationChangesItsChoiceAndTheDefaultAndTurnsCompareOff`
- [x] A choice not in the catalogue, not stored, or for a conversation that is not there changes
  nothing, the default included —
  `RoomVoiceRepositoryTest.aChoiceThatIsNotInTheCatalogueNotStoredOrForAConversationThatIsNotThereChangesNothing`
- [x] Koin provides one `RoomVoiceRepository` as `VoiceRepository` —
  `DataChatModuleTest.koinProvidesOneRoomVoiceRepositoryOnTheChatAndModelRepositories`

Every `RoomChatRepository*Test`, `RoomForkRepositoryTest` and `RoomCompareRepositoryTest` still
passes on the shared `TurnWriter` and `StoredChoice`, which moved out of `RoomChatRepository.kt`
unchanged.

### UI (`:feature:voice`, `:core:navigation`, `:core:ui`, `:feature:auth`, `:app`)

The screen is `VoiceScreen` in `:feature:voice`, stateless, over one `VoiceUiState` from
`VoiceViewModel`, in the `ChatTheme` of what answers the session. Its previews
(`VoiceScreenPreviewParameterProvider`, 402 × 874) draw every state in light and dark with the
prototype's `USER_T` / `MODEL_T`:

| Preview | What it shows | Design source |
| --- | --- | --- |
| Listening · starting | "LISTENING", the whole `USER_T` in `--fg3`, nothing revealed yet | lines 455–458, 855–858 |
| Listening | 13 words of `USER_T` (3 s in) in `--fg2`, the rest in `--fg3` | 856–858 |
| Thinking | "THINKING", the whole `USER_T` in `--fg2`; the sphere as a tilted ring | 857, 754–793 |
| Speaking | 13 words of `MODEL_T`, the rest to come; the glow at 0.22 | 856–858, 923 |
| Speaking · time up | 30 of `MODEL_T`'s 33 words, its last three still `--fg3` as speaking ends | 856–858 (see Decided in the data part) |
| Muted | mute filled `--fg` with the crossed-out mic, label "unmute"; the listening sphere stilled | 460–462, 924 |
| Persona | the code reviewer answering: chip "sonnet-4.5", the Claude accent | 450, 851 |
| Gemini | chip "gemini-2.5-pro" and the sphere, glow, dot and label in Gemini's blue | 450, 859 |
| Loading | the session not read yet: the label and no line | — |

Criteria run on the Android host and the iOS simulator. Layout, colours, the sphere's motion and the
sheet are checked in the previews and on a device: there is no `runComposeUiTest`, as in rows 3–7
(no JVM target and no Robolectric on the Android host). The phases, the words, every call to the
repository and the navigation are ViewModel state on virtual time, tested below with fakes.

Home and navigation

- [x] With a signed-in user at launch, or after any sign-in (Google, email, a new account), Voice
  replaces the auth screen as the only entry of the back stack, so back from it leaves the app —
  `SplashViewModelTest.aRestoredUserReplacesSplashWithVoiceTheRootThatBackLeavesTheAppFrom`,
  `SignInViewModelTest.aValidSignInSignsInWithWhatWasTypedAndReplacesSignInWithVoice`,
  `SignInViewModelTest.aValidSignUpCreatesTheAccountAndReplacesSignInWithVoice`,
  `SignInViewModelTest.aGoogleTokenShowsAuthorizingUntilFirebaseAnswersThenReplacesSignInWithVoice`
- [x] `VoiceEntry()` and `VoiceEntry(42)` survive the back stack's save, next to every key the app
  registers — `VoiceEntryTest.aSavedBackStackRestoresANewSessionAndAConversationGoingOnInVoice`,
  `VoiceEntryTest.aNewSessionIsTheDefault`,
  `VoiceEntryProviderTest.aSavedBackStackRestoresANewSessionAndAConversationGoingOnInVoice`,
  `AppEntriesTest.theSavedBackStackRestoresEveryScreenTheAppShows`
- [x] Koin provides the entry provider and a `VoiceViewModel` for the entry it is given, on the
  app's navigator — `VoiceModuleTest.koinProvidesTheEntryProvider`,
  `VoiceModuleTest.koinBuildsAVoiceViewModelForTheEntryItIsGivenOnTheAppsNavigator`
- [x] The History button stops the phases and opens History over Voice; back returns to Voice —
  `VoiceViewModelSessionTest.historyStopsThePhasesAndOpensOverVoiceWhichBackReturnsTo`
- [x] "Aa" stops the phases and opens the session as text, `ChatEntry(conversationId)`, over Voice;
  back returns to Voice, which listens again on the conversation's next exchange —
  `VoiceViewModelSessionTest.textStopsThePhasesAndOpensTheSessionAsTextOverVoice`
- [x] "Aa" before the first transcript opens a new chat, `ChatEntry()`, and saves nothing —
  `VoiceViewModelSessionTest.textBeforeTheFirstTranscriptOpensANewChat`

The phases

- [x] Listening lasts 6500 ms, thinking 2400 ms and speaking 7000 ms, then listening again, round
  and round — `VoiceViewModelTest.listeningLasts6500MsThinking2400MsAndSpeaking7000MsThenListeningAgainForever`,
  `VoicePhasesTest.listeningLasts6500MsThinking2400MsAndSpeaking7000Ms`,
  `VoicePhasesTest.thePhasesLoopListeningThinkingSpeakingAndBack`
- [x] One more word shows every 230 ms, from none at the start of each phase —
  `VoiceViewModelTest.aWordShowsEvery230MsAndEachPhaseStartsAgainFromNone`,
  `VoicePhasesTest.aWordShowsEvery230Ms`
- [x] A tap on the sphere ends the phase at once, and the next phase gets its whole time —
  `VoiceViewModelTest.aTapEndsThePhaseAtOnceAndTheNextOneGetsItsWholeTime`
- [x] The caption: listening reveals the transcript word by word, the rest of the sentence still to
  come; thinking shows it whole; speaking reveals the reply, its last three words still to come
  when its 7 s are up; nothing before the session is read —
  `VoicePhasesTest.listeningRevealsTheTranscriptWordByWordWithTheRestOfTheSentenceToCome`,
  `VoicePhasesTest.thinkingShowsTheWholeTranscriptWhateverTheWordCount`,
  `VoicePhasesTest.speakingRevealsTheReplyWordByWordItsLastThreeWordsStillToComeWhenItsTimeIsUp`,
  `VoicePhasesTest.beforeTheSessionIsReadThereIsNoCaption`
- [x] The label reads the phase ("LISTENING", "THINKING", "SPEAKING", in capitals on screen) —
  `VoiceUiStateTest.theLabelIsThePhaseAndTheMuteLabelSaysWhatATapDoes`
- [x] Leaving the screen (paused or gone) stops the timers where they are, and coming back starts
  again at listening; starting while playing changes nothing, and a tap while stopped is
  forgotten — `VoiceViewModelTest.stoppingHoldsThePhasesAndStartingAgainBeginsAtListening`,
  `VoiceViewModelTest.startingWhilePlayingChangesNothingAndATapWhileStoppedIsForgotten`

Saving the session

- [x] When listening ends the transcript is saved, and when thinking ends the reply; speaking saves
  nothing — `VoiceViewModelTest.theTranscriptIsSavedWhenListeningEndsAndTheReplyWhenThinkingEnds`
- [x] A tap that ends listening or thinking saves what the timer would, and skipping speaking saves
  nothing more — `VoiceViewModelTest.aTapThatEndsListeningOrThinkingSavesWhatTheTimerWould`
- [x] The first transcript moves the session to the conversation it created: the next loop plays
  its next exchange and saves into it — `VoiceViewModelTest.theFirstTranscriptMovesTheSessionToTheConversationItCreated`
- [x] Listening takes the session's next exchange when it starts and keeps it; thinking and
  speaking play the saved turn's exchange —
  `VoiceViewModelTest.listeningTakesTheSessionsNextExchangeWhenItStartsAndThinkingAndSpeakingPlayTheSavedTurns`
- [x] A transcript that is not saved (a typed reply on its way) saves no reply, thinking and
  speaking still play, and the loop goes on, saving once it can —
  `VoiceViewModelTest.aTranscriptThatIsNotSavedSavesNoReplyAndTheLoopGoesOn`
- [x] An entry's conversation that is gone starts over as a new session —
  `VoiceViewModelTest.aConversationThatIsGoneStartsOverAsANewSession`
- [x] Leaving while thinking keeps the transcript and saves no reply —
  `VoiceViewModelTest.stoppingHoldsThePhasesAndStartingAgainBeginsAtListening`

The model chip

- [x] The chip is the short name of what answers, and the accent its palette —
  `VoiceUiStateTest.theChipIsTheShortNameOfWhatAnswersAndTheAccentIsItsPalette`
- [x] The chip opens the chat sheet ("Model", personas, no compare button, hint or "◐") marking what
  answers; the scrim, a drag down or Back closes it —
  `VoiceViewModelSessionTest.theChipOpensTheChatSheetOnWhatAnswersAndScrimOrBackClosesIt`,
  `VoiceUiStateTest.theSheetIsTheChatSheetWithPersonasAndNoCompareMarkingWhatAnswers`
- [x] A pick before the first transcript closes the sheet, shows at once, makes its model the
  default (`choose(null, …)`), and is passed with the first transcript as `newSession`; the
  conversation holds it from then on —
  `VoiceViewModelSessionTest.aPickBeforeTheFirstTranscriptIsKeptForItAndMakesItsModelTheDefault`,
  `VoiceViewModelSessionTest.aModelPickedBeforeTheFirstTranscriptTurnsTheAccentAtOnce`
- [x] A pick after the first transcript is chosen for the conversation (`choose(id, …)`) —
  `VoiceViewModelSessionTest.aPickAfterTheFirstTranscriptChoosesForTheConversation`
- [x] A pick that changes nothing puts the chip back, and is not kept for the first transcript —
  `VoiceViewModelSessionTest.aPickThatChangesNothingPutsTheChipBackOnWhatAnswers`
- [x] A star toggles a favorite without picking it or closing the sheet —
  `VoiceViewModelSessionTest.aStarTogglesAFavoriteWithoutPickingIt`

Mute and "end"

- [x] Mute only stills the listening sphere: the phases, the words and the saves go on, and the
  label reads "unmute" until it is tapped again —
  `VoiceViewModelSessionTest.muteOnlyStillsTheListeningSphereAndThePhasesAndTranscriptGoOn`,
  `ChatVoiceSphereTest.muteStillsTheListeningSphereButNotTheSpeakingOne`
- [x] "end" while listening saves nothing and listening starts again with its whole time —
  `VoiceViewModelSessionTest.endWhileListeningSavesNothingAndListeningStartsAgain`
- [x] "end" closes the session: what it saved stays (ended while thinking: the transcript, no
  reply), and a fresh session on the default model starts a new conversation —
  `VoiceViewModelSessionTest.endClosesTheSessionAndAFreshOneStartsANewConversation`
- [x] "end" leaves the entry's conversation for a new session and drops a persona picked for the
  ended one — `VoiceViewModelSessionTest.endLeavesTheEntrysConversationForANewSessionAndDropsAPersonaPickedForIt`
- [x] "end" while the screen is stopped starts no timer —
  `VoiceViewModelSessionTest.endWhileStoppedStartsNoTimer`

The frame

- [x] A new session listens on the default model with nothing to show until the session is read —
  `VoiceUiStateTest.aNewSessionListensOnTheDefaultModelWithNothingToShowYet`
- [x] The previews cover every state in light and dark, and the sphere's previews muted listening —
  `VoiceUiStateTest.previewsCoverEveryStateInLightAndDark`,
  `ChatVoiceSphereTest.providerCoversEveryPhaseAndMutedListeningInBothModes`

On a device (not automatable here): the sphere's motion in each phase and muted, the glow
brightening while speaking; a tap on the sphere; the caption's two-colour reveal and its centring;
the controls clear of the navigation bar or home indicator; the chip and the sheet's accent after a
pick; the timers stopping for History, "Aa", another app and a rotation, and starting again at
listening on return; system back on Voice leaving the app (Android) and the iOS edge swipe doing
nothing on it; TalkBack and VoiceOver reading the controls once, mute as a switch, and the phase
changes; light and dark on Android and iOS.

## Data contract

- **Module and package:** `:data:chat`, `br.com.weslleycampos.chat.data.chat` (models in `….models`).
- **Repository:** a new interface next to `ChatRepository`, `ForkRepository` and
  `CompareRepository`, whose signatures are unchanged:

  ```kotlin
  interface VoiceRepository {
      fun observeSession(conversationId: Long?): Flow<VoiceSession?>
      suspend fun saveTranscript(conversationId: Long?, newSession: ModelChoice? = null): VoiceTurn?
      suspend fun saveReply(turn: VoiceTurn): Boolean
      suspend fun choose(conversationId: Long?, choice: ModelChoice): Boolean
  }
  ```

- **Models:**
  - `data class VoiceSession(conversationId: Long?, model: ChatModel, persona: Persona? = null,
    next: VoiceExchange)`, with `choice: ModelChoice` (`ModelChoice(model, persona)`, never
    comparing).
  - `data class VoiceExchange(transcript: String, reply: String)`.
  - `data class VoiceTurn(conversationId: Long, transcriptId: Long, exchange: VoiceExchange)`.
  - Internal `VoiceLines` (`replies`): `exchanges` (four: the prototype's `USER_T` / `MODEL_T`,
    then three more) and `next(exchangesSoFar)`.
- **Storage:** no schema change, version 6. `MessageDao` gains `countUserMessages(conversationId):
  Int` and `last(conversationId): MessageEntity?`. A transcript is a `user` row (`DONE`); a reply
  an `assistant` row, `DONE`, `blocks` = one paragraph, `modelId` / `personaId` from the
  conversation, `latencyMillis` = the thinking time, `createdAt` = its transcript's.
- **Koin binding:** `@Single internal fun voiceRepository(chats: ChatRepository, models:
  ModelRepository, conversationDao, messageDao, transactions): VoiceRepository` →
  `RoomVoiceRepository` on `Clock.System`, in `ChatBindings.kt`. Inject `VoiceRepository` only.
- **Behaviour:**
  - `observeSession(null)` emits `VoiceSession(null, defaultModel, null, exchanges[0])` on collect
    and again whenever the default model changes. `observeSession(id)` combines
    `ChatRepository.observeConversation(id)` and `observeMessages(id)`: the conversation's model and
    persona, and `next = exchanges[userMessages % 4]`; it emits on collect, when a transcript is
    saved, and when the model or persona changes; never twice in a row with the same value (a
    saved reply or a typed reply's ticks emit nothing new). `null` while no conversation has that
    id.
  - `saveTranscript(null, newSession)`: in one transaction, creates the conversation titled
    `ConversationTitles.from(exchanges[0].transcript)` on `newSession` (compare dropped) or the
    default model, and saves `exchanges[0].transcript` as its user message, now. Returns
    `VoiceTurn(newId, messageId, exchanges[0])`. `null`, nothing written, for a model not in the
    catalogue or a persona not stored.
  - `saveTranscript(id)`: reads the conversation through `ChatRepository` first (which waits for
    its start-up recovery), then in one transaction moves it to the top (`updatedAt` = now), brings
    it back from the archive, and saves `exchanges[n % 4].transcript`, n = its user messages so far.
    `null`, nothing written, for an unknown id or while a reply in it is `THINKING` / `STREAMING`.
    `newSession` is ignored.
  - `saveReply(turn)`: in one transaction, when the conversation exists and `turn.transcriptId` is
    its last message and a user message, inserts the reply to that exchange (n = user messages − 1):
    `DONE` at once, one `Block.Paragraph(reply)`, the conversation's model and persona at this
    moment, `latency` = now − the transcript's `createdAt` (2400 ms on the timer, less after a tap),
    `createdAt` = the transcript's. Returns `true`. `false`, nothing written, otherwise: unknown or
    deleted conversation, the turn already answered, or a typed message after it. `updatedAt` does
    not move.
  - `choose(id, choice)`: drops compare, refuses (`false`, nothing written) a model not in the
    catalogue or a persona not stored; with an id, calls `ChatRepository.choose(id, …)` and returns
    `false` if that does; then `ModelRepository.setDefaultModel(model)` (a persona's own model) and
    returns `true`. With `null`, only the default changes.
  - Nothing voice writes is ever `THINKING` or `STREAMING`; no stream, no timer and no scope in the
    data layer: the phases are the screen's.
  - Voice conversations are ordinary conversations: `observeHistory` (with search over the
    transcripts), `observeConversationCount`, `observeRecentConversations`, `observeMessages`,
    `send` (canned typed replies rotate on from the voice replies), pin, archive, delete and fork all
    work on them.

## UI contract

What later rows extend, in `:feature:voice` (`br.com.weslleycampos.chat.feature.voice`) unless
noted. The module depends on `:core:navigation`, `:core:ui`, `:data:chat` and `:feature:chat` (for
the sheet and `ChatModel.palette`); nothing depends on it but `:app`.

- **Key:** `@Serializable data class VoiceEntry(val conversationId: Long? = null) : NavKey` in
  `:core:navigation`'s `entries.voice`. `VoiceEntryProvider` (`@Single`, an `EntryProvider` Koin
  collects) registers it. `SplashViewModel` (signed in) and `SignInViewModel` (after any sign-in)
  call `navigate(VoiceEntry()) { popUpTo<SplashEntry|SignInEntry> { inclusive = true } }`, so the
  stack is `[VoiceEntry()]` and back from it leaves the app.
- **Screen:** `VoiceScreen(uiState, onHistoryClick, onModelClick, onSettingsClick, onSphereTap,
  onToggleMute, onEnd, onTextClick, modifier)`, wrapped by the provider in
  `ChatTheme(palette = uiState.model.palette, darkTheme = ChatTheme.colors.isDark)`, followed by
  `ModelPickerSheet(open = uiState.modelPickerOpen, state = uiState.modelPicker, onDismissRequest =
  picker::onDismiss, onPick = picker::onPick, onToggleFavorite = picker::onToggleFavorite)` with no
  `headerAction`. `LifecycleResumeEffect` calls `onStart()` on resume and `onStop()` on pause or
  dispose.
- **Callbacks:** `onHistoryClick` = `VoiceViewModel.onOpenHistory()` (extension: `onStop()`, then
  `navigate(HistoryEntry)`: `[VoiceEntry(x)]` → `[VoiceEntry(x), HistoryEntry]`); `onTextClick` =
  `VoiceViewModel.onOpenText()` (extension: `onStop()`, then `navigate(ChatEntry(conversationId))`,
  `ChatEntry()` before the first transcript); `onModelClick` = `picker.onOpen()`; `onSphereTap`,
  `onToggleMute`, `onEnd` = the ViewModel's own.
- **No-op left for row 10:** `onSettingsClick = {}` in `VoiceEntryProvider` ("Settings is row 10").
  Row 10 replaces it with a navigation to its Settings key, pushed over Voice so that its back
  (`navigateUp()`) returns to Voice. History's Memory and Settings icons are still `{}` in
  `HistoryEntryProvider`; row 9 removes Memory (dropped) and row 10 wires Settings there or removes
  it.
- **For row 9 (History in text):** History opened from Voice sits on `[VoiceEntry(x), HistoryEntry]`.
  `HistoryViewModel.onBack()` already pops back to Voice with `navigateUp()`; its fallback when
  History is alone still pushes `ChatEntry()` and should become `VoiceEntry()`. A row tap calls
  `navigate(ChatEntry(id)) { popUpTo<ChatEntry> { inclusive = true } }`, which over Voice pops
  nothing, giving `[VoiceEntry, HistoryEntry, ChatEntry(id)]` (back → History → Voice): what row 9
  asks for. The thread's mic (`onVoiceClick = {}` in `ChatEntryProvider`) becomes
  `navigate(VoiceEntry(conversationId)) { popUpTo<VoiceEntry> { inclusive = true } }` (or a plain
  pop back to Voice when Voice is underneath on the same conversation); `VoiceEntry(id)` goes on with
  that conversation in voice. Chat's History button and new-chat icon are row 9's to remove. (Done
  in row 9, with a plain push for the row tap instead of the `popUpTo`; spec 09.)
- **State:** `VoiceUiState(conversationId, choice: ModelChoice, phase: ChatVoicePhase, words: Int,
  exchange: VoiceExchange?, muted, favorites, personas, modelPickerOpen)` with `model`, `chipLabel`
  (the short name), `caption: VoiceCaption(shown, rest)`, `phaseLabel`, `muteLabel` and
  `modelPicker` (`ModelPickerVariant.Chat` on `choice`). `ChatVoicePhase.label`.
- **ViewModel:** `VoiceViewModel(@InjectedParam entry: VoiceEntry, navigator, voices:
  VoiceRepository, models: ModelRepository)`, `Navigator by navigator`, bound only to its own class:
  `onStart`, `onStop`, `onSphereTap`, `onToggleMute`, `onEnd`, and `picker: VoicePicker` (`onOpen`,
  `onDismiss`, `onPick(row)`, `onToggleFavorite(model)`, `pending`). The phase machine is the
  internal `VoicePlayer(scope, state, turns: VoiceTurns)`.
- **Phases:** `ChatVoicePhase.duration` (6500 / 2400 / 7000 ms), `ChatVoicePhase.next`,
  `WordInterval` (230 ms), `voiceCaption(phase, exchange, words)`.
- **`:core:ui`:** `ChatVoiceSphere(modifier, phase, muted = false)`; `muted` stills the listening
  amplitude (0.05), as the prototype's mute.

## Decided

- Decided: the voice data is a new `VoiceRepository` (`RoomVoiceRepository`) instead of more
  functions on `ChatRepository`, which has ten, at detekt's `TooManyFunctions` limit, as rows 6 and
  7 did. It reuses rather than copies: reads go through `ChatRepository.observeConversation` and
  `observeMessages`, picks through `ChatRepository.choose` and `ModelRepository.setDefaultModel`,
  and transcripts are written by the `TurnWriter` a send uses (moved to its own file with
  `StoredChoice` and `ModelRepository.stored`), so a voice conversation is created, titled, moved
  up and unarchived exactly as a typed one.
- Decided: the data layer has no timer. The phases are the prototype's screen state
  (`voicePhase`), so the Voice screen runs them and tells the repository when listening ends
  (`saveTranscript`) and when thinking ends (`saveReply`); only the exchange is data.
- Decided: the transcript is saved when listening ends, by its timer or a tap, and saved whole even
  when a tap cut its reveal short, as thinking then shows the whole `USER_T`.
- Decided: the reply is saved when thinking ends, as speaking starts, `DONE` at once and whole. It
  never streams in the database: speaking's word-by-word reveal is the screen's, so nothing is ever
  left thinking or streaming, and the existing restart recovery has nothing to do for voice.
- Decided: ending early. A session that ends while listening saves nothing of that exchange (the
  user had not finished). One that ends while thinking keeps the transcript and saves no reply: the
  thread ends on the user's message, as a fork made at a user message does, and the next voice
  exchange or typed send goes on from it. One that ends while speaking keeps both, the reply
  whole. "end", "Aa", back, leaving the screen and the app being killed all give the same result
  with no call, because nothing is saved on ending.
- Decided: the latency is measured, from the transcript's save to the reply's: the thinking
  phase, 2.4 s on the timer, shorter after a tap. The text thread's meta then reads "2.4s". It is
  the thinking time, not the streaming time a typed reply's latency is, since a voice reply does
  not stream.
- Decided: the reply's `createdAt` is its transcript's, like a typed reply's, and it does not move
  `updatedAt`, as a typed reply does not.
- Decided: the reply records the conversation's model and persona when it is saved, which is what
  the chip shows while it speaks, so a pick made while thinking answers that exchange.
- Decided: the rotation counts the conversation's user messages, typed ones included, not its
  replies: a transcript left without a reply still moves the rotation on, so the next loop never
  repeats the line just heard, and a typed conversation continued in voice starts where its count
  is. The reply always belongs to its transcript's exchange (its user messages − 1).
- Decided: three lines follow the prototype's, going on with the same debounce hook (unmount while
  pending, a changing delay, testing with fake timers) in the same plain, contraction-free voice.
  Each fits its phase at 230 ms a word (≤ 28 words heard in 6.5 s, ≤ 30 said in 7 s); the
  prototype's `MODEL_T` keeps its 33 words, so its last three are still grey when speaking ends, as
  there.
- Decided: voice never compares, answering spec 07's note for row 11: a reply is one
  `Message.Assistant` from the conversation's model, even while the conversation compares, so it
  can be read aloud; the conversation keeps comparing in text. A pick on Voice and a new session's
  choice drop compare, so the sheet needs no compare button.
- Decided: a pick on Voice is one call, `VoiceRepository.choose(conversationId, choice)`: the
  conversation's choice (`ChatRepository.choose`), then the default model for new sessions and new
  chats (orchestrator decision; Settings has no "Default model" any more). A persona's own model
  becomes the default. The default stays a model only (spec 04: there is no default persona), so a
  persona picked before a new session's first transcript is kept by the screen and passed as
  `newSession`, as a new chat passes `newChat`.
- Decided: a first transcript leaves the default alone, as a first send does: only a pick on
  Voice sets it.
- Decided: a transcript while a typed reply is still thinking or streaming is refused (`null`),
  as `send` refuses one, so replies never overlap in a conversation. Row 9's thread mic can open
  Voice right after a send; the screen keeps looping and the exchange is offered again.
- Decided: `saveReply` answers a turn only while its transcript is the conversation's last
  message, so a timer and a tap landing together, or a call after a typed message, write nothing
  more.
- Decided: an existing conversation's transcript first reads the conversation through
  `ChatRepository`, which waits for its start-up recovery, so a reply an earlier run left thinking
  reads interrupted instead of refusing the transcript.
- Decided: no schema change. Whether a message was spoken or typed is not stored: nothing reads it,
  and voice exchanges read as text like any other. Two queries on `MessageDao`
  (`countUserMessages`, `last`) are all the database needed; it stays on version 6.

- Decided (UI): `:feature:voice` depends on `:feature:chat` for `ModelPickerSheet`,
  `modelPickerUiState` and `ChatModel.palette`, as spec 04 decided for Voice and Settings; nothing
  moves. `:feature:chat` does not depend on Voice: row 9's thread mic reaches it through
  `VoiceEntry` in `:core:navigation`.
- Decided (UI): the bar is History on the left (Chat's three-line History icon, where the back
  chevron was, since Voice is home), Settings on the right (History's sliders, where the 44 px
  spacer was), and the chip centred between them. In the frame a flex spacer pushes the chip right,
  next to the spacer that balanced the chevron; with a 44 px button on each side it centres. No
  hairline under the bar, as the frame.
- Decided (UI): the chip is the model's short name only, the frame's `modelShort`, whether or not a
  persona answers; the persona shows as the sheet's "●" and in its model's accent. Its accessible
  name is "Model: sonnet-4.5".
- Decided (UI): the glow is `ChatVoiceSphere`'s own, centred on the sphere with the frame's 0.12
  alpha (0.22 while speaking) and its 0.6 s ease; the screen draws no second one. The frame centres
  its glow at 44 % of the whole screen, the sphere area's centre is at about 41 %, and one glow keeps
  the screen matching the sphere's own previews.
- Decided (UI): mute needed `muted` on `ChatVoiceSphere` in `:core:ui`: muted listening eases to the
  quiet amplitude, 0.05, the prototype's `amp` target when listening muted; speaking is unchanged.
  Mute is the ViewModel's: it lasts while Voice is on the back stack (History and back keep it) and
  a new Voice screen starts unmuted, as the prototype's state does.
- Decided (UI): the timers run while the Voice entry is resumed (`LifecycleResumeEffect`): History,
  "Aa", another app, the lock screen and a rotation stop them, and coming back starts again at
  listening with no word shown, the prototype's `startVoice` on entering. The model picker does not
  stop them: the sheet is over Voice, and the prototype stops only on leaving the screen.
- Decided (UI): each phase's word timer starts with the phase, so its first word shows 230 ms in. The
  prototype's single `setInterval` runs on across phases, so its first word after an advance comes
  0–230 ms in; restarting it gives every phase the same pace.
- Decided (UI): a tap while a transcript is being saved (milliseconds) ends the phase that follows as
  soon as it shows, and further taps in that window are dropped, so a burst of taps cannot skip a
  loop unseen. A tap while the timers are stopped is forgotten.
- Decided (UI): thinking's timer starts once the transcript is saved and speaking's once the reply
  is, so the reply's latency is thinking's 2.4 s on the timer. The transcript's save runs to its end
  even when the screen is left meanwhile, so the session never loses the conversation its first
  transcript created; a save for a session "end" has closed moves nothing.
- Decided (UI): "end" closes the session and Voice starts over at once on a new session
  (`conversationId` back to `null`), whatever conversation the entry opened: returning to the entry's
  conversation would make "end" do nothing for a session opened from the thread's mic (row 9). The
  ended conversation is in History, with what it saved (see "Ending early" above). A persona picked
  for the ended session before its first transcript is dropped, as "new chat" drops a pick: the
  fresh session is on the default model, which a pick on Voice already set. The prototype's
  `exitVoice` goes to Chat; Voice is home, so it stays.
- Decided (UI): "Aa" opens `ChatEntry(conversationId)` over Voice, and back returns to Voice, which
  listens again on the conversation's next exchange. Before the first transcript there is no
  conversation, so it opens a new chat, `ChatEntry()`, on the default model; a persona picked on
  Voice for the unsaved session does not carry over. The prototype's `draft = USER_T` is not
  reproduced: once listening ends the transcript is already in the thread, a canned line in the
  composer would put words in the user's mouth, and `ChatEntry` has no draft.
- Decided (UI): History is pushed over Voice and back returns to it. The timers stop before the
  navigation, as for "Aa", so nothing is saved during the transition.
- Decided (UI): Voice is the root of the back stack: no back chevron, system back leaves the app on
  Android and there is no edge swipe on iOS.
- Decided (UI): a pick shows at once (chip, accent, "●"), closes the sheet and is saved with
  `VoiceRepository.choose`; one the repository refuses puts the chip back on what answers and is
  not kept for the first transcript, as the chat's pick does. A pick never restarts the phases.
- Decided (UI): until the session is first read the caption is empty under "LISTENING", and
  listening's timer waits for it, so its first word is always the line it reveals.
- Decided (UI): after process death the back stack restores `VoiceEntry` as it was entered, so a
  session that began as `VoiceEntry()` starts a new conversation, as `ChatEntry()` restores a new
  chat; what it saved is in History.
- Decided (UI): accessibility follows the prototype's labels: the sphere is a button "Skip to the
  next phase", mute a switch "Mute", end "End" and "Aa" "Switch to text"; the mono labels under them
  repeat that and are hidden from screen readers; the phase label is a polite live region, read as
  the word rather than the capitals.
- Decided (UI): the chip darkens to `--bg3` while pressed, as the chat's chip does; the circles
  have no pressed state, as in the frame.
- Decided (UI): no `runComposeUiTest`, as in rows 3–7: it has no JVM target and no Robolectric on the
  Android host, so the frame is checked in its previews and on a device, and everything it shows is
  `VoiceUiState`, tested as state.

## Out of scope

- A microphone, speech recognition, text to speech, voices and speech rate: there is no provider
  and no speech engine; the lines are canned and the phases a timer.
- Mute: it only lowers the sphere's listening amplitude in the prototype, so it is the screen's,
  with nothing in the data.
- The particle sphere and its glow: `:core:ui`'s `ChatVoiceSphere` and the feature.
- Recording, hold to talk and attachments (dropped by the user, 2026-10-28).
- Compare in voice mode: a voice reply is always single.
- History in text (row 9) and Settings (row 10): Voice draws their buttons; see the UI contract.
- The prototype's `voiceToText` draft (`draft = USER_T`): see Decided (UI).
