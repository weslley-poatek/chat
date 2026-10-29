# Fuelix replies

## Goal

Replies come from real models instead of the design's canned text ("Send replies through Fuelix's
gateway"). Every reply, typed or spoken, goes through Fuelix (`https://api.fuelix.ai`), the one
provider Settings keeps a key for, on its OpenAI-compatible API through JetBrains Koog ("why you
aren't using koog? we can use it, with open ai api key compatible format"). The model picker lists
every model the key can use, by vendor, each vendor in the colour the design gave its models ("list
all available and group by vendors, and keep the colors for each vendor"). Voice mode, the home,
is a real conversation ("can you do the real integration? because I'm seeing some like a demo in the
home"): it listens through the microphone with the device's speech recognition and says the model's
reply with the device's voice ("On-device"). A reply that gets none says why in a toast and reads
"interrupted" ("Toast only").

## Frames

No new frame. Every line number refers to `.claude/screens/design/ctx Prototype.dc.html`.

| Frame | States | Design source |
| --- | --- | --- |
| `Chat` thread | a reply thinks until Fuelix's first words, streams them as they arrive, and finishes with its latency; one that gets none reads "interrupted" | as built in rows 3 and 7 |
| `Model picker` | "favorites", "personas", then one section per vendor ("anthropic", "openai", "google", "meta", "deepseek", "mistral", "other") in place of "all models"; a row's meta is the Fuelix id, then the context window and cost where the app knows them | markup of `mRow`, as built in row 4 |
| `Voice mode` | listening shows the words as they are heard and ends when the user stops speaking or taps; thinking lasts until the reply arrives; speaking says the reply aloud, each word shown as it is said, until the end or a tap; mute turns the microphone off; "tap to allow the microphone" until it is allowed; after a failure, listening, stopped until a tap | as built in row 8 |
| Toast | "allow the microphone in Settings to talk", "speech recognition isn’t available on this device", "couldn’t hear you, tap to try again", "add your Fuelix key in Settings", "Fuelix refused the key, check it in Settings", "this model isn’t available on your Fuelix key", "Fuelix is busy, try again shortly", "couldn’t reach Fuelix, check your connection", "Fuelix couldn’t answer, try again" (copy written here) | frames §2.9 |

## Acceptance criteria

Each criterion is one observable behaviour with at least one test that checks it. Every key in a
test is an obvious fake.

### Fuelix (`:data:fuelix`)

- [x] With no key, or a blank one, every call throws `MissingKey` and sends nothing —
  `KoogFuelixClientTest.withNoKeyStoredEveryCallThrowsMissingKeyAndSendsNothing`,
  `KoogFuelixClientTest.aBlankKeyCountsAsNoKey`
- [x] The models are the ids `GET v1/models` lists, in its order, asked for with the key —
  `KoogFuelixClientTest.theModelsAreTheIdsFuelixListsInItsOrderAskedForWithTheKey`
- [x] A reply is a streaming Chat Completion of the messages in order, sent when collected —
  `KoogFuelixClientTest.aReplyIsSentWhenCollectedAsAStreamingChatCompletionOfTheMessagesInOrder`
- [x] A reply's pieces are its text in order; reasoning, the whole text again and empty pieces are
  left out — `KoogFuelixClientTest.aReplyStreamsItsTextPiecesInOrderAndLeavesOutTheReasoningAndTheWholeTextAgain`,
  `KoogFuelixClientTest.anEmptyPieceIsLeftOut`
- [x] HTTP 401 is `KeyRefused`, 403 and 404 `ModelUnavailable`, 429 `RateLimited`, any other
  `Failed` with its status, and the key is in no message —
  `KoogFuelixClientTest.anHttpErrorForTheModelsThrowsWhatItsStatusMeans`,
  `KoogFuelixClientTest.anHttpErrorForAReplyThrowsWhatItsStatusMeans`,
  `FuelixFailuresTest.anHttpStatusMeansTheKeyTheModelTheRateOrAnyOtherFailureWithItsStatus`
- [x] A connection that fails, breaks mid-reply or goes silent too long is `Unreachable`, after the
  pieces that arrived — `KoogFuelixClientTest.aConnectionThatFailsThrowsUnreachableForEveryCall`,
  `KoogFuelixClientTest.aConnectionThatBreaksInTheMiddleOfAReplyThrowsUnreachableAfterThePiecesThatArrived`,
  `KoogFuelixClientTest.aReplyThatGoesSilentTooLongThrowsUnreachable`
- [x] An error sent in the middle of a reply is `Failed` with no status, not the stream's 200 —
  `KoogFuelixClientTest.anErrorSentInTheMiddleOfAReplyThrowsFailedWithNoStatus`,
  `FuelixFailuresTest.aKoogFailureMeansTheFirstHttpStatusAmongItsCauses`,
  `FuelixFailuresTest.anyOtherFailureIsFailedWithNoStatus`
- [x] A changed key is sent from the next call on; a reply reads the key when collected —
  `KoogFuelixClientTest.aChangedKeyIsSentFromTheNextCallOnByANewClientAndTheSameKeyReusesItsClient`,
  `KoogFuelixClientTest.aReplyReadsTheKeyWhenItIsCollectedNotWhenItIsAskedFor`

### Key (`:data:settings`)

- [x] The key source reads the whole key Settings saved, `null` while none is, and a saved or
  removed key counts from the next read —
  `EncryptedFuelixKeySourceTest.theKeyIsNullWhileNoneIsStoredAndReadingItWritesNothing`,
  `EncryptedFuelixKeySourceTest.theKeyIsTheWholeOneSettingsSavedTrimmed`,
  `EncryptedFuelixKeySourceTest.eachReadIsTheKeyStoredThenSoASavedOrRemovedKeyCountsFromTheNextRead`,
  `EncryptedFuelixKeySourceTest.aKeySavedForAProviderNoLongerListedIsNeverRead`
- [x] Koin provides one key source on the encrypted preferences the key repository writes —
  `DataSettingsModuleTest.koinProvidesOneEncryptedFuelixKeySourceThatReadsTheKeyTheRepositorySaves`

### Replies, models and voice (`:data:chat`)

- [x] A reply thinks until its first words, streams the reply so far as Markdown blocks at most every
  40 ms, and is done with the time from its first words to its last —
  `ReplyStreamerTest.aReplyThinksUntilItsFirstWordsArriveThenStreamsTheReplySoFar`,
  `ReplyStreamerTest.aStreamingReplyIsWrittenAtMostOnceEveryFortyMilliseconds`,
  `ReplyStreamerTest.theReplySoFarIsReadAsMarkdownAnOpenCodeBlockIncluded`,
  `ReplyStreamerTest.aFinishedReplyIsDoneWithItsWholeTextAndTheTimeFromItsFirstWordsToItsLastAsItsLatency`
- [x] A reply that fails or comes empty is left interrupted with what it showed, and its failure is
  told with its conversation; a cancelled one tells nothing; a deleted one ends quietly —
  `ReplyStreamerTest.aFuelixFailureIsReturnedAndTheReplyIsLeftInterruptedWithWhatItShowed`,
  `ReplyStreamerTest.anEmptyOrBlankReplyFailsAndIsLeftInterruptedShowingNothing`,
  `ReplyStreamerTest.everyFuelixFailureReadsAsItsReason`,
  `ReplyStreamerTest.aStreamCancelledMidwayLeavesTheReplyInterruptedWithWhatItShowed`,
  `ReplyStreamerTest.aStreamWhoseReplyIsGoneStopsOnTheWriteThatFindsNothingAndEndsQuietly`,
  `RoomChatRepositoryTest.aReplyThatFailsIsLeftInterruptedWithWhatItShowedAndItsFailureIsToldWithItsConversation`,
  `RoomChatRepositoryTest.aReplyWhoseStreamIsCancelledIsLeftInterruptedWithWhatItShowedAndNoFailureIsTold`,
  `RoomChatRepositoryTest.aFailureNobodyIsCollectingIsNotToldLater`
- [x] A compare pair's sides stream at once, each from its own model and done on its own —
  `ReplyStreamerTest.aPairsSidesStreamAtOnceEachFromItsOwnModel`,
  `ReplyStreamerTest.eachSideOfAPairIsDoneOnItsOwnWithItsOwnLatency`,
  `ReplyStreamerTest.aPairReturnsTheFailureOfASideWhileTheOtherSideStillFinishes`
- [x] A request sends the system prompt (the persona's), then the conversation so far, replies written
  back as Markdown; of a pair a model reads its own side —
  `ReplyPromptsTest.aRequestSendsTheSystemPromptFirstThenTheConversationBeforeItsReply`,
  `ReplyPromptsTest.aReplyIsSentAsItsBlocksWrittenBackAsMarkdownAndOneThatShowedNothingIsLeftOut`,
  `ReplyPromptsTest.ofAComparePairAModelReadsItsOwnSideOrTheFirstWhenNeitherIsItsOwn`,
  `ReplyPromptsTest.eachSideOfANewPairAsksItsOwnModelAndTheSecondSideLeavesOutItsPartner`,
  `ReplyPromptsTest.aReplyIsAskedOfTheModelItsRowWasSavedWithAndItsPersonasPromptIsTheSystemPrompt`,
  `RoomChatRepositoryTest.eachSendAsksTheConversationsModelWithTheConversationSoFarAndANewConversationStartsOver`
- [x] A model's Markdown reads as paragraphs, numbered lists (blank lines between items included) and
  code, an open fence while streaming included —
  `MarkdownBlocksTest.aFencedCodeBlockIsCodeWithItsLanguageItsBlankLinesAndIndentation`,
  `MarkdownBlocksTest.aCodeBlockStillOpenWhileTheReplyStreamsIsCodeSoFar`,
  `MarkdownBlocksTest.aRunOfNumberedLinesIsANumberedListApartFromTheParagraphsAroundIt`,
  `MarkdownBlocksTest.aNumberedListWithBlankLinesBetweenItsItemsIsOneList`,
  `MarkdownBlocksTest.headingMarksAreDroppedAndBulletsReadAsDots`,
  `MarkdownBlocksTest.blocksAreWrittenBackAsMarkdownOneBlankLineApart`
- [x] Ids stored before Fuelix are answered by the model they became; any other id is a model with a
  name and vendor read from it —
  `RoomChatRepositoryTest.aConversationStoredOnAnIdFromBeforeFuelixIsAnsweredByTheModelItBecame`,
  `LocalModelRepositoryTest.idsStoredBeforeFuelixReadAsTheModelsTheyBecameAndAreStoredResolvedOnTheNextChange`,
  `ModelNamesTest.aClaudeIdDropsItsDateAndJoinsItsVersionAndItsShortNameDropsClaude`,
  `ModelNamesTest.aDashedDateIsDroppedToo`,
  `ModelNamesTest.aModelsVendorIsReadFromTheStartOfItsIdWhateverItsCase`
- [x] The catalogue's models are listed until the key's are read; a refresh lists the key's chat
  models in Fuelix's order once each, and one that fails or finds none keeps the list —
  `LocalModelRepositoryTest.untilTheKeysModelsAreReadTheCatalogueIsListed`,
  `LocalModelRepositoryTest.refreshingListsTheKeysChatModelsInFuelixsOrderOnceEach`,
  `LocalModelRepositoryTest.aRefreshThatFailsOrFindsNoChatModelKeepsTheListAsItWas`,
  `LocalModelRepositoryTest.favoritesFollowTheKeysOrderThenAnyNoLongerListedByTheirIds`
- [x] Deleting the account erases the key's model list too —
  `LocalChatDataEraserTest.erasingDeletesEveryConversationAndMessageTheChoicesAndTheKeysModelsButKeepsThePersonas`
- [x] Voice saves what was heard, trimmed, and nothing for blank words —
  `RoomVoiceRepositoryTest.theFirstTranscriptIsSavedTrimmedInAConversationTitledAfterItOnTheDefaultModel`,
  `RoomVoiceRepositoryTest.aBlankTranscriptSavesNothing`,
  `RoomVoiceRepositoryTest.eachTranscriptIsSavedAsHeardAndAnsweredInTurn`
- [x] A voice answer asks with the voice prompt, the persona's and the conversation so far, is saved
  done and whole, credited to the model that wrote it; a failed one saves nothing —
  `RoomVoiceRepositoryTest.theAnswerAsksTheConversationsModelWithTheVoicePromptThePersonasPromptAndTheConversationSoFar`,
  `RoomVoiceRepositoryTest.theAnswerIsSavedDoneAndWholeFromTheModelAndPersonaWithTheTimeItTookAsItsLatency`,
  `RoomVoiceRepositoryAnswerTest.aReplyIsCreditedToTheModelThatWroteItEvenWhenAnotherIsPickedWhileItThinks`,
  `RoomVoiceRepositoryTest.aFailedAnswerSavesNothingAndTheTurnCanBeAnsweredAgain`
- [x] A voice turn answered twice, followed by a typed message or deleted is answered once at most —
  `RoomVoiceRepositoryAnswerTest.aTurnAnsweredTwiceAtOnceIsSavedOnceAndTheOtherAnswerIsDropped`,
  `RoomVoiceRepositoryAnswerTest.aTurnFollowedByATypedMessageOrDeletedWhileItIsAnsweredIsDroppedAndSavesNothing`,
  `RoomVoiceRepositoryTest.aTurnIsDroppedWithoutAskingFuelixAfterATypedMessageOrOnceItsConversationIsGone`

### UI (`:feature:chat`, `:feature:history`, `:feature:voice`, `:app`)

- [x] The picker groups the models by vendor under its lowercase name, in the vendors' order,
  keeping the list's order within one; a model of no known vendor comes last, under "other" —
  `ModelPickerUiStateTest.theModelsAreGroupedByVendorUnderItsLowercaseNameInTheVendorsOrder`,
  `ModelPickerUiStateTest.eachVendorKeepsTheListsOrderAModelOfNoKnownVendorComesLastAndAVendorWithNoneHasNoSection`,
  `ModelPickerUiStateTest.withNoModelListedThereIsNoVendorSection`
- [x] A favorite the key no longer lists stays under "favorites" only —
  `ModelPickerUiStateTest.aFavoriteTheKeyNoLongerListsStaysUnderFavoritesOnly`
- [x] A row's meta is its id, then the context window and cost where known —
  `ModelPickerUiStateTest.rowMetaIsTheIdContextAndCostOrThePersonasModelAndPrompt`,
  `ModelPickerUiStateTest.rowMetaLeavesOutAContextWindowOrCostTheAppDoesNotKnow`,
  `ChatFormatsTest.theEmptyStateMetaLeavesOutAContextWindowOrCostTheAppDoesNotKnow`
- [x] Every model wears its vendor's palette, in the chat, the picker, History and the root —
  `ChatFormatsTest.everyModelWearsItsVendorsPalette`,
  `ModelPickerUiStateTest.eachRowsDotIsItsVendorsPaletteAndAPersonasIsItsModels`,
  `HistoryFormatsTest.everyModelWearsItsVendorsPaletteAsTheChatPicksIt`,
  `AppViewModelTest.theRootWearsTheDefaultModelsVendorsPaletteAndFollowsItAtOnce`
- [x] Opening the picker asks Fuelix for the key's models; it keeps the ones it knows when Fuelix
  lists none or fails, and follows the list as it changes —
  `ChatViewModelPickerTest.openingTheSheetAsksFuelixForTheKeysModelsAndListsThemByVendor`,
  `ChatViewModelPickerTest.whenFuelixListsNothingOrTheRefreshFailsTheSheetKeepsTheModelsItKnows`,
  `ChatViewModelPickerTest.theSheetFollowsTheModelsTheRepositoryListsWheneverTheyChange`,
  `VoiceViewModelSessionTest.theChipAsksFuelixForTheKeysModelsAndTheSheetListsThemByVendor`,
  `VoiceViewModelSessionTest.whenFuelixListsNothingOrTheRefreshFailsTheSheetKeepsTheModelsItKnows`
- [x] Every failure has the toast that says why —
  `ChatViewModelReplyFailureTest.everyFailureHasTheToastThatSaysWhy`
- [x] A failed reply of the open conversation, or of a compare pair, shows why for 1400 ms and reads
  interrupted; another conversation's failure shows nothing —
  `ChatViewModelReplyFailureTest.aReplyOfTheOpenConversationThatFailsShowsWhyFor1400MsAndReadsInterrupted`,
  `ChatViewModelReplyFailureTest.aComparePairWhoseSidesBothFailShowsWhyAndBothSidesReadInterrupted`,
  `ChatViewModelReplyFailureTest.aFailureOfAnotherConversationShowsNothing`,
  `ChatViewModelReplyFailureTest.aFailureOfAnotherConversationShowsNothingInANewChatWithNothingSent`
- [x] A new chat's first reply that fails before or after its send returns still shows why, even
  when another conversation fails meanwhile —
  `ChatViewModelReplyFailureTest.aNewChatsFirstReplyThatFailsAfterTheSendReturnedShowsWhy`,
  `ChatViewModelReplyFailureTest.aNewChatsFirstReplyThatFailsBeforeItsSendReturnsShowsWhyOnceTheSendDoes`,
  `ChatViewModelReplyFailureTest.aFailureOfAnotherConversationWhileANewChatSendsIsDroppedWhenTheSendReturns`,
  `ChatViewModelReplyFailureTest.aNewChatsOwnFailureStillShowsWhenAnotherConversationFailsBeforeItsSendReturns`
- [x] Listening shows each word as it is heard and ends when the device says the user stopped, after
  2 s with no new word, or at a tap with what was heard; nothing heard listens again —
  `VoiceViewModelListeningTest.listeningShowsEachWordAsItIsHeardAndEndsWhenTheDeviceSaysTheUserStopped`,
  `VoiceViewModelListeningTest.onceSomethingWasHeardTwoSecondsWithNoNewWordEndListening`,
  `VoiceViewModelListeningTest.eachNewWordStartsTheSilenceOver`,
  `VoiceViewModelListeningTest.untilAWordIsHeardListeningWaitsHoweverLong`,
  `VoiceViewModelListeningTest.aTapEndsListeningWithTheWordsHeardSoFar`,
  `VoiceViewModelListeningTest.hearingNothingListensAgainAfterAPause`
- [x] Mute turns the microphone off mid-listen and unmuting listens afresh, from an empty caption —
  `VoiceViewModelListeningTest.muteTurnsTheMicrophoneOffMidListenAndUnmutingListensAfresh`,
  `VoiceViewModelListeningTest.unmutingStartsTheCaptionFromNothing`,
  `VoiceViewModelListeningTest.whileMutedSpeakingGoesOnAndTheNextListeningWaitsForUnmute`
- [x] Nothing listens without the microphone; allowing it starts listening —
  `VoiceViewModelListeningTest.withoutTheMicrophoneNothingListensAndAllowingItStartsAtOnce`,
  `VoiceViewModelListeningTest.theMicrophoneAllowedWhileTheScreenIsAwayWaitsForItToResume`,
  `VoiceViewModelListeningTest.losingTheMicrophoneMidListenTurnsItOffAndSavesNothing`
- [x] A device that cannot listen shows why and stops until a tap —
  `VoiceViewModelListeningTest.aDeviceThatCannotListenShowsWhyFor1400MsAndStopsUntilATap`,
  `VoiceViewModelListeningTest.aListenThatFailsLaterShowsWhyAndStopsThere`,
  `SpeechTest.eachSpeechProblemHasTheToastThatSaysWhy`
- [x] What was heard is saved, and thinking lasts until the reply arrives; taps while thinking are
  ignored — `VoiceViewModelTest.theTranscriptIsSavedAsListeningEndsAndThinkingLastsUntilTheReplyArrives`,
  `VoiceViewModelTest.thinkingWaitsForTheReplyHoweverLongItTakes`,
  `VoiceViewModelTest.tapsWhileThinkingAreIgnoredAndNotCarriedIntoSpeaking`
- [x] Speaking says the reply, showing each word as it starts, until the end or a tap; a device that
  cannot speak shows a word every 230 ms —
  `VoiceViewModelTest.speakingSaysTheReplyShowingEachWordAsItStartsAndListensAgainWhenItEnds`,
  `VoiceViewModelTest.aTapStopsSpeakingAtOnceAndListensAgain`,
  `VoiceViewModelTest.aDeviceThatCannotSpeakShowsAWordEvery230MsForTheRepliesSpeakingDuration`,
  `SpeechTest.eachWordStartsAtZeroOrJustAfterASpace`
- [x] A transcript not saved, or a reply dropped, listens again —
  `VoiceViewModelTest.aTranscriptThatIsNotSavedAsksForNoReplyAndListensAgain`,
  `VoiceViewModelTest.aReplyThatIsDroppedListensAgain`
- [x] A failed reply shows why for 1400 ms and stops at listening until a tap, the screen coming back,
  or "end" — `VoiceViewModelTest.aFailedReplyShowsWhyFor1400MsAndStopsAtListeningWithNothingHeard`,
  `VoiceViewModelTest.aTapAfterAFailedReplyListensAgain`,
  `VoiceViewModelTest.afterAFailedReplyTheScreenComingBackListensAgainButATapWhileItIsAwayDoesNot`,
  `VoiceViewModelTest.endAfterAFailureStartsAFreshSessionAtOnce`,
  `VoiceViewModelTest.aReplyThatThrowsIsAFailedReply`
- [x] Leaving the screen turns the microphone off and stops speaking —
  `VoiceViewModelTest.stoppingTurnsTheMicrophoneOffAndStartingAgainListensAfresh`,
  `VoiceViewModelTest.stoppingWhileSpeakingStopsTheVoice`
- [x] Koin provides one speech input and output —
  `VoiceModuleTest.koinProvidesOneSpeechInputAndOutputForTheDevice`
- [ ] On the Samsung SM-S921B: speaking to the phone gets a spoken reply from Fuelix (needs the key in
  Settings) — manual
- [ ] On an iPhone: the same — manual; the app builds and links for the simulator

## Data contract

- **Module and package:** `:data:fuelix`, `br.com.weslleycampos.chat.data.fuelix` (new).
- **Client:**

  ```kotlin
  interface FuelixClient {
      suspend fun models(): List<String>
      fun streamChat(model: String, messages: List<FuelixMessage>): Flow<String>
  }
  fun interface FuelixKeySource { suspend fun key(): String? }
  sealed class FuelixException // MissingKey, KeyRefused, ModelUnavailable, RateLimited, Unreachable, Failed
  ```

- **Implementation:** `KoogFuelixClient`, Koog 1.3.0's `OpenAILLMClient` with
  `OpenAIClientSettings(baseUrl = "https://api.fuelix.ai")` over `KtorKoogHttpClient.Factory`
  (OkHttp on Android, Darwin on iOS). Every model is sent as an `LLModel` with `Completion` and
  `OpenAIEndpoint.Completions`, so every vendor goes through Chat Completions. Connecting times out
  after 15 s, a silent stream after 120 s.
- **Key:** `EncryptedFuelixKeySource` in `:data:settings` reads the encrypted preference
  `ProviderKeyRepository` writes; bound as `FuelixKeySource`. `:data:fuelix` is the only module that
  reads the whole key, for the `Authorization` header.
- **`:data:chat`:**
  - `ChatRepository.replyFailures: Flow<ReplyFailed>`, `ReplyFailed(conversationId, failure)`,
    `ReplyFailure { NO_KEY, KEY_REFUSED, MODEL_UNAVAILABLE, RATE_LIMITED, UNREACHABLE, FAILED }`.
  - `ModelRepository.observeModels()` and `refreshModels()`: the key's chat models, stored as
    `fuelix_model_ids` in the plain DataStore, the catalogue's eight until the first refresh.
  - `VoiceRepository.answer(turn): VoiceAnswer` (`Spoken`, `Failed`, `Dropped`) in place of
    `saveReply`.
  - `ChatModel.vendor: ModelVendor`, by id prefix; `contextWindow` and `cost` are known only for the
    catalogue's eight.
- **Speech (`:feature:voice`):** `SpeechInput.listen(): Flow<Heard>` (partial words, then the
  final ones) and `SpeechOutput.speak(text): Flow<Int>` (words started so far), bound per platform:
  `AndroidSpeechInput` (`SpeechRecognizer`), `AndroidSpeechOutput` (`TextToSpeech`),
  `IosSpeechInput` (`SFSpeechRecognizer` on an `AVAudioEngine` tap), `IosSpeechOutput`
  (`AVSpeechSynthesizer`); `rememberMicrophoneAccess()` asks for `RECORD_AUDIO`, or for iOS's
  microphone and speech recognition. `VoiceRepository.saveTranscript(conversationId, transcript,
  newSession)` saves what was heard; the design's canned lines (`VoiceLines`) are gone.
- **Platform files:** `RECORD_AUDIO` and the recognizer and TTS `<queries>` in
  `feature/voice/src/androidMain/AndroidManifest.xml`; `NSMicrophoneUsageDescription` and
  `NSSpeechRecognitionUsageDescription` in `iosApp/iosApp/Info.plist`.
- **Storage:** no schema change; Room stays on version 6.

## Decided

- Decided: Koog rather than a client of our own. The user asked for it; it runs on Android and iOS,
  streams Chat Completions and lists models, and leaves tools and agents open for later.
- Decided: one `:data:fuelix` module that both `:data:chat` and `:data:settings` depend on, holding
  `FuelixKeySource`, since `:data:settings` already depends on `:data:chat` and the key cannot be
  read the other way round.
- Decided: the ids stored before Fuelix ("sonnet", "gpt5"…) are read as their Fuelix models
  (`ModelCatalogue.resolve`) and never rewritten, so no migration.
- Decided: a stream that ends with no finish reason was cut off. Ktor reads a connection that breaks
  mid-reply as the end of the stream, and OpenAI-compatible streams always end with a finish
  reason, so this is the only way to tell; such a reply reads interrupted with what arrived.
- Decided: the reply is written to Room at most every 40 ms while it streams, the pace the design
  revealed at, so a fast model does not write once per token.
- Decided: voice asks for one to three short spoken sentences with no Markdown, before the persona's
  prompt, and speaking lasts as long as the device takes to say it, not the design's 7 s.
- Decided: on-device speech rather than Fuelix's audio models (the user's pick): free, no round trip
  before the words show, and it works with every chat model. It speaks and listens in the device's
  language.
- Decided: listening ends when the device says the user stopped, or after 2 s with no new word once
  some were heard (iOS never says so on its own), or at a tap, which takes what was heard. Nothing
  heard, and it listens again after half a second.
- Decided: mute turns the microphone off, as in a call; the design's mute only stilled the sphere.
- Decided: a voice reply is credited to the model and persona that wrote it, even when another is
  picked while it thinks.
- Decided: after a failed voice reply the phases stop at listening until a tap, so a missing key or
  a lost connection does not save one unanswered transcript after another.
- Decided: the model list is refreshed when the picker opens and kept when Fuelix does not answer,
  so the picker always has models to show.

## Out of scope

- Choosing a voice or a language for speech: the device's default is used.
- Listening while the model speaks (barge-in): a tap stops speaking instead.
- Retrying a failed reply in place: "retry" still puts the message back in the composer.
- Context windows and costs for models outside the catalogue's eight: Fuelix's model list does not
  give them.
- Tools, agents and attachments.
