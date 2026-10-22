# Model picker

## Goal

The user taps the model chip and picks what answers: one of the eight models, or a persona that
answers with its own model. The pick changes the open conversation, or the new chat before its
first send, and the accent follows it at once. They star their favorite models, and new chats start
on a default model that Settings (row 10) changes. Favorites, personas, the default model and each
conversation's model and persona stay on the device and survive restarts.

## Frames

Every line number refers to `.claude/screens/design/ctx Prototype.dc.html`; `frames.md` §2.7, §2.2
and §3 describe them.

| Frame | States | Design source |
| --- | --- | --- |
| `Model picker` sheet | `sheet:'chat'` (title "Model", compare button), `sheet:'default'` (title "Default model", no compare button); scrim, grabber | lines 476–518; `sheetOpen`, `sheetForChat`, `sheetTitle`, `closeSheet` 928 |
| `Model picker` rows | "favorites" (models in `favs`, `MODELS` order), "personas", "+ new persona" (no handler), "all models" with "★" / "☆"; dot in the model's hue; meta; mark "●" active | lines 488–516; `mRow` 848–853; `favRows`, `personaRows`, `allRows` 931 |
| Pick rules | persona, model, favorite toggle (compare rules are row 7) | `mRow.pick` 851–853, `mRow.fav` 850; `toggleCompare` 930 |
| `Chat` persona parts | chip "Code reviewer · sonnet-4.5", empty-state meta "… · persona: Code reviewer", assistant meta "sonnet-4.5 · code reviewer · 14:02" | `chipLabel`, `modelMetaLine` 888; meta 813; frames §2.2 |
| Catalogue and state | `MODELS`, `PERSONAS`; `modelId`, `personaId`, `sheet`, `favs` | lines 542–556; state 634 |

## Acceptance criteria

Each criterion is one observable behaviour with at least one test that checks it. The data
criteria run on the Android host and the iOS simulator unless marked iOS.

### Database (`:core:database`, version 3)

- [x] Given a new in-memory database, when it opens, then it is on version 3, and
  `schemas/…ChatDatabase/3.json` records its identity hash —
  `ChatDatabaseTest.inMemoryDatabaseOpensOnTheCurrentVersion`,
  `ExportedSchemaTest.currentVersionIsExportedWithTheCompiledIdentityHash`
- [x] Given a version 2 database with a conversation and its messages, when it migrates, then
  version 3 validates, keeps every row with no persona, and has an empty `personas` table (iOS) —
  `MigrationTestHelperTest.migratesVersionTwoToThreeKeepingEveryConversationAndMessageWithoutAPersona`
- [x] Personas read in their position order, the id breaking a tie, and inserting personas again
  keeps the rows already there and only adds the missing ones — `PersonaDaoTest.aNewDatabaseHasNoPersonas`,
  `PersonaDaoTest.personasAreObservedInTheirPositionOrderWhateverTheirIds`,
  `PersonaDaoTest.insertingPersonasAgainKeepsTheRowsAlreadyThereAndAddsTheMissingOnes`,
  `PersonaDaoTest.aTieInPositionIsOrderedById`
- [x] Setting a conversation's model rewrites only its model and persona, leaves `updatedAt` and
  "recent" alone, and changes nothing for an unknown id — `ConversationDaoTest.aNewConversationHasNoPersona`,
  `ConversationDaoTest.settingTheModelRewritesOnlyItsModelAndPersona`,
  `ConversationDaoTest.settingTheModelOfAConversationThatDoesNotExistChangesNothing`
- [x] A reply keeps the persona it was written as — `MessageDaoTest.aReplyKeepsThePersonaItWasWrittenAs`
- [x] Koin provides `PersonaDao` on the one database (iOS) —
  `CoreDatabaseModuleTest.koinProvidesTheDaosAndATransactionRunnerOnThatDatabase`

### Data (`:data:chat`)

Personas

- [x] The presets are the prototype's three `PERSONAS` to the character, each on a catalogue model —
  `PersonaPresetsTest.*`
- [x] A fresh install reads the three presets, in the prototype's order, from its first emission —
  `LocalModelRepositoryTest.aFreshInstallSeedsTheThreePresetPersonasInThePrototypesOrder`
- [x] An install upgraded from version 2 gets the presets and keeps its conversations —
  `LocalModelRepositoryTest.anInstallUpgradedFromVersionTwoGetsThePresetsAndKeepsItsConversations`
- [x] Seeding runs on every start and is idempotent: a stored persona is kept as it is and only a
  missing preset is added — `LocalModelRepositoryTest.seedingOnEveryStartKeepsThePersonasStoredAndOnlyAddsTheMissingPresets`
- [x] A persona row reads with its catalogue model and back; a persona id that is not stored reads
  as no persona — `EntityMappersTest.aPersonaRowReadsWithItsCatalogueModelAndBack`,
  `EntityMappersTest.aConversationRowReadsWithItsStoredPersonaAndAnUnknownOneAsNone`,
  `EntityMappersTest.anAssistantRowReadsWithItsBlocksStatusAndLatency`,
  `RoomChatRepositoryTest.aPersonaThatIsNoLongerStoredReadsAsNoneOnItsConversationAndReplies`

Favorites and the default model

- [x] The favorites are Claude Sonnet 4.5, GPT-5 and Gemini 2.5 Pro until the user changes them —
  `LocalModelRepositoryTest.theFavoritesAreSonnetGptFiveAndGeminiUntilTheUserChangesThem`
- [x] Starring and unstarring keep the favorites in catalogue order; removing all of them leaves
  none rather than the defaults; setting a favorite to what it is writes nothing —
  `LocalModelRepositoryTest.favoritesKeepTheCatalogueOrderWhateverOrderTheyWereAddedIn`,
  `LocalModelRepositoryTest.removingEveryFavoriteLeavesNoneRatherThanTheDefaults`,
  `LocalModelRepositoryTest.settingAFavoriteToWhatItAlreadyIsChangesNothing`
- [x] The default model is Claude Sonnet 4.5 until another is set —
  `LocalModelRepositoryTest.theDefaultModelIsClaudeSonnetUntilAnotherIsSet`
- [x] A model the catalogue does not have can be neither a favorite nor the default, and stored ids
  it no longer has read as the default model and are left out of the favorites —
  `LocalModelRepositoryTest.aModelOutsideTheCatalogueCanBeNeitherAFavoriteNorTheDefault`,
  `LocalModelRepositoryTest.storedIdsTheCatalogueNoLongerHasReadAsTheDefaultModelAndAreLeftOutOfTheFavorites`
- [x] The default model and the favorites each emit only when they change —
  `LocalModelRepositoryTest.theDefaultModelAndTheFavoritesOnlyEmitWhenThemselvesChange`
- [x] The favorites and the default model survive a restart in a real preferences file, an empty
  set of favorites included — `ModelPreferencesPersistenceTest.theFavoritesAndTheDefaultModelSurviveARestart`,
  `ModelPreferencesPersistenceTest.noFavoritesLeftIsStillNoneAfterARestart`

A new chat's first send

- [x] With no choice, the first send starts on the default model the user set, with no persona —
  `RoomChatRepositoryTest.aFirstSendWithNoChoiceStartsOnTheDefaultModelTheUserSet`,
  `RoomChatRepositoryTest.aFirstSendCreatesAConversationTitledAfterTheMessageOnTheDefaultModel`
- [x] The model picked before the first send is the new conversation's and its reply's, and the
  default stays as it was — `RoomChatRepositoryTest.aFirstSendStartsOnTheModelPickedBeforeItAndLeavesTheDefaultAlone`
- [x] A persona picked before the first send is the conversation's and its reply's, on the model
  the persona answers with — `RoomChatRepositoryTest.aFirstSendWithAPersonaStartsOnThePersonaAndTheModelItAnswersWith`,
  `ModelChoiceTest.*`
- [x] A choice whose model is not in the catalogue or whose persona is not stored sends nothing —
  `RoomChatRepositoryTest.aNewChatChoiceThatIsNotInTheCatalogueOrNotStoredSendsNothing`
- [x] An open conversation answers with its own model and persona and ignores a new-chat choice —
  `RoomChatRepositoryTest.anOpenConversationAnswersWithItsOwnChoiceAndIgnoresANewChatOne`

An open conversation

- [x] Choosing a model changes the conversation, drops its persona and keeps its place in
  "recent" — `RoomChatRepositoryTest.choosingAModelChangesTheConversationDropsItsPersonaAndKeepsItsPlaceInRecent`
- [x] Choosing a persona picks the model it answers with —
  `RoomChatRepositoryTest.choosingAPersonaPicksTheModelItAnswersWith`
- [x] The next reply comes from the new choice, while the reply on its way and earlier ones keep
  the model and persona they were written with —
  `RoomChatRepositoryTest.theNextReplyComesFromTheNewChoiceWhileTheReplyOnItsWayAndEarlierOnesKeepTheirs`
- [x] Choosing for a conversation that does not exist, or a choice that is not stored, changes
  nothing and returns `false` — `RoomChatRepositoryTest.choosingForAConversationThatDoesNotExistOrAChoiceThatIsNotStoredChangesNothing`
- [x] A conversation's observers see a choice as soon as it is made, and only when it changes —
  `RoomChatRepositoryTest.aConversationsObserversSeeAChoiceAsSoonAsItIsMade`
- [x] Koin provides one `LocalModelRepository` as `ModelRepository`, and `RoomChatRepository` on it —
  `DataChatModuleTest.koinProvidesOneLocalModelRepositoryOnThePersonaDaoAndThePlainPreferences`,
  `DataChatModuleTest.koinProvidesOneRoomChatRepositoryOnTheDatabasesDaos`

### UI (`:feature:chat`, `:app`) — placeholder for the feature agent

> The feature agent replaces this section with the UI criteria and their tests: the sheet
> (`sheet:'chat'` from the model chip; title, grabber, scrim, sections, rows, dots, meta, marks,
> "★" / "☆", "+ new persona" with no handler), the pick rules on the calls in "Data contract", the
> chip, empty-state meta and reply meta with the persona, the new chat's choice, and the accent
> following the pick at once. Previews in light and dark for each state. The compare button, hint
> and "◐" mark are row 7.

## Data contract

- **Module and package:** `:data:chat`, `br.com.weslleycampos.chat.data.chat` (models in `….models`).
  It now also depends on `:core:preferences`. `DataChatModule` (already in `AppModule`'s `includes`,
  next to `PreferencesModule` and `CoreDatabaseModule`) binds both repositories.
- **Repositories:**

  ```kotlin
  interface ModelRepository {
      fun observeFavorites(): Flow<List<ChatModel>>
      suspend fun setFavorite(model: ChatModel, favorite: Boolean)
      fun observePersonas(): Flow<List<Persona>>
      fun observeDefaultModel(): Flow<ChatModel>
      suspend fun setDefaultModel(model: ChatModel)

      companion object { val DEFAULT_FAVORITE_IDS = listOf("sonnet", "gpt5", "gemini") }
  }

  interface ChatRepository {
      fun observeConversation(id: Long): Flow<Conversation?>          // now with persona
      fun observeMessages(conversationId: Long): Flow<List<Message>>  // replies with persona
      fun observeRecentConversations(): Flow<List<Conversation>>
      suspend fun send(conversationId: Long?, text: String, newChat: ModelChoice? = null): Long?
      suspend fun choose(conversationId: Long, choice: ModelChoice): Boolean

      companion object { const val RECENT_CONVERSATIONS = 3 }
  }
  ```

- **Models:**
  - `Persona(id: String, name: String, model: ChatModel, prompt: String)`;
    `object PersonaPresets { val personas: List<Persona> }` — reviewer / "Code reviewer" / sonnet /
    "Correctness first, style last. Cite line numbers. No praise."; terse / "Terse" / gpt5 / "One
    paragraph max. Code over prose."; digest / "Paper digest" / gemini / "Claim, method, evidence,
    caveats. Bullets."
  - `ModelChoice(model: ChatModel, persona: Persona? = null)` with `constructor(persona: Persona)`
    (= `ModelChoice(persona.model, persona)`): the prototype's `modelId` + `personaId`.
  - `Conversation(id, title, model, persona: Persona? = null, createdAt, updatedAt)` with
    `val choice: ModelChoice`; `Message.Assistant(…, model, persona: Persona? = null, blocks, …)`.
    Both new fields default to `null`, so row 3's named-argument calls still compile.
  - `ModelCatalogue` is unchanged; `DEFAULT_MODEL_ID` (`sonnet`) is now the default model's value
    until one is set.
- **Storage:**
  - Room version 3 with `AutoMigration(from = 2, to = 3)`, schema `3.json`: table `personas` (`id`
    TEXT primary key, `name`, `modelId`, `prompt`, `position` INTEGER); nullable `personaId` TEXT on
    `conversations` and on `messages`, no foreign key. `PersonaDao` (`observeAll()` by position then
    id; `insertIfAbsent(personas)` with `OnConflictStrategy.IGNORE`) and
    `ConversationDao.setModel(id, modelId, personaId): Int`, all bound in `CoreDatabaseModule`.
  - The `@UnencryptedPreferences` DataStore (`chat.preferences_pb`): `favorite_model_ids` (string
    set; absent = the defaults, empty = none) and `default_model_id` (string; absent = `sonnet`).
- **Koin binding:** in `ChatBindings.kt`, `@Single internal fun modelRepository(personaDao,
  @UnencryptedPreferences preferences): ModelRepository` → `LocalModelRepository` with a
  process-long `CoroutineScope(SupervisorJob() + Dispatchers.Default)`, and `@Single internal fun
  chatRepository(conversationDao, messageDao, transactions, models: ModelRepository): ChatRepository`
  → `RoomChatRepository`. Inject the interfaces only.
- **Behaviour:**
  - Every read emits on collection and again on every change that alters it, never the same value
    twice in a row. `observeFavorites` is in `ModelCatalogue` order; `observePersonas` in position
    order (the presets: reviewer, terse, digest); `observeDefaultModel` is `ModelCatalogue.default`
    until set. Changing the favorites does not make the default model emit, and the reverse.
  - The first `LocalModelRepository` of a process inserts the presets whose id is missing; persona
    reads wait for it, so the first emission already has them, on a fresh install, an upgraded one
    and every later start alike.
  - `setFavorite` and `setDefaultModel` ignore a model the catalogue does not have. `setFavorite` to
    what it already is writes nothing.
  - `send(null, text, newChat)` creates the conversation on `newChat` (`null`: the default model,
    no persona). With a persona, the conversation answers with the stored persona's model, whatever
    `newChat.model` says. It returns `null`, writing nothing, for a model not in the catalogue or a
    persona not stored, besides row 3's cases. `send(id, …)` ignores `newChat`: the conversation
    answers with its own model and persona. Each reply records both.
  - `choose(id, choice)` writes the conversation's model and persona (a model drops the persona, a
    persona brings its model) and returns `true`; `false` and no write for an unknown conversation,
    model or persona. `updatedAt` and "recent" do not move. A reply already on its way keeps its
    model and persona. Sends and choices are serialised, so a pick made before a send applies to it.
- **What the feature builds on:**
  - Pick rules (`mRow.pick`): a persona row → `ModelChoice(persona)`, a model row →
    `ModelChoice(model)`; in an open conversation pass it to `choose(conversationId, choice)`, in a
    new chat keep it as the new chat's choice and pass it as `send(null, text, newChat = choice)`.
    Close the sheet either way. "★" / "☆" → `setFavorite(model, model !in favorites)`, the sheet
    stays open. The "●" mark: a persona row when `choice.persona?.id == persona.id`, a model row when
    `choice.persona == null && choice.model.id == model.id`, where `choice` is `Conversation.choice`
    or the new chat's.
  - A new chat starts on `ModelChoice(defaultModel)` from `observeDefaultModel()`, and "new chat"
    returns to it, dropping any persona.
  - Copy from the data: chip `"${persona.name} · ${model.shortName}"` with a persona, else
    `model.name`; empty-state meta `"${provider} · ${contextWindow} context · ${cost}"` plus
    `" · persona: ${persona.name}"`; reply meta inserts `" · " + reply.persona.name.lowercase()`
    after the short name, from the reply's own persona; picker meta `"→ ${persona.model.shortName}
    · ${persona.prompt}"` and `"${provider} · ${contextWindow} ctx · ${cost}"`.
  - Accent: inside a chat, `choice.model` of what it shows (`Conversation.model`, or the new chat's
    choice), at once on every pick; everywhere else (Splash, sign-in, and later History, Memory,
    Settings) `observeDefaultModel()`. The root wears the default model's palette; the chat wears
    its own over it.
  - Settings' "Default model" sheet (`sheet:'default'`, row 10) uses `observeDefaultModel` and
    `setDefaultModel`.

## Decided

- Decided: personas are rows of a Room `personas` table, not catalogue constants. The prototype
  calls them user data ("history, memory and personas live on this device") and draws "+ new
  persona", and the queue allows seeding presets. The models stay a constant `ModelCatalogue`.
- Decided: the presets are seeded by the repository, not by Room: the first `LocalModelRepository`
  of every process runs `insertIfAbsent(presets)` (`INSERT OR IGNORE`), and persona reads wait for
  it. One step covers a fresh install, an install upgraded from version 2 (an `@AutoMigration` adds
  the empty table and cannot insert rows) and every later start; it never overwrites a stored
  persona, so a later editor's changes would survive. A `RoomDatabase.Callback` or an
  `AutoMigrationSpec.onPostMigrate` would need two paths and would put the prototype's copy in
  `:core:database`.
- Decided: there is no API to create, edit or delete a persona. "+ new persona" has no handler in
  the prototype and no persona editor exists; the `position` column and the non-overwriting seed
  leave room for one.
- Decided: the favorites and the default model are plain preferences in the `@UnencryptedPreferences`
  DataStore, not Room. They are single settings, not records: frames §3 lists `favs` and the default
  model under "Settings / preferences", the queue puts the default model in DataStore for row 10,
  and `:core:preferences` says ordinary settings go there. A missing key is the prototype's default,
  so nothing is seeded, and an empty favorites set is stored as such.
- Decided: `:data:chat` grows rather than a new `:data:models`. `ModelCatalogue` already lives here
  and `send` needs the default model and the personas; a separate module would split the catalogue
  from the user's side of it or move it from under row 3's imports.
- Decided: `send` keeps its shape and gains `newChat: ModelChoice? = null`, used only when
  `conversationId` is `null`. Row 3's callers compile unchanged and `null` means the default model,
  as before. A choice that ignored an open conversation's own would make one send change two
  things; `choose` does that.
- Decided: `ModelChoice` carries model and persona together, so a new chat's pick is one value and
  the pick rules read as `ModelChoice(model)` / `ModelChoice(persona)`. With a persona the stored
  persona's model wins, so the pair stored is always consistent.
- Decided: choosing a model or persona does not move `updatedAt`: "recent" and History sort on the
  latest message, and a pick is not one.
- Decided: a pick while a reply is on its way is allowed, as in the prototype; that reply keeps the
  model and persona it started with, and the next one uses the new choice.
- Decided: each reply records its persona (`messages.personaId`), as it records its model, and the
  meta shows the reply's own. The prototype's meta uses the active persona for every message
  (frames.md quirk 2); a conversation that changes persona keeps an honest history here.
- Decided: `personaId` has no foreign key. A persona that is gone reads as none, leaving its
  conversations and their model alone, the way an unknown model id reads as the default.
- Decided: the prototype's one global `modelId` splits in three: each conversation's model and
  persona, a new chat's pick, and the default model. Only Settings' "Default model" (row 10)
  changes the default; a pick in the chat sheet does not. A new chat, and "new chat", start on the
  default model with no persona; the prototype's `newChat` keeps `modelId` only because its
  `modelId` is the default.
- Decided: there is no default persona: the default is a model, and new chats start without a
  persona. Row 10 decides what a persona row does on the "Default model" sheet.
- Decided: outside a chat the app's accent is the default model's, the model a new chat would
  start on; in a chat it is the shown model's, from the frame of the pick. Data exposes
  `observeDefaultModel()` for the root and nothing session-scoped.
- Decided: a model the catalogue does not have, or a persona not stored, is refused (`null` from
  `send`, `false` from `choose`, no write from the setters) rather than stored.

## Out of scope

- The compare toggle, its hint, the "◐" mark, the compare pick rules and pair replies: row 7.
- A persona editor and a handler for "+ new persona": no frame exists.
- The Settings "Default model" row and its sheet: row 10, on `observeDefaultModel` /
  `setDefaultModel`. Wiping personas and preferences on delete account: row 10 (the seed puts the
  presets back on the next start).
- The Voice chip opening the sheet: row 11.
- Persona prompts changing replies: there is no model provider; every reply stays canned.
