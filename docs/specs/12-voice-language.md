# Voice language

## Goal

The user picks the language voice mode works in: Brazilian Portuguese, American English, or both
("can we have an option to set the language? pt-br en-US, I think this can improve the recognize
and voice speak"). The device's speech recognition listens for it, the model is asked to answer in
it, and the device says the answer in that language's voice, instead of all three following the
device's own language.

"Both" is for a developer who talks about code in English and the rest in Portuguese ("I need that
to be bilingue, for example when I speaking about code I'll speak in english"). Each thing said is
heard in the language it is in, on the device ("Auto-detect on device"), the model answers in the
language of the user's last message, and the reply is said in the voice of the language it is
written in.

## Frames

No new frame. Every line number refers to `.claude/screens/design/ctx Prototype.dc.html`.

| Frame | States | Design source |
| --- | --- | --- |
| `Settings` "voice" | label "voice" (as "defaults", 406); one row, "Language" and a segmented control "pt-BR" · "en-US" · "both", the picked one filled; hidden until the languages are read | the "Voice" row of "defaults" (410–414) and `seg` (808), as `ChatSegmentedControl` builds them |
| Toast | "couldn’t change the language, try again" (copy written here) | frames §2.9 |

## Acceptance criteria

Each criterion is one observable behaviour with at least one test that checks it.

### Languages (`:data:chat`)

- [x] The languages are Brazilian Portuguese and American English, by their tags "pt-BR" and
  "en-US"; a language an engine reports hearing is read by its language alone ("en" is English) —
  `VoiceLanguageTest.theLanguagesAreBrazilianPortugueseAndAmericanEnglishByTheirTags`,
  `VoiceLanguageTest.aLanguageAnEngineHeardIsReadByItsLanguageAlone`
- [x] One language listens for it alone; both listen for both, the primary first —
  `VoiceLanguageTest.oneLanguageListensForItAloneAndBilingualForBothThePrimaryFirst`
- [x] Until picked, it is the device's language alone: Brazilian Portuguese for any Portuguese,
  American English for anything else —
  `LocalVoiceLanguageRepositoryTest.untilPickedTheLanguageIsTheDevicesAloneBrazilianPortugueseForAnyPortugueseElseEnglish`
- [x] Picked languages are stored as the primary's tag and whether both, and outlast a change of the
  device's language and a restart —
  `LocalVoiceLanguageRepositoryTest.pickedLanguagesAreStoredAsThePrimarysTagAndWhetherBilingualAndOutlastTheDevicesLanguage`
- [x] A stored language this build does not have reads as the device's —
  `LocalVoiceLanguageRepositoryTest.aStoredLanguageThisBuildDoesNotHaveReadsAsTheDevices`
- [x] The languages emit at once and on each change, never the same twice in a row —
  `LocalVoiceLanguageRepositoryTest.itEmitsAtOnceAndOnEachChangeButNeverTheSameLanguagesTwiceInARow`
- [x] With one language, the voice prompt asks for one to three short spoken sentences in it —
  `VoiceLanguageTest.theVoicePromptAsksForAShortSpokenAnswerInTheLanguage`
- [x] With both, it asks for the language of the user's last message, code and technical terms in
  English — `VoiceLanguageTest.bilingualTheVoicePromptAsksForTheLanguageOfTheUsersLastMessageWithTechnicalTermsInEnglish`
- [x] Each voice reply is asked for in the languages as they are when asked —
  `RoomVoiceRepositoryAnswerTest.eachAnswerIsAskedForInTheVoiceLanguagesAsTheyAreWhenAsked`,
  `RoomVoiceRepositoryTest.theAnswerAsksTheConversationsModelWithTheVoicePromptThePersonasPromptAndTheConversationSoFar`
- [x] Deleting the account puts the languages back to the device's alone —
  `LocalChatDataEraserTest.erasingPutsTheVoiceLanguagesBackToTheDevicesAlone`
- [x] Koin provides one `VoiceLanguageRepository` —
  `DataChatModuleTest.koinProvidesOneLocalVoiceLanguageRepositoryOnThePlainPreferences`

### Settings (`:feature:settings`)

- [x] The "voice" group offers each language alone, then "both", which keeps the language listened
  for first; screen readers hear the languages' own names —
  `SettingsUiStateTest.theVoiceGroupOffersEachLanguageAloneThenBothListeningFirstForTheOneItWasIn`
- [x] The group shows the languages voice mode is in, and follows their changes —
  `SettingsViewModelLanguageTest.theVoiceGroupShowsTheLanguagesVoiceModeIsInAndFollowsTheirChanges`
- [x] A tap on a segment picks its languages for voice mode —
  `SettingsViewModelLanguageTest.tappingASegmentPicksItsLanguagesForVoiceMode`
- [x] A pick that cannot be stored toasts "couldn’t change the language, try again", and the group
  keeps the languages voice mode is in —
  `SettingsViewModelLanguageTest.languagesThatCannotBeStoredToastAndTheGroupKeepsThoseVoiceModeIsIn`
- [x] Languages that cannot be read leave the group hidden —
  `SettingsViewModelLanguageTest.languagesThatCannotBeReadLeaveTheVoiceGroupHidden`
- [x] Nothing is picked while the account is being deleted —
  `SettingsViewModelLanguageTest.nothingIsPickedWhileTheAccountIsBeingDeleted`
- [x] Settings has only the "account", "api keys" and "voice" groups —
  `SettingsUiStateTest.settingsHasOnlyTheAccountApiKeysAndVoiceGroups`
- [x] The previews cover the "voice" group with each pick, light and dark —
  `SettingsUiStateTest.previewsCoverEveryStateInLightAndDark`

### Voice mode (`:feature:voice`)

- [x] With one language, each listen is for it and each reply is said in it, as picked when it
  starts — `VoiceViewModelListeningTest.eachListenAndEachReplySpokenIsInTheLanguageSettingsPickedAsItStarts`
- [x] With both, each listen is for both and each reply is said in the language it is written in —
  `VoiceViewModelListeningTest.bilingualEachListenIsForBothAndEachReplyIsSaidInTheLanguageItIsWrittenIn`,
  `SpokenLanguageTest.oneLanguageSaysEveryReplyInItAndBilingualSaysEachInTheOneItIsWrittenIn`
- [x] A reply is written in the language whose common words it has, Portuguese's accented words
  counting for it and code terms for neither; the first language when nothing tells them apart —
  `SpokenLanguageTest.aReplyIsWrittenInTheLanguageWhoseCommonWordsItHasCodeTermsCountingForNeither`,
  `SpokenLanguageTest.portuguesesAccentedWordsCountForIt`,
  `SpokenLanguageTest.whenNothingTellsThemApartItIsTheFirstLanguage`
- [x] Koin builds the view model on the app's `VoiceLanguageRepository` —
  `VoiceModuleTest.koinBuildsAVoiceViewModelForTheEntryItIsGivenOnTheAppsNavigator`
- [ ] On the Samsung SM-S921B (Android 16): with "pt-BR" the phone understands and answers in
  Portuguese in a Brazilian voice, with "en-US" in English in an American voice; with "both", a
  Portuguese question is heard and answered in Portuguese and an English one in English — manual
- [ ] On an iPhone: the same — manual; the app builds and links for the simulator

On a device (not automatable here): the "voice" group against the prototype's segmented rows in
light and dark; TalkBack and VoiceOver reading each segment by its languages' own names
("Português (Brasil)", "English (US)", "Português (Brasil) + English (US)") as a selected or
unselected radio button.

## Data contract

- **Module and package:** `:data:chat`, `br.com.weslleycampos.chat.data.chat`.
- **Repository:**

  ```kotlin
  interface VoiceLanguageRepository {
      fun observeLanguages(): Flow<VoiceLanguages>
      suspend fun setLanguages(languages: VoiceLanguages)
  }
  ```

- **Models:** `enum class VoiceLanguage(tag, displayName)`: `PORTUGUESE_BRAZIL` ("pt-BR",
  "Português (Brasil)"), `ENGLISH_US` ("en-US", "English (US)"), with `forDevice(tag)`,
  `ofTag(tag)` and `heardAs(tag)`. `data class VoiceLanguages(primary, bilingual = false)`, whose
  `all` is the languages to listen for, the primary first.
- **Storage:** the primary's tag under `voice_language` and `voice_bilingual` in the plain
  DataStore (`@UnencryptedPreferences`), both absent until first picked; `LocalChatDataEraser`
  removes them with the model choices. No Room change.
- **Device language:** `deviceLanguageTag()`, `Locale.getDefault()` on Android and the first of
  `NSLocale.preferredLanguages` on iOS, read each time nothing is stored.
- **Koin binding:** `LocalVoiceLanguageRepository` bound as `VoiceLanguageRepository` in
  `DataChatModule`; `RoomVoiceRepository`, `VoiceViewModel` and `SettingsViewModel` take it.
- **Prompt:** `ReplyPrompts.voicePrompt(languages)` in place of `VOICE_PROMPT`: "Answer in Brazilian
  Portuguese" or "Answer in American English"; with both, "The user speaks Brazilian Portuguese and
  American English: answer in the language of their last message, keeping code and technical terms
  in English"; then the one to three spoken sentences.
- **Speech (`:feature:voice`):** `SpeechInput.listen(languages)`, the likeliest first, and
  `SpeechOutput.speak(text, language)` with `VoiceLanguages.voiceFor(text)`, the one language or
  the one the reply is written in (`writtenIn`).
  - Android passes the first language as `RecognizerIntent.EXTRA_LANGUAGE`, and for both, from
    Android 14, language detection and `EXTRA_ENABLE_LANGUAGE_SWITCH` (`LANGUAGE_SWITCH_BALANCED`)
    between them; each listen starts in the language last detected (`onLanguageDetection`).
    `TextToSpeech.setLanguage(Locale.forLanguageTag(tag))`.
  - iOS runs one `SFSpeechRecognizer(locale = NSLocale(tag))` per language on the same microphone
    tap; 1.5 s after the last new word the audio ends and the surest final transcript (its words'
    average confidence) is what was heard, its language shown first while the next listen's words
    come. It speaks with the highest quality `AVSpeechSynthesisVoice` installed for the tag, or iOS's
    default for it.
- **Settings (`:feature:settings`):** `SettingsUiState.languages`, `VoiceLanguages.options()` and
  `description`, `SettingsViewModel.voice` (`SettingsVoiceGroup.onLanguagesClick`);
  `:feature:settings` now depends on `:data:chat`.

## Decided

- Decided: the two languages the user named, and the device's alone until picked, so a Portuguese
  phone starts in Portuguese and nothing changes for someone who never opens Settings.
- Decided: "both" on the device rather than Fuelix's transcription models (the user's pick): words
  still show as they are spoken, it is free and works offline. Its limit: English words inside a
  Portuguese sentence go through the Portuguese model, which knows common terms (deploy, branch) but
  may not spell identifiers.
- Decided: on Android the recognizer switches language once fairly sure (balanced), so an English
  term in a Portuguese sentence does not switch the whole sentence; each listen starts in the
  language last heard, since people stay in one for a while. Before Android 14, or on a recognizer
  that cannot switch, "both" listens in the first language alone.
- Decided: on iOS, where a recognizer hears one language, one recognizer per language, the surer
  final transcript winning. Listening there now ends when the audio ends, 1.5 s after the last new
  word, for one language too, so the final transcripts arrive before the player's 2 s silence.
- Decided: with both, the model is told to answer in the language of the user's last message, and
  the voice is picked from the reply's own words (each language's common words, Portuguese's
  accents), since the model may not follow the prompt and a Portuguese voice reading English is hard
  to follow. With one language the model is told to answer in it whatever language the
  conversation so far is in.
- Decided: "both" keeps the language that was picked as the one listened for first.
- Decided: the languages live in `:data:chat`, next to the default model, because the voice
  repository reads them for the prompt and `:data:settings` already depends on `:data:chat`.
- Decided: a "voice" group under "api keys", where the prototype's "defaults" group was, with the
  prototype's segmented control and the tags as labels, as the user wrote them.
- Decided: the languages are read as each listen and each reply starts, so a pick made while voice
  mode is away takes effect when it comes back, with no restart.
- Decided: on iOS the best voice installed is used; on Android, the engine's own voice for the
  language. A language whose voice is not installed shows the words at the design's pace without
  sound, as spec 11's device that cannot speak.
- Decided: deleting the account puts the languages back to the device's alone, as it does the other
  choices; signing out keeps them.

## Out of scope

- Transcribing through Fuelix (Whisper, gpt-4o-transcribe), which would hear mixed sentences best
  but shows no words until the user stops.
- Other languages, and following the device's language after one was picked.
- Choosing a voice or a speech rate (the prototype's "Voice" and "Speech rate").
- The app's own text, which stays in English.
- The language of typed chats.
