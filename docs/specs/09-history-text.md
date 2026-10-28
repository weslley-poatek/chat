# History in text

## Goal

Voice mode is home, and History, opened from Voice, is the way to read a conversation as text: a
row opens that conversation in the thread rows 3–7 built (its messages, the action row with copy,
fork, read aloud and retry, the compare cards, the fork banner, and the composer to go on typing).
Back returns to History and then to Voice, and the thread's mic goes on with that conversation in
voice. The controls that led to what the user dropped (Memory, attachments) and the ones Voice
replaced (the thread's History button and new chat) are gone.

## Frames

Every line number refers to `.claude/screens/design/ctx Prototype.dc.html`; `frames.md` §2.3
(History), §2.2 (Chat: top bar, empty state and composer) and §4 (navigation map) describe them.

| Frame | States | Design source |
| --- | --- | --- |
| `History` bar | back chevron → Voice (the prototype's `goChat`, Voice being home); title "History"; Settings (row 10, inert); the Memory icon is not built | lines 256–261; frames §4 "History · back chevron" |
| `History` row | a tap opens that conversation as text over History (the prototype's `loadSeed`); a tap while a row is open only closes it | lines 276–294; `hRow` 837–844 |
| `History` status row | "on this device · n conversations", as row 5 built it | lines 262–264 |
| `Chat` top bar | back chevron in the History button's place; the model chip and its picker, compare included; a 44 px spacer in New chat's place | lines 107–115 |
| `Chat` composer | draft, mic (a tap → Voice on this conversation), Send; no "+" | lines 220–249, input row 241–248; `micUp` 732–734 |
| `Chat` empty state | "new chat", model name and meta line, the tips, "recent" (a row opens in place) | lines 120–140 |
| `Chat` thread | messages, action row, fork banner, read-aloud bar, compare cards: unchanged (rows 3, 6, 7) | lines 142–218 |
| Navigation | History back → Voice; History row → Chat; Chat mic tap → Voice; "⑂ fork here" → the fork in place of the thread | frames §4 |

## Acceptance criteria

Each criterion is one observable behaviour with at least one test that checks it. Every criterion
runs on the Android host and the iOS simulator. The navigation is ViewModel state over a
`NavigatorImpl` on a real `NavBackStack`, built as Voice and History build it. There is no
`runComposeUiTest`, as in rows 3–8 (no JVM target and no Robolectric on the Android host), so the
controls that are gone are checked on the labels the modules bundle, the layout in the previews
and on a device.

### History (`:feature:history`)

- [x] Back (the chevron, system back, the iOS edge swipe) returns to Voice underneath as it was, on
  a new session or a conversation — `HistoryViewModelTest.backReturnsToVoiceUnderneathAsItWas`
- [x] History alone on the stack gives way to a new voice session, `VoiceEntry()`, never a dead
  end — `HistoryViewModelTest.historyAloneGivesWayToANewVoiceSessionOnBack`
- [x] A row tap pushes `ChatEntry(id)` over History, `[VoiceEntry(), HistoryEntry, ChatEntry(id)]`,
  a second tap opening it once; the thread's back returns to History and History's to Voice —
  `HistoryViewModelTest.aTapOnARowOpensItsConversationAsTextOverHistoryWhoseBackReturnsHereThenToVoice`
- [x] A row tap pops nothing, whatever is under History (Voice on a session or a conversation, a
  chat, nothing) — `HistoryViewModelTest.aTapOnARowPopsNothingWhateverIsUnderHistory`
- [x] A tap while a row is open only closes it, and the next tap pushes the thread over History —
  `HistoryViewModelTest.aTapWhileARowIsOpenClosesItAndOpensNothing`
- [x] The bar offers back and Settings and no Memory control —
  `HistoryUiStateTest.theBarOffersBackAndSettingsAndNoMemory`
- [x] The status row still counts what History lists, voice conversations included, which open as
  text like any other — `HistoryViewModelTest.theCountFollowsTheConversationsAsTheyComeAndGo`,
  `RoomVoiceRepositoryTest.voiceConversationsAreListedInHistoryAndOpenAsTextLikeAnyOther`
- [x] Koin builds History's ViewModel on the app's navigator —
  `HistoryModuleTest.koinBuildsAHistoryViewModelOnTheAppsNavigator`

### The thread (`:feature:chat`)

Back

- [x] Back returns to the screen underneath as it was: History for a row's conversation, Voice for
  "Aa" (on a conversation or a new chat) —
  `ChatViewModelNavigationTest.backReturnsToTheScreenUnderneathHistoryOrVoiceAsItWas`
- [x] With nothing underneath, back gives way to a new voice session, `VoiceEntry()` —
  `ChatViewModelNavigationTest.backWithNothingUnderneathGivesWayToANewVoiceSession`

The mic

- [x] A tap on the mic goes on in voice with the conversation: `VoiceEntry(id)` replaces Voice and
  everything over it, whatever session Voice was on, so Voice is the root again; a second tap
  changes nothing — `ChatViewModelNavigationTest.theMicGoesOnInVoiceWithTheConversationAsTheRootOfTheStack`
- [x] In a thread opened through "Aa", the mic returns to the Voice underneath, the same key —
  `ChatViewModelNavigationTest.theMicInAThreadOpenedThroughTextReturnsToVoiceOnTheSameConversation`
- [x] With no conversation yet (`ChatEntry()`, which "Aa" opens before the first transcript), the
  mic opens a new session, `VoiceEntry()` —
  `ChatViewModelNavigationTest.theMicWithNoConversationYetOpensANewVoiceSession`
- [x] After a new chat's first send, the mic goes on with the conversation the send created —
  `ChatViewModelNavigationTest.theMicAfterANewChatsFirstSendGoesOnInVoiceWithTheConversationItCreated`

The empty state and fork

- [x] Over Voice, a "recent" row opens its conversation in place, the stack unchanged, and the mic
  and back follow it — `ChatViewModelNavigationTest.aRecentRowOverVoiceOpensInPlaceAndTheMicAndBackFollowIt`
- [x] "⑂ fork here" replaces the thread with the fork and keeps Voice and History under it —
  `ChatViewModelNavigationTest.forkHereReplacesTheThreadAndKeepsVoiceAndHistoryUnderIt`,
  `ChatViewModelMessageActionsTest.forkHereReplacesTheChatWithTheForkWhichSaysForkedNewConversationFor1400Ms`

The controls

- [x] The bar has the back chevron and the composer the mic and Send; there is no History, new-chat
  or "+" control — `ChatViewModelNavigationTest.theBarHasBackAndTheComposerTheMicWithNoHistoryNewChatOrAttach`
- [x] What "new chat" reset now follows only an open conversation that is gone: the empty state on
  the default model with the draft kept and "copied" cleared, a persona dropped, compare off, no
  fork banner, the read stopped and the selection cleared —
  `ChatViewModelTest.aConversationGoneWhileOpenGoesBackToANewChatOnTheDefaultModelKeepingTheDraft`,
  `ChatViewModelPickerTest.aConversationGoneWhileOpenGoesBackToTheDefaultModelDroppingItsPersona`,
  `ChatViewModelCompareTest.aComparingConversationGoneWhileOpenGoesBackToANewChatWithCompareOff`,
  `ChatViewModelMessageActionsTest.aForkGoneWhileOpenLeavesANewChatWithNoForkBanner`,
  `ChatViewModelMessageActionsTest.readingStopsOnItsCrossOnAnotherConversationAndWhenTheConversationIsGone`
- [x] A send that answers after a "recent" row was opened leaves that row's conversation open —
  `ChatViewModelTest.aSendThatAnswersAfterARecentRowWasOpenedLeavesThatConversationOpen`
- [x] Message actions, compare and streaming are unchanged: every other `ChatViewModel*Test`,
  `ChatUiStateTest` and `ChatFormatsTest` passes as before, the previews included —
  `ChatUiStateTest.previewsCoverEveryStateInLightAndDark`

### `:core:ui`, `:core:navigation`, `:app`

- [x] `ChatIcons` no longer has `Add`, `Memory` or `NewChat`, whose drawables are gone, and still
  reaches every bundled icon once — `ChatIconsTest.everyBundledIconIsReachableFromOtherModulesOnce`
- [x] Every key still survives the back stack's save —
  `AppEntriesTest.theSavedBackStackRestoresEveryScreenTheAppShows`,
  `VoiceEntryTest.aSavedBackStackRestoresANewSessionAndAConversationGoingOnInVoice`

On a device (not automatable here): the thread's bar (chevron, chip as wide as before, the spacer)
and composer (no "+", the draft wider) in light and dark; History's bar without Memory; back,
system back and the iOS edge swipe through `[Voice, History, thread]`; the mic from a History
thread landing on Voice listening on that conversation's next exchange, and system back from there
leaving the app; the mic from an "Aa" thread returning to the same Voice session (mute kept); a
fast double tap on a back chevron while the screen leaves (it can pop the screen underneath too, as
row 5's back could); TalkBack and VoiceOver reading "Back" on both bars.

## Data contract

None new. Voice conversations are ordinary conversations (spec 08), so History lists, counts and
searches them and the thread opens, continues, forks and compares them with the existing
repositories. No module under `:data` or `:core:database` changes.

## UI contract

What row 10 and later rows extend.

- **Stacks:**

  | Path | Back stack |
  | --- | --- |
  | Signed in (Splash, sign-in) | `[VoiceEntry()]` |
  | Voice's History button | `[VoiceEntry(x), HistoryEntry]`; back → `[VoiceEntry(x)]` |
  | A History row | `[VoiceEntry(x), HistoryEntry, ChatEntry(id)]`; back → History → Voice |
  | Voice's "Aa" | `[VoiceEntry(x), ChatEntry(x)]`, `ChatEntry()` before the first transcript; back → Voice |
  | A "recent" row in `ChatEntry()` | the same stack; the thread shows that conversation |
  | The thread's mic | `[VoiceEntry(id)]`, `VoiceEntry()` with no conversation yet |
  | "⑂ fork here" | the thread replaced by `ChatEntry(forkId)`, what is under it kept |
  | History or the thread alone, back | `[VoiceEntry()]` |

- **History:** `HistoryScreen(uiState, onBack, onSettingsClick, onQueryChange, onClearQuery,
  onRowClick, onSwipeStart, onSwipeSettle, onRowAction, modifier)`. `HistoryViewModel.onBack()` is
  `navigateUp()`, else `navigate(VoiceEntry()) { popUpTo<HistoryEntry> { inclusive = true } }`;
  `onRowClick(id)` is `navigate(ChatEntry(id))`.
- **Thread:** `ChatScreen(uiState, onBack, onModelClick, onOpenRecent, onDraftChange, onSend,
  onVoiceClick, onCopyCode, onDismissForkBanner, messageActions, modifier)`, with
  `ChatTopBar(chipLabel, onBack, onModelClick)` and `ChatComposer(draft, draftRows, placeholder,
  canSend, onDraftChange, onSend, onVoiceClick)`. `ChatEntryProvider` wires `onBack =
  ChatViewModel.onBack()` (`navigateUp()`, else `navigate(VoiceEntry()) { popUpTo<ChatEntry> {
  inclusive = true } }`) and `onVoiceClick = ChatViewModel.onOpenVoice()`
  (`navigate(VoiceEntry(uiState.conversationId)) { popUpTo<VoiceEntry> { inclusive = true } }`),
  both extensions next to the class. `ChatViewModel.onNewChat` is now the private `startNewChat`,
  for a conversation that is gone.
- **No-ops left for row 10:** `onSettingsClick = {}` in `VoiceEntryProvider` and in
  `HistoryEntryProvider` ("Settings is row 10"). Row 10 pushes its Settings key over the screen it
  is opened from, so its back (`navigateUp()`) returns there.
- **`:core:ui`:** `ChatIcons` is `Back`, `Close`, `Menu`, `Mic`, `MicOff`, `Search`, `Send`,
  `Settings`.

## Decided

- Decided: a History row is a plain `navigate(ChatEntry(id))`, without spec 08's suggested
  `popUpTo<ChatEntry> { inclusive = true }`. Over Voice that `popUpTo` found nothing, but over any
  stack with a chat under History it would have popped History with it; a plain push keeps whatever
  is under History, and a second tap on the same row is an equal key, which navigates nowhere.
- Decided: the mic is always `navigate(VoiceEntry(conversationId)) { popUpTo<VoiceEntry> {
  inclusive = true } }`, with no second branch for "pop back when Voice underneath is on the same
  conversation": when it is (a thread opened through "Aa"), the stack ends on an equal key, which
  Navigation 3 keeps as the same entry (its content key is the key's text), so Voice's ViewModel,
  its session and its mute stay, as a pop would. From a History thread it drops History too: Voice
  is the root (spec 08), so back from it leaves the app rather than reopening a thread the user
  just left for voice. The thread reads only the key under it, so a `VoiceEntry()` whose first
  transcript already created the conversation is replaced by `VoiceEntry(id)` on that same
  conversation, which listens on its next exchange as the old one would have; only mute starts
  over. On a stack with no Voice at all (not reachable since row 8), Voice is pushed over the thread.
- Decided: the mic uses the thread's conversation as it is now, `ChatUiState.conversationId`, not
  the entry's: after a new chat's first send, or a "recent" row opened in `ChatEntry()`, Voice goes
  on with that conversation. With none yet, `VoiceEntry()`.
- Decided: both backs fall back to `VoiceEntry()` in place of the screen when nothing is underneath,
  as row 5's History fell back to a new chat; Voice is home. System back and the iOS edge swipe are
  `NavDisplay`'s own pop, as before, so with nothing underneath they leave the app (Android) or do
  nothing (iOS), as on Voice.
- Decided: the thread's back chevron is `ChatIcons.Back` labelled "Back", as History's. The bar keeps
  a 44 px spacer in New chat's place, so the chip keeps the frame's width and stays centred, as the
  prototype's Voice bar balances its chevron with a 44 px spacer.
- Decided: the composer's mic keeps its "Voice" label (the prototype's `aria-label`) and is a plain
  tap; holding it to record is not built (recording was dropped with attachments).
- Decided: the empty state stays, "recent" included, and its tips drop what is not built: "type or
  paste code" without "· drop files", and "tap mic for voice mode" in place of "hold mic to talk ·
  tap for voice mode". The other two tips are the prototype's.
- Decided: "new chat"'s reset stays, private (`startNewChat`), for the one case that still needs
  it: an open conversation that is gone (deleted, or an entry restored for one that is gone). The
  tests that drove it through the control drive it through a deletion now, and the guard against a
  send answering late is tested with a "recent" row, the one way left to change conversation in
  place.
- Decided: the dead code these removals left is gone: History's `onMemoryClick` and
  `history_memory`; the thread's `onHistoryClick`, `onNewChat`, `onAttachClick`, `onOpenHistory`,
  `chat_history`, `chat_new_chat` and `chat_attach`; `:core:ui`'s `ChatIcons.Add`, `Memory` and
  `NewChat` with their drawables, which nothing draws any more. `ChatIcons.Menu` stays for Voice's
  History button and `Settings` for Voice and History.
- Decided: no `runComposeUiTest`, as rows 3–8. That a control is gone is checked on the labels the
  module bundles (`allStringResources`): a control the screen no longer has has no label left to be
  announced by.
- Decided: History's status row is unchanged, "on this device · n conversations" (row 5).

## Out of scope

- Settings (row 10): the Settings buttons on Voice and History stay `{}`.
- Memory, attachments, recording with a held mic, and App lock: dropped by the user (2026-10-28).
- Starting a new chat from the thread: new sessions start on Voice, and "Aa" before the first
  transcript opens `ChatEntry()`.
- Any data or schema change.
