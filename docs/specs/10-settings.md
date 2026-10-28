# Settings: account and provider keys

## Goal

Voice's Settings button opens Settings, reduced to what the user kept (2026-10-28): the "account"
group, where the signed-in user sees who they are and how they sign in, signs out, or deletes their
account, and the "api keys" group, where they type their own key for each model provider ("we need
to have a option to add a key"). A key is stored encrypted on the device and only ever shown
masked; replies stay canned, so it is never sent anywhere. Deleting the account deletes it on
Firebase and then wipes everything the device keeps.

## Frames

Every line number refers to `.claude/screens/design/ctx Prototype.dc.html`; `frames.md` §2.5
(Settings) describes them. Only the parts below are built.

| Frame | States | Design source |
| --- | --- | --- |
| `Settings` bar | back chevron (→ the screen underneath: Voice or History; the prototype's `goHistory`), title "Settings" | markup 349–352 |
| `Settings` "account", signed in | profile row (40 x 40 initials avatar, name, email in mono, ellipsized; no "edit ›"); "Sign-in methods" with its summary ("Google", "email", "Google, email" or "none"), not expandable; "Sign out"; "Delete account" in `--danger`: idle, armed for 3000 ms by a first tap with "tap again to confirm" (`deleteArmed`), deleting ("deleting…", nothing else answers), refused (toast) | markup 355–378 without "Biometric unlock" (372–375) and the expanded methods (364–371); bindings 912–915 |
| `Settings` "api keys" | label row "api keys" · "encrypted on device · never sent in plain text"; one row per provider, Anthropic, OpenAI, Google, Meta, DeepSeek, Mistral: 7 px dot (green filled when a key is stored, else a hollow 1.5 px `--fg3` ring), name (88 wide), masked key or "not set", "edit" / "add" (none while editing) | markup 385–394; `keyRows` 860–861 |
| `Settings` key row in edit | `keyEdit === provider`, one row at most: input (focused as it opens, the provider's placeholder, typed masked), "save" (primary small), "remove" in `--danger` when a key exists; save toasts "key encrypted and stored" when a key was stored | markup 395–401; `keyRows` 861 |
| `Settings` footer | "Chat 1.0 (1) · ios" / "Chat 1.0 (1) · android" (the prototype's "ctx 0.9.2 (418)"), from the installed app's version | markup 439 |
| Toast | "key encrypted and stored"; the delete refusals and the key failures (copy written here) | frames §2.9; markup 531–534 |
| Navigation | Voice / History Settings icon → Settings over them; back → the screen underneath; "Sign out" → sign-in, stack cleared; "Delete account" x2 within 3 s → account creation, stack cleared | frames §4 |

Not built: "Local only" (379–382, no local-only mode), the expanded sign-in methods with
"connect" / "disconnect" (364–371), "Biometric unlock" (372–375), "defaults" (406–425),
"appearance" (427–433) and "data" (435–438): see Out of scope.

## Acceptance criteria

Each criterion is one observable behaviour with at least one test that checks it. Every data
criterion runs on the Android host and the iOS simulator. Every key in a test is an obvious fake,
such as `"sk-test-0000000000"`.

### Database (`:core:database`, still version 6)

No schema change: the database stays on version 6 and no schema JSON changes. `ConversationDao`
gains one query.

- [x] Deleting every conversation deletes all of them, archived and pinned included, and every
  message through the cascade, says how many it deleted (0 the second time), and keeps the
  personas — `ConversationDaoTest.deletingEveryConversationDeletesAllOfThemAndEveryMessageButKeepsThePersonas`

### Account (`:data:auth`, new files and row 2's files only)

`AndroidFirebaseAuthManager.kt` is unchanged (sha1 `c903f98b2a54da203b69334eba2d4d893afd16fd`).

- [x] Firebase's provider ids map to Google, then email and password, whatever their order —
  `SignInMethodTest.mapsFirebasesProviderIdsToGoogleThenEmailAndPasswordWhateverTheirOrder`
- [x] Each method is listed once, and Firebase's own `"firebase"` entry and providers this app never
  signs in with (GitHub, Apple, phone) are left out —
  `SignInMethodTest.listsEachMethodOnceAndLeavesProvidersThisAppNeverSignsInWithOut`
- [x] The SDKs' own provider id constants are the ones mapped, on Android and on iOS —
  `FirebaseSignInMethodTest.mapsTheAndroidSdksProviderIds`, `FIRSignInMethodTest.mapsTheIosSdksProviderIds`
- [x] A call on the signed-in user gets that user, and while nobody is signed in throws
  `AuthException.NotSignedIn` — `SignedInUserTest.aCallOnTheSignedInUserGetsThatUser`,
  `SignedInUserTest.aCallOnTheSignedInUserWhileNobodyIsSignedInThrowsNotSignedIn`
- [x] Firebase asking for a recent sign-in before a delete (`ERROR_REQUIRES_RECENT_LOGIN` /
  `FIRAuthErrorCodeRequiresRecentLogin`), or a session that expired or was revoked
  (`ERROR_USER_TOKEN_EXPIRED`, `ERROR_INVALID_USER_TOKEN` / `FIRAuthErrorCodeUserTokenExpired`,
  `FIRAuthErrorCodeInvalidUserToken`), maps to `AuthException.RequiresRecentLogin`; an account
  already deleted is still `UserNotFound` —
  `FirebaseAuthExceptionMappingTest.mapsAFreshSignInThatDeletingTheAccountNeedsToRequiresRecentLogin`,
  `NSErrorAuthExceptionMappingTest.mapsAFreshSignInThatDeletingTheAccountNeedsToRequiresRecentLogin`
- [x] The earlier mappings are unchanged: a disabled user and anything unknown are still
  `Unknown` — `FirebaseAuthExceptionMappingTest.mapsFirebaseFailuresToAuthExceptions`,
  `NSErrorAuthExceptionMappingTest.mapsFirebaseAuthErrorCodesToAuthExceptions`
- [x] On Android an `AuthException` thrown while a call starts (the `NotSignedIn` of a delete with
  nobody signed in) is rethrown as it is, not wrapped in `Unknown` —
  `FirebaseAuthExceptionMappingTest.passesAnAuthExceptionThroughAsItIs`,
  `AwaitAuthTaskTest.rethrowsAnAuthExceptionThrownWhileStartingAsItIs`
- [x] A `CancellationException` passes through the delete's wait untouched on Android, and
  cancelling the caller ends the wait on iOS — `AwaitAuthTaskTest.letsCancellationThroughUntouched`,
  `AwaitAuthCompletionTest.cancellingTheCallerStopsTheWaitAndIgnoresALateCompletion`
- [x] `DataAuthModule` provides one `AccountManager`, next to the `AuthManager` —
  `DataAuthModuleTest.koinProvidesOneAccountManagerNextToTheAuthManager`

The calls into Firebase themselves (the auth state listener's provider data, `FirebaseUser.delete()`
and `FIRUser.deleteWithCompletion`) need a configured Firebase app and a network, which the host
and simulator tests do not have; `:androidApp:assembleDebug` and
`:app:linkDebugFrameworkIosSimulatorArm64` compile them.

### Wipe (`:data:chat`)

- [x] Erasing deletes every conversation and message, pinned and archived ones included, and the
  favorites and default model, which read as on a fresh install (sonnet, gpt5, gemini; sonnet);
  the persona presets stay, and preferences that are not the chat's are left alone —
  `LocalChatDataEraserTest.erasingDeletesEveryConversationAndMessageAndTheChoicesButKeepsThePersonas`
- [x] A reply still streaming when the data is erased stops at its next tick —
  `LocalChatDataEraserTest.erasingWhileAReplyStreamsStopsItAtItsNextTick`
- [x] Erasing again writes nothing, and the next chat starts afresh on the default model —
  `LocalChatDataEraserTest.erasingAgainErasesNothingMoreAndTheNextChatStartsAfresh`
- [x] `DataChatModule` provides one `LocalChatDataEraser` as `ChatDataEraser` —
  `DataChatModuleTest.koinProvidesOneLocalChatDataEraserOnTheConversationDaoAndThePlainPreferences`

### Provider keys (`:data:settings`)

- [x] The providers are the prototype's, in its order, Anthropic, OpenAI, Google, Meta, DeepSeek,
  Mistral, with stable ids — `ModelProviderTest.theProvidersAreThePrototypesInItsOrder`
- [x] Anthropic's placeholder is "sk-ant-…", OpenAI's "sk-…", the others' "paste key" —
  `ModelProviderTest.anthropicAndOpenAiHintTheirKeyPrefixAndTheOthersSayPasteKey`
- [x] Every model in the catalogue has its provider's row, and every row a model —
  `ModelProviderTest.everyModelInTheCatalogueHasItsProvidersKeyRow`
- [x] A key of 22 characters or more is masked as its first 7, "…" and its last 4, as the
  prototype's save does — `KeyMaskTest.aKeyOf22CharactersOrMoreShowsItsFirst7AndLast4AroundAnEllipsisAsThePrototypeDoes`
- [x] A key of 8 to 21 characters shows only "…" and its last 4, and a shorter one only "…" —
  `KeyMaskTest.aShorterKeyOf8To21CharactersShowsOnlyAnEllipsisAndItsLast4`,
  `KeyMaskTest.aKeyOfFewerThan8CharactersShowsOnlyAnEllipsis`
- [x] The mask never shows more than half of a key, whatever its length —
  `KeyMaskTest.theMaskNeverShowsMoreThanHalfOfAKey`
- [x] On a fresh install every provider is listed, in order, with no key —
  `EncryptedProviderKeyRepositoryTest.everyProviderIsListedInThePrototypesOrderAndNoneHasAKeyOnAFreshInstall`
- [x] A saved key is stored trimmed under its provider, `save` returns `true`, and it reads back
  only masked; no row's text holds the key —
  `EncryptedProviderKeyRepositoryTest.aSavedKeyIsStoredTrimmedAndReadBackOnlyMasked`,
  `EncryptedProviderKeyRepositoryTest.aShortKeyIsMaskedToLessThanHalfOfIt`
- [x] Saving a blank key stores nothing, returns `false` and keeps the key the provider had, as the
  prototype's empty save does — `EncryptedProviderKeyRepositoryTest.savingABlankKeyStoresNothingAndKeepsTheKeyTheProviderHad`
- [x] Saving again replaces the provider's key —
  `EncryptedProviderKeyRepositoryTest.savingAgainReplacesTheProvidersKey`
- [x] Removing a key removes only that provider's and says whether it had one —
  `EncryptedProviderKeyRepositoryTest.removingAKeyRemovesOnlyThatProvidersAndSaysWhetherItHadOne`
- [x] Removing every key leaves none and leaves other preferences alone; doing it again writes
  nothing — `EncryptedProviderKeyRepositoryTest.removingEveryKeyLeavesNoneAndLeavesOtherPreferencesAlone`
- [x] The rows emit on collect and again only when a row changes: saving the same key again, a
  blank save and removing a missing key emit nothing —
  `EncryptedProviderKeyRepositoryTest.theKeysEmitOnCollectAndOnlyWhenARowChanges`

### Account (`:data:settings`)

- [x] The account is the signed-in user with how they sign in, emits when that changes, never
  twice with the same value, and is `null` once signed out —
  `FirebaseAccountRepositoryTest.theAccountIsTheSignedInUserWithHowTheySignInAndNullWhileSignedOut`
- [x] Signing out keeps every key and the chat's data —
  `FirebaseAccountRepositoryTest.signingOutKeepsEveryKeyAndTheChatsData`
- [x] Deleting the account deletes it on Firebase first, and only once that returned removes every
  key and then erases the chat's data —
  `FirebaseAccountRepositoryTest.deletingTheAccountDeletesItOnFirebaseFirstThenWipesTheKeysAndTheChatsData`
- [x] A delete Firebase refuses (`RequiresRecentLogin`, `NotSignedIn`, `NetworkError`, `Unknown`)
  throws that `AuthException` and wipes nothing; the user stays signed in —
  `FirebaseAccountRepositoryTest.aDeleteFirebaseRefusesThrowsItsAuthExceptionAndWipesNothing`
- [x] A `CancellationException` from Firebase is rethrown untouched and wipes nothing —
  `FirebaseAccountRepositoryTest.aCancellationFromFirebaseIsRethrownUntouchedAndWipesNothing`
- [x] Cancelling the caller while Firebase deletes neither stops the delete nor the wipe after it;
  the caller then ends cancelled —
  `FirebaseAccountRepositoryTest.cancellingTheCallerWhileFirebaseDeletesStillWipesTheDeviceOnceTheDeleteSucceeds`
- [x] `DataSettingsModule` provides one `EncryptedProviderKeyRepository` and one
  `FirebaseAccountRepository` —
  `DataSettingsModuleTest.koinProvidesOneEncryptedProviderKeyRepositoryOnTheEncryptedPreferences`,
  `DataSettingsModuleTest.koinProvidesOneFirebaseAccountRepositoryOnTheAuthManagersAndTheChatsEraser`

### UI (`:feature:settings`, `:core:navigation`, `:feature:voice`, `:feature:history`, `:feature:auth`, `:app`)

The ViewModel criteria run on `SettingsViewModel` over a `NavigatorImpl` on a real `NavBackStack`,
with fakes of `AccountRepository` and `ProviderKeyRepository` that keep the real ones' contracts,
on virtual time. There is no `runComposeUiTest`, as in rows 3–9 (no JVM target and no Robolectric
on the Android host): the layout is checked in the previews and on a device, and the controls that
are not built on the labels the module bundles.

Opening and leaving

- [x] Voice's Settings button stops the phases and pushes Settings over Voice,
  `[VoiceEntry(x), SettingsEntry]`; back returns to Voice, which listens again once resumed —
  `VoiceViewModelSessionTest.settingsStopsThePhasesAndOpensOverVoiceWhichBackReturnsTo`
- [x] History's Settings button closes an open row and pushes Settings over History,
  `[VoiceEntry(x), HistoryEntry, SettingsEntry]`; back returns to History, then to Voice —
  `HistoryViewModelTest.settingsOpensOverHistoryClosingAnOpenRowAndItsBackReturnsHereThenToVoice`
- [x] Back (the chevron, system back, the iOS edge swipe) returns to the screen underneath as it
  was — `SettingsViewModelTest.backReturnsToTheScreenUnderneathVoiceOrHistoryAsItWas`
- [x] Settings alone on the stack gives way to a new voice session, `VoiceEntry()`, on back —
  `SettingsViewModelTest.settingsAloneGivesWayToANewVoiceSessionOnBack`
- [x] `SettingsEntry` and both `SignInEntry` modes survive the back stack's save —
  `SettingsEntryTest.aSavedBackStackRestoresSettings`,
  `SettingsEntryProviderTest.aSavedBackStackRestoresSettings`,
  `SignInEntryTest.aSavedBackStackRestoresSignInAndSignUp`, `SignInEntryTest.signInIsTheDefaultMode`,
  `AppEntriesTest.theSavedBackStackRestoresEveryScreenTheAppShows`
- [x] Koin provides Settings' entry provider and builds its ViewModel on the app's navigator —
  `SettingsModuleTest.koinProvidesTheEntryProvider`,
  `SettingsModuleTest.koinBuildsASettingsViewModelOnTheAppsNavigator`

The account

- [x] The profile row shows the display name's initials ("AR"), the name and the email; the
  "Sign-in methods" summary is "Google", "email", "Google, email" or "none", and follows the
  account — `SettingsViewModelTest.theAccountGroupShowsTheSignedInUserAndHowTheySignIn`,
  `SettingsUiStateTest.theProfileShowsTheDisplayNamesInitialsTheNameAndTheEmail`,
  `SettingsUiStateTest.theSignInMethodsSummaryJoinsGoogleAndEmailOrSaysNone`
- [x] With no display name (an email + password account) the name is the email's local part and
  the initials come from it ("weslley.campos" → "WC"); a blank email shows none —
  `SettingsUiStateTest.anAccountWithNoDisplayNameIsNamedAndInitialledFromItsEmailsLocalPart`
- [x] The initials are the first letters of the first and last words, upper-cased, one for one
  word, "?" with nothing to take — `SettingsUiStateTest.theInitialsAreTheFirstAndLastWordsFirstLettersUpperCased`
- [x] "Sign out" signs out once and replaces the whole stack with `SignInEntry()`, from over Voice,
  over History or alone, and keeps the keys; the account's `null` after it navigates no further —
  `SettingsViewModelTest.signOutSignsOutAndOpensSignInWithTheWholeStackCleared`
- [x] An account signed out while Settings shows (a session Firebase revoked) opens
  `SignInEntry()` with the stack cleared —
  `SettingsViewModelTest.anAccountSignedOutBehindSettingsOpensSignInWithTheStackCleared`

Delete account

- [x] A first tap arms "Delete account" for 3000 ms with "tap again to confirm", then it disarms
  and nothing is deleted — `SettingsViewModelTest.theFirstDeleteTapArmsItFor3000MsThenItDisarmsAndDeletesNothing`,
  `SettingsUiStateTest.deleteIsArmedFor3000MsAndItsHintSaysTapAgainThenDeleting`
- [x] A tap after the arming ran out arms it again for a whole 3000 ms —
  `SettingsViewModelTest.aTapAfterTheArmingExpiredArmsItAgainForAWhole3000Ms`
- [x] A second tap within 3000 ms deletes the account once and replaces the whole stack with
  `SignInEntry(signUp = true)`, over Voice or History —
  `SettingsViewModelTest.aSecondTapWithin3000MsDeletesTheAccountAndOpensSignUpWithTheWholeStackCleared`,
  `SignInViewModelTest.aSignUpEntryStartsOnCreateAccountWhoseBackLinkReturnsToSignIn`
- [x] While the delete runs the row says "deleting…", the edit row is closed with its draft, and
  another delete, sign out, back and the key rows do nothing —
  `SettingsViewModelTest.whileTheDeleteRunsTheRowIsBusyAndNothingElseAnswers`
- [x] A delete Firebase refuses shows its copy for 1400 ms ("sign in again, then delete",
  "no connection, nothing deleted", "too many attempts, try again later", else "couldn’t delete,
  try again"), wipes nothing, keeps the user signed in on Settings and can be tried again —
  `SettingsViewModelTest.aDeleteFirebaseRefusesSaysWhyFor1400MsAndTheUserStaysSignedInOnSettings`,
  `SettingsUiStateTest.eachRefusedDeleteHasItsOwnCopyAndAnythingElseTheGenericOne`
- [x] A delete Firebase cancels leaves the row usable, with no toast —
  `SettingsViewModelTest.aDeleteFirebaseCancelsLeavesTheRowUsableWithNoToast`
- [x] A wipe that fails after Firebase deleted the account says so and still opens sign-in —
  `SettingsViewModelTest.aWipeThatFailsAfterTheAccountWasDeletedSaysSoAndStillOpensSignIn`

The api keys

- [x] One row per provider in `ModelProvider` order, each with its dot (filled when a key is
  stored), its name, the masked key or "not set", the placeholder, and "edit" / "add", following
  the repository — `SettingsViewModelKeysTest.theRowsAreEveryProviderInOrderWithTheMaskedKeyTheRepositoryReads`,
  `SettingsUiStateTest.aKeyRowShowsItsDotItsNameItsMaskedKeyOrNotSetAndEditOrAdd`
- [x] The row being edited has no action and offers "remove" only for a stored key —
  `SettingsUiStateTest.theRowBeingEditedHasNoActionAndOffersRemoveOnlyForAStoredKey`,
  `SettingsViewModelKeysTest.removeIsOfferedAndDoesSomethingOnlyForAStoredKey`
- [x] A tap opens that row's edit row with an empty draft, and a tap again closes it and clears
  the draft — `SettingsViewModelKeysTest.aTapOpensThatRowsEditRowWithAnEmptyDraftAndATapAgainClosesItAndClearsTheDraft`
- [x] Only one row is edited at a time; another row starts with an empty draft —
  `SettingsViewModelKeysTest.onlyOneRowIsEditedAtATimeAndAnotherRowStartsWithAnEmptyDraft`,
  `SettingsViewModelKeysTest.typingWithNoRowOpenIsIgnored`
- [x] "save" passes the draft to `save` once, closes the row, clears the draft, and toasts "key
  encrypted and stored" for 1400 ms when it returns `true` —
  `SettingsViewModelKeysTest.saveStoresTheDraftOnceClosesTheRowAndToastsKeyEncryptedAndStoredFor1400Ms`,
  `SettingsViewModelKeysTest.savingAgainReplacesTheKeyAndToastsAgain`
- [x] A blank save keeps the old key, closes the row and shows no toast —
  `SettingsViewModelKeysTest.aBlankSaveKeepsTheOldKeyClosesTheRowAndShowsNoToast`
- [x] "remove" removes that key, closes the row, clears the draft and shows no toast —
  `SettingsViewModelKeysTest.removeRemovesTheKeyClosesTheRowAndShowsNoToast`
- [x] A save or remove the encrypted store refuses says so ("couldn’t store the key, try again",
  "couldn’t remove the key, try again") — `SettingsViewModelKeysTest.aSaveOrRemoveTheStoreRefusesSaysSoAndStoresNothing`
- [x] The draft never reaches the UI state, the saved back stack or a toast, and is cleared on
  save, on closing, on remove, on back and on signing out; a new Settings starts with none —
  `SettingsViewModelKeysTest.theDraftNeverReachesTheStateTheSavedBackStackOrTheToast`,
  `SettingsViewModelKeysTest.leavingSettingsClearsTheDraftAndANewSettingsStartsWithNone`,
  `SettingsViewModelKeysTest.signingOutClearsTheDraft`

The footer and the frame

- [x] The footer is "Chat <versionName> (<versionCode>) · android" / "· ios", leaving out what the
  platform does not report — `SettingsUiStateTest.theFooterIsChatItsVersionNameAndCodeAndThePlatform`
- [x] On iOS the version is the bundle's `CFBundleShortVersionString` and `CFBundleVersion` —
  `AppVersionIosTest.theVersionIsTheBundlesShortVersionStringAndBuildNumberOnIos`,
  `AppVersionIosTest.aBundleWithoutTheKeysReadsAsNoVersion`
- [x] Settings has only the "account" and "api keys" groups: no label for biometric unlock,
  defaults, voice, speech rate, read aloud, theme, export, connecting methods or local only —
  `SettingsUiStateTest.settingsHasOnlyTheAccountAndApiKeysGroups`
- [x] The previews cover every state in light and dark, with only a fake draft —
  `SettingsUiStateTest.previewsCoverEveryStateInLightAndDark`

On a device (not automatable here): the layout against the frame in light and dark (the cards,
hairlines, avatar, the 88 px name column, the dot, the edit row's 36 px input and buttons); the
input focusing with the keyboard up as the row opens, typed masked on a password keyboard with no
suggestions (Gboard, the iOS keyboard), Done saving; the edit row of the last provider scrolling
above the keyboard; the toast over the keyboard; the Android footer from `PackageManager`
("Chat 1.0 (1) · android"); sign out and delete against Firebase, including
`RequiresRecentLogin` on an old session; system back and the iOS edge swipe blocked while deleting;
TalkBack and VoiceOver reading the rows, the delete hint and the key input as a password field.

## Data contract

- **Modules and packages:** new `:data:settings`, `br.com.weslleycampos.chat.data.settings`
  (models in `….models`); `:data:auth`, `br.com.weslleycampos.chat.data.auth`; `:data:chat`,
  `br.com.weslleycampos.chat.data.chat`. `:data:settings` depends on `:data:auth` (`api`),
  `:data:chat` and `:core:preferences`.
- **Repositories (`:data:settings`):**

  ```kotlin
  interface ProviderKeyRepository {
      fun observeKeys(): Flow<List<ProviderKey>>
      suspend fun save(provider: ModelProvider, key: String): Boolean
      suspend fun remove(provider: ModelProvider): Boolean
      suspend fun removeAll()
  }

  interface AccountRepository {
      fun observeAccount(): Flow<Account?>
      fun signOut()
      suspend fun deleteAccount()
  }
  ```

- **Account (`:data:auth`):**

  ```kotlin
  @Provided
  interface AccountManager {
      val signInMethods: Flow<List<SignInMethod>>
      suspend fun deleteAccount()
  }

  enum class SignInMethod(val providerId: String) {
      GOOGLE("google.com"), EMAIL_PASSWORD("password");
      companion object { fun of(providerIds: Iterable<String>): List<SignInMethod> }
  }
  ```

  `AuthException` gains `RequiresRecentLogin(message, cause)` and `NotSignedIn(message, cause)`.
  `AuthManager` and `EmailAuthManager` are unchanged.

- **Wipe (`:data:chat`):** `interface ChatDataEraser { suspend fun eraseAll() }`.
- **Models (`:data:settings`):**
  - `enum class ModelProvider(id: String, displayName: String, placeholder: String)`: `ANTHROPIC`
    ("anthropic", "Anthropic", "sk-ant-…"), `OPENAI` ("openai", "OpenAI", "sk-…"), `GOOGLE`,
    `META`, `DEEPSEEK`, `MISTRAL` ("google" / "meta" / "deepseek" / "mistral", their names, "paste
    key"). `entries` is the catalogue, in the rows' order.
  - `data class ProviderKey(provider: ModelProvider, masked: String?)` with `hasKey: Boolean`
    (`masked != null`).
  - `data class Account(user: AuthUser, signInMethods: List<SignInMethod>)`.
  - Internal `KeyMask.of(key)`: 22+ characters → first 7 + "…" + last 4; 8–21 → "…" + last 4;
    under 8 → "…".
- **Storage:**
  - Keys: the `@EncryptedPreferences` DataStore (`chat.encrypted.preferences_pb`), one string
    preference `provider_key_<id>` per provider holding the trimmed key. Nothing else is written.
  - No schema change: Room stays on version 6. `ConversationDao.deleteAll(): Int` is
    `DELETE FROM conversations`, its messages going through the foreign key's cascade.
  - The wipe removes `favorite_model_ids` and `default_model_id` from the plain DataStore and
    leaves the `personas` table as it is.
- **Koin bindings:**
  - `:data:settings` (`SettingsBindings.kt`, scanned by `DataSettingsModule`, now in `AppModule`'s
    `includes`): `@Single internal fun providerKeyRepository(@EncryptedPreferences preferences:
    DataStore<Preferences>): ProviderKeyRepository` → `EncryptedProviderKeyRepository`;
    `@Single internal fun accountRepository(auth: AuthManager, accounts: AccountManager, keys:
    ProviderKeyRepository, chatData: ChatDataEraser): AccountRepository` → `FirebaseAccountRepository`.
  - `:data:auth`: `@Single internal class AndroidFirebaseAccountManager` (androidMain) and
    `IosFirebaseAccountManager` (iosMain), bound as `AccountManager` by `DataAuthModule`'s
    `@ComponentScan`.
  - `:data:chat` (`ChatBindings.kt`): `@Single internal fun chatDataEraser(conversationDao,
    @UnencryptedPreferences preferences): ChatDataEraser` → `LocalChatDataEraser`.
- **Behaviour:**
  - `observeKeys()` emits the six rows in `ModelProvider` order on collect and again when a row's
    `masked` changes (`distinctUntilChanged`). Only the masked form leaves the repository; no API
    returns a whole key.
  - `save(provider, key)`: trims; blank → `false`, nothing written, the old key kept; otherwise
    stores the trimmed key in place of any and returns `true`, even when it is the same key.
  - `remove(provider)` → whether there was a key. `removeAll()` removes every `provider_key_*`
    and nothing else; run again, it writes nothing.
  - The encrypted store's own failures pass through: an `IOException` when the Keystore or
    Keychain cannot be read (for example before the device's first unlock) fails the read or
    write; a lost key or a corrupt file empties the store, and every provider reads as not set.
  - `observeAccount()` = `combine(AuthManager.currentUser, AccountManager.signInMethods)`: `null`
    while `currentUser` is `null`, else `Account(user, methods)`; `distinctUntilChanged`.
  - `signOut()` = `AuthManager.signOut()`. It keeps every conversation, choice and key.
  - `deleteAccount()`, in `withContext(NonCancellable)`: `AccountManager.deleteAccount()`, then
    `ProviderKeyRepository.removeAll()`, then `ChatDataEraser.eraseAll()`. When it returns,
    Firebase has signed the user out. It throws what `AccountManager.deleteAccount()` throws
    (`AuthException.RequiresRecentLogin`, `NotSignedIn`, `NetworkError`, `TooManyRequests`,
    `UserNotFound`, `Unknown`, or Firebase's `CancellationException`), and then wipes nothing.
    Cancelling the caller does not stop it; it finishes, wipe included, and then throws the
    caller's `CancellationException`. A failure of the wipe itself (a disk error) propagates after
    the account is already deleted.
  - `AccountManager.signInMethods`: from the auth state listener, `SignInMethod.of(…)` over the
    user's provider data ids, `[]` while signed out, `distinctUntilChanged`.
  - `AccountManager.deleteAccount()`: `NotSignedIn` with no current user; else Firebase's
    `delete()` / `deleteWithCompletion`, failures mapped as above; cancellation untouched.
  - `ChatDataEraser.eraseAll()`: `ConversationDao.deleteAll()`, then one edit removing the two
    choice keys. A reply streaming at the time stops at its next tick.

## UI contract

- **Keys (`:core:navigation`):** `@Serializable data object SettingsEntry : NavKey`
  (`entries.settings`), and `SignInEntry` is now `data class SignInEntry(val signUp: Boolean =
  false)`: `SignInEntry()` opens sign in, `SignInEntry(signUp = true)` account creation
  (`SignInViewModel` takes the entry as `@InjectedParam` and starts on `AuthMode.SignUp`).
- **Stacks:**

  | Path | Back stack |
  | --- | --- |
  | Voice's Settings button | `[VoiceEntry(x), SettingsEntry]`; back → `[VoiceEntry(x)]` |
  | History's Settings button | `[VoiceEntry(x), HistoryEntry, SettingsEntry]`; back → History → Voice |
  | Settings alone, back | `[VoiceEntry()]` |
  | "Sign out" (or an account signed out elsewhere) | `[SignInEntry()]` |
  | "Delete account" x2 within 3000 ms, succeeded | `[SignInEntry(signUp = true)]` |
  | "Delete account" refused | unchanged, toast |

- **Screen:** `SettingsScreen(uiState, draft, appVersion, onBack, onSignOut, onDeleteAccount,
  onKeyRowClick, onDraftChange, onSaveKey, onRemoveKey, modifier)`, stateless.
  `SettingsEntryProvider` wires the ViewModel, collects `uiState` and `draft`, reads
  `rememberAppVersion()` (expect/actual: `PackageManager` on Android, `NSBundle.mainBundle` on iOS)
  and blocks system back with a `NavigationBackHandler` while `uiState.deleting`.
- **ViewModel:** `SettingsViewModel(navigator, accounts: AccountRepository, keys:
  ProviderKeyRepository)`, `@KoinViewModel(binds = [SettingsViewModel::class])`. `uiState:
  StateFlow<SettingsUiState>` (`account: AccountUi?`, `deleteArmed`, `deleting`, `keys:
  List<ProviderKey>`, `editing: ModelProvider?`, `toast: StringResource?`) and `draft:
  StateFlow<String>`, kept apart. `onBack()` is `navigateUp()`, else `navigate(VoiceEntry()) {
  popUpTo<SettingsEntry> { inclusive = true } }`; sign out and delete use `navigate(entry) {
  popUpTo(navBackStack.first()) { inclusive = true } }`, once.
- **Voice and History:** `VoiceViewModel.onOpenSettings()` (`onStop()`, then
  `navigate(SettingsEntry)`) and `HistoryViewModel.onOpenSettings()` (`onCloseRow()`, then
  `navigate(SettingsEntry)`), extensions next to each ViewModel as Voice's `onOpenHistory` is,
  replace the `onSettingsClick = {}` no-ops.
- **`:feature:settings`:** `SettingsModule` (`@ComponentScan`), in `AppModule`'s `includes`; depends
  on `:core:navigation`, `:core:ui` and `:data:settings`.

## Decided

- Decided: three pieces instead of one call in the feature. `:data:auth` gets what Firebase alone
  can do (`AccountManager`: sign-in methods and the delete), `:data:chat` erases what it owns
  (`ChatDataEraser`), and `:data:settings` owns the keys and the order (`AccountRepository`), so
  each module wipes only its own storage and the order is tested in the data layer. The Settings
  ViewModel needs only `AccountRepository` and `ProviderKeyRepository`.
- Decided: `AccountManager` is a new interface with its own platform classes, bound like
  `EmailAuthManager`, because `AndroidFirebaseAuthManager.kt` carries the user's uncommitted change
  and must not be edited. Sign out reuses `AuthManager.signOut()`.
- Decided: the order is Firebase first, the wipe only after it returned, so a failed delete loses
  nothing (the recommended order). Within the wipe the keys go first, as they are the secrets, then
  the chat's data.
- Decided: `deleteAccount()` runs in `NonCancellable`. Firebase may finish a delete it was asked
  for even if the screen goes away, and a deleted account must not leave its conversations and keys
  behind; so the call always runs to its end and then reports the caller's cancellation.
- Decided: signing out keeps the conversations, the choices and the keys. They belong to this
  device, nothing syncs, and the next sign-in finds them (the recommended choice). The prototype's
  sign out clears only the open thread from memory.
- Decided: the persona presets stay through a wipe. They are catalogue data, the same on every
  install, and the app has no user personas ("+ new persona" is inert), so there is nothing to
  reseed. The `placeholder` table is empty and left alone.
- Decided: no schema change. Deleting everything is one `DELETE FROM conversations`, whose
  cascade removes every message, so Room stays on version 6.
- Decided: masking follows the prototype's save (`v.slice(0,7)+'…'+v.slice(-4)`), not its seeded
  sample "sk-ant-api03-…4f2a", which shows 13 characters before the ellipsis. Short keys: the
  prototype's formula would show a key of 11 characters or fewer whole (or repeat it), so a key
  shorter than 22 characters, where the first 7 and the last 4 would be more than half of it,
  shows only "…" and its last 4, and one shorter than 8 only "…". The mask never shows more than
  half of a key. Every real key (32+ characters) takes the prototype's form.
- Decided: keys are stored whole and encrypted, and masked when read, rather than storing only the
  masked text as the prototype's state does, since the point of typing a key is to keep it.
  Nothing reads the whole key back yet (see Out of scope).
- Decided: an empty save keeps the old key and returns `false`, so the screen toasts "key encrypted
  and stored" only when something was stored, as the prototype toasts only for a non-empty value.
  Saving the same key again returns `true`: the user pressed save on a key.
- Decided: one preference per provider, `provider_key_<id>`, with an id that is not the enum's
  name, so renaming a constant never orphans a stored key. `removeAll()` removes only these keys,
  not the whole encrypted store.
- Decided: the providers are an enum, `ModelProvider`, so a key can only be saved for a provider
  Settings lists; its display names are the model catalogue's `provider` strings (tested).
- Decided: `RequiresRecentLogin` also covers an expired or revoked session
  (`USER_TOKEN_EXPIRED`, `INVALID_USER_TOKEN`): the remedy is the same, sign in again and retry.
  A disabled user stays `Unknown`, as row 2 decided. An account already deleted elsewhere is
  `UserNotFound` and also wipes nothing, so only a delete this device saw succeed wipes it.
- Decided: a delete with nobody signed in throws the new `AuthException.NotSignedIn` instead of
  returning, which would have wiped the device for an account that was not deleted.
- Decided: on Android `toAuthException()` now passes an `AuthException` through as it is, so the
  `NotSignedIn` thrown while starting the delete is not wrapped in `Unknown`.
- Decided: sign-in methods are mapped in common code from Firebase's provider id strings
  ("google.com", "password"), which tests check against each SDK's own constants, and are read
  from the same auth state listener as `currentUser`. Only Google and email + password exist in
  this app, so any other provider id is ignored.
- Decided: `observeAccount()` combines the two flows instead of a new `AccountManager` user flow,
  so `AuthUser` keeps coming from `AuthManager` alone.

- Decided (UI): the profile row drops the prototype's "edit ›": it is not interactive there either,
  and nothing here edits the name, email or avatar, so it would promise an action that does not
  exist.
- Decided (UI): "Sign-in methods" is a plain, non-interactive summary. Linking and unlinking are out
  of scope, so the expanded rows would only repeat the summary without their buttons. The labels
  are "Google" (the brand) and "email" (lowercase like the rest of the mono copy), joined by ", "
  in `SignInMethod` order, or the prototype's "none".
- Decided (UI): the name falls back to the email's local part ("you" for "you@example.dev") rather
  than the whole email, so the email under it is not repeated; the initials come from the name,
  its words split on whitespace and on ".", "_", "+" and "-" (first and last word), "?" when there
  is nothing to take.
- Decided (UI): `SignInEntry` carries the mode as `signUp: Boolean = false`, so deleting the account
  opens account creation as the prototype does (`authMode:'signup'`). It is only the first mode;
  "← sign in" and system back switch to sign in as usual. Splash and sign out use `SignInEntry()`.
- Decided (UI): sign out and delete clear the whole stack (`popUpTo(navBackStack.first()) {
  inclusive = true }`), since what was under Settings belonged to the account; an account that
  becomes `null` while Settings shows and no delete runs (a revoked session) does the same, and
  each leaves only once, so the `null` that follows a sign-out navigates no further.
- Decided (UI): while the delete runs, the row says "deleting…" and every other control waits:
  another delete, sign out, the key rows, the back chevron, and system back and the iOS edge swipe
  (a `NavigationBackHandler`). The delete is non-cancellable in the data layer, and leaving
  mid-delete would strand a signed-out user on Voice. The edit row closes when it starts, so no
  key is saved after the wipe.
- Decided (UI): the refusal copy, a toast for the usual 1400 ms, as the prototype has none:
  `RequiresRecentLogin` "sign in again, then delete", `NetworkError` "no connection, nothing
  deleted", `TooManyRequests` "too many attempts, try again later", anything else (`NotSignedIn`,
  `UserNotFound`, `Unknown`) "couldn’t delete, try again". A Firebase cancellation only frees the
  row. A wipe that fails after the account was deleted shows the generic copy and opens sign-in,
  since the user is signed out.
- Decided (UI): the key is typed masked (`PasswordVisualTransformation`, password semantics), on a
  `KeyboardType.Password` keyboard with autocorrect off and no capitalisation, Done saving. The
  prototype shows it in plain mono, but a pasted key is seldom read character by character, and
  plain text would leave the whole key in screenshots, screen recordings, the recent-apps
  thumbnail and over the user's shoulder, while everywhere else the app only shows it masked.
  The masked form after save ("sk-ant-…4f2a") is the check that the right key went in.
- Decided (UI): the draft is not part of `SettingsUiState` but a separate `StateFlow<String>` in
  the ViewModel, so nothing that prints, compares or saves the state can carry a key; Settings has
  no `SavedStateHandle`, its key `SettingsEntry` has no arguments, and the input's value comes from
  the ViewModel, not `rememberSaveable`. The draft is cleared on save, on closing its row (a tap on
  the row or another row), on remove, on back, on sign out and delete, and in `onCleared`. It is
  kept while the app is only in the background, so a key can be copied from another app and pasted.
- Decided (UI): "save" with a blank draft still calls `save`, which keeps the old key and returns
  `false` (no toast), as the prototype's empty save; it closes the row either way. A save or
  remove the encrypted store refuses shows "couldn’t store the key, try again" / "couldn’t remove
  the key, try again" (copy written here); the prototype has no failure. "remove" has no toast, as
  in the prototype.
- Decided (UI): the input is a `BasicTextField` styled with `ChatTheme` tokens as the frame draws it
  (36 tall, `--bg2`, a `--fg3` border, radius 4, mono 12), as History's search field is, because
  `ChatTextField` is 48 tall on `--bg` with a `--line` border; "save" is `ChatButton` Primary Small
  at 36 tall; "remove" is mono 11 `--danger` text with no border, which no `ChatButton` variant is.
- Decided (UI): the cards draw a hairline between rows but not under the last one; the prototype's
  last row's `border-bottom` doubles the card's own border there.
- Decided (UI): rows take taps with no ripple, as the prototype's plain buttons and History's rows;
  what a tap does shows in the state (the hint, the edit row, the next screen).
- Decided (UI): the footer reads the installed app's version from the platform through a small
  composable expect/actual in `:feature:settings`, `rememberAppVersion()`: Android's
  `PackageManager` (`versionName`, `longVersionCode`, from `gradle/libs.versions.toml`), iOS's main
  bundle (`CFBundleShortVersionString`, `CFBundleVersion`, which Xcode generates from
  `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION`). It is not Koin-provided, so the ViewModel and
  its module test need no Android `Context`. A part the platform does not report is left out.
- Decided (UI): until the account and keys are read (a few milliseconds), their groups are not drawn,
  rather than flashing "not set" for keys that are stored; a keys read the encrypted store fails
  leaves the group out instead of crashing.
- Decided (UI): Settings wears the root's accent (the default model's), as History does; the green
  key dot is `status.success`, the prototype's positive green.

## Out of scope

- Reading a whole key back. Replies stay canned (rules: no real model provider), so nothing needs
  it; a future provider integration would add an internal read in `:data:settings` that never
  reaches the UI.
- Sending a key anywhere, validating it with its provider, or checking its format.
- Linking and unlinking sign-in methods (the prototype's expanded "connect" / "disconnect" rows,
  364–371) and changing the email, password, name or avatar ("edit ›" is not interactive in the
  prototype either).
- Re-authenticating inside Settings when Firebase answers `RequiresRecentLogin`: the user signs
  out and in again, then deletes.
- "Local only" and "Sign in to sync" (no local-only mode, no sync), "Biometric unlock" and App lock,
  "defaults" (default model, voice, speech rate, read replies aloud), "appearance" (theme) and
  "data" (export): dropped by the user (2026-10-28).
- Revoking Google's own consent on delete: the Firebase user is deleted and signed out; the Google
  account picker may still offer the same Google account next time.
- A "show" toggle on the key input, `FLAG_SECURE` on Settings, and a cancel button in the edit row
  (a tap on the row closes it, as in the prototype).
