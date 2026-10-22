# Auth: email + password

## Goal

Let people sign in to Chat with an email and a password, create an account, and get a
password-reset email, next to the Google sign-in that already works. Everything runs on Firebase
Auth, with the Android SDK on Android and the iOS SDK on iOS.

## Frames

| Frame | States | Design source |
| --- | --- | --- |
| `Auth` | `authMode:'signin'` | `ctx Prototype.dc.html`, lines 45–102: bar 48–51, title and sub 53–54, OAuth 61–68, form 70–81, links 83–86, local card 95–100 |
| `Auth` | `authMode:'signup'` | lines 50 ("← sign in"), 61–81, links 87–90; placeholder and hint in bindings 876–886 |
| `Auth` | `authMode:'forgot'`, `forgotSent:false` | lines 50, 53–54, form 70–81 without the password (73–78) |
| `Auth` | `authMode:'forgot'`, `forgotSent:true` | lines 50, 53–54, sent confirmation 56–59 |
| `Auth` | `authBusy:'google'`, `authBusy:'form'` | OAuth hint 63, CTA spinner 80; script `oauth` 667, `authSubmit` 668–674 |
| `Auth` | `authErr` set | line 79; messages in `authSubmit` 670–673 |
| `Auth` | `showPw` true / false | lines 75–76; bindings 882 |

Script: `finishAuth` 666, `oauth` 667, `authSubmit` 668–674, bindings 876–886. The inventory is
`.claude/screens/frames.md`, section 2.1, with the state and timer tables in section 1.3.

In code every state is a field of `SignInUiState` (`feature/auth/…/signin/SignInUiState.kt`), shown
by `SignInScreen` and owned by `SignInViewModel`:

| Prototype | `SignInUiState` | Shown as |
| --- | --- | --- |
| `authMode` | `mode: AuthMode` (`SignIn`, `SignUp`, `Forgot`) | title, sub, CTA, which parts show (`title`, `subtitle`, `cta`, `shows…`) |
| `email`, `pw` | `email`, `password` | the two `ChatTextField`s |
| `showPw` | `showPassword` | `PasswordVisualTransformation` on or off, toggle "show" / "hide" |
| `authBusy` | `busy: AuthBusy?` (`Google`, `Form`) | "authorizing…" in the Google button; the CTA's `ChatButton` spinner |
| `forgotSent` | `forgotSent` | "Check your inbox", the "sent to" box and "Back to sign in" |
| `authErr` | `error: StringResource?` | the mono 12 `status.danger` line above the CTA |

Top to bottom, with `ChatTheme` tokens: safe-area padding, then `xxxLarge` (22) sides and `xLarge`
(14) bottom; bar `xxLarge` (44) tall after `xSmall` (6), wordmark `displaySmall` + accent caret, "←
sign in" `labelMedium` `text.secondary`; spacer weight 1; title `displayLarge`; sub `bodyMedium`
`text.secondary` after `small` (8); sent box after `xxxLarge`, `xLarge` padding, 1 dp
`border.primary`, `shapes.control`, `labelMedium` at 1.6 line height, then a Primary `ChatButton`
after `xLarge`; Google as a Secondary `ChatButton` after `xxxLarge`; the "or" rule 18 / 14 around a
`labelSmall` `text.tertiary` "or"; the form's `ChatTextField`s and Primary `ChatButton` `small` (8)
apart, the CTA `xxSmall` (4) lower; the links row `bodySmall`; spacer weight 1.2.

## Acceptance criteria

Each criterion is one observable behaviour with at least one test that checks it.

### Data (`:data:auth`)

- [x] Given Firebase's invalid-email failure (`ERROR_INVALID_EMAIL` / `FIRAuthErrorCodeInvalidEmail`),
  when it is mapped, then it is `AuthException.InvalidEmail` —
  `FirebaseAuthExceptionMappingTest.mapsEmailPasswordFailuresToTheirOwnAuthExceptions`,
  `NSErrorAuthExceptionMappingTest.mapsEmailPasswordErrorCodesToTheirOwnAuthExceptions`
- [x] Given a wrong password (`ERROR_WRONG_PASSWORD` / `FIRAuthErrorCodeWrongPassword`) or a rejected
  credential (`ERROR_INVALID_CREDENTIAL` / `FIRAuthErrorCodeInvalidCredential`), when it is mapped,
  then it is `AuthException.InvalidCredentials` — the two tests above and
  `FirebaseAuthExceptionMappingTest.mapsFirebaseFailuresToAuthExceptions`,
  `NSErrorAuthExceptionMappingTest.mapsFirebaseAuthErrorCodesToAuthExceptions`
- [x] Given no account for the email (`ERROR_USER_NOT_FOUND` / `FIRAuthErrorCodeUserNotFound`), when it is
  mapped, then it is `AuthException.UserNotFound` — the two email-password mapping tests
- [x] Given an email that already has an account (`ERROR_EMAIL_ALREADY_IN_USE` /
  `FIRAuthErrorCodeEmailAlreadyInUse`), when it is mapped, then it is `AuthException.EmailAlreadyInUse`
  — the two email-password mapping tests
- [x] Given a password Firebase rejects (`FirebaseAuthWeakPasswordException` / `FIRAuthErrorCodeWeakPassword`),
  when it is mapped, then it is `AuthException.WeakPassword` — the two email-password mapping tests
- [x] Given too many attempts (`FirebaseTooManyRequestsException` / `FIRAuthErrorCodeTooManyRequests`), when
  it is mapped, then it is `AuthException.TooManyRequests` — the two email-password mapping tests
- [x] Given the existing failures, when they are mapped, then a network failure is still
  `NetworkError`, and a disabled user, an account that exists with a different credential, a
  disabled sign-in provider (`ERROR_OPERATION_NOT_ALLOWED` / `FIRAuthErrorCodeOperationNotAllowed`)
  and anything else are `Unknown` — `FirebaseAuthExceptionMappingTest.mapsFirebaseFailuresToAuthExceptions`,
  `NSErrorAuthExceptionMappingTest.mapsFirebaseAuthErrorCodesToAuthExceptions`
- [x] Given a Firebase exception on Android, when it is mapped, then it is the `AuthException`'s
  cause — `FirebaseAuthExceptionMappingTest.keepsTheFirebaseExceptionAsTheCause`
- [x] Given a Firebase task on Android, when it succeeds, then its result is returned, and when it
  fails or fails to start, then the failure is thrown as an `AuthException` —
  `AwaitAuthTaskTest.returnsTheResultOfASuccessfulTask`,
  `AwaitAuthTaskTest.rethrowsAFailedTaskAsAnAuthException`,
  `AwaitAuthTaskTest.rethrowsAFailureWhileStartingAsAnAuthException`
- [x] Given a `CancellationException` on Android, including a cancelled task, when the call is
  awaited, then the same exception is rethrown, not wrapped — `AwaitAuthTaskTest.letsCancellationThroughUntouched`
- [x] Given a Firebase completion block on iOS, when it reports no error, then the call returns, and
  when it reports an error, then the mapped `AuthException` is thrown —
  `AwaitAuthCompletionTest.returnsWhenTheCompletionReportsNoError`,
  `AwaitAuthCompletionTest.rethrowsTheCompletionErrorAsAnAuthException`
- [x] Given a call waiting on iOS, when its caller is cancelled, then the wait ends cancelled and a
  late completion is ignored — `AwaitAuthCompletionTest.cancellingTheCallerStopsTheWaitAndIgnoresALateCompletion`
- [x] Given an email, when it is checked, then it is valid only if, trimmed, it matches the
  prototype's `/.+@.+\..+/` — `EmailAuthRulesTest.acceptsAnEmailWithSomethingAroundTheAtAndADotAfterIt`,
  `EmailAuthRulesTest.rejectsAnythingElseAsAnEmail`
- [x] Given a password, when it is checked, then it is valid only with 8 or more characters, spaces
  included — `EmailAuthRulesTest.acceptsAPasswordOfEightOrMoreCharacters`
- [x] Given `DataAuthModule`, when Koin is asked for `EmailAuthManager` twice, then it returns one
  instance, and `AuthManager` still resolves, on the Android host and the iOS simulator —
  `DataAuthModuleTest.koinProvidesOneEmailAuthManagerNextToTheAuthManager`

The calls into Firebase themselves (`signInWithEmailAndPassword`, `createUserWithEmailAndPassword`,
`sendPasswordResetEmail` and their `FIRAuth` equivalents) need a configured Firebase app and a
network, which the host and simulator tests do not have. `:androidApp:assembleDebug` and
`:app:linkDebugFrameworkIosSimulatorArm64` compile them. On a device they also need the
**Email/Password** provider enabled under **Authentication → Sign-in method** in the Firebase
console.

### UI (`:feature:auth`)

`SignInViewModelTest` runs against `FakeAuthManager` and `FakeEmailAuthManager`; `SignInUiStateTest`
checks the pure state. Both are in `commonTest`, so they run on the Android host and the iOS
simulator.

- [x] Given the sign-in screen opens, then it is in `signin` with an empty form, nothing busy and no
  error — `SignInViewModelTest.startsOnSignInWithAnEmptyForm`
- [x] Given each mode, when it is shown, then its title, sub and CTA are: `signin` "Sign in" / account
  sub / "Sign in"; `signup` "Create account" / account sub / "Create account"; `forgot` "Reset
  password" / reset sub / "Send reset link"; `forgot` sent "Check your inbox" / no sub —
  `SignInUiStateTest.eachModeShowsItsTitleSubtitleAndCallToAction`
- [x] Given each mode, when it is shown, then `signin` has Google, "or", email + password and the
  links "Forgot password?" / "Create account →" and no "← sign in"; `signup` adds "← sign in" and has
  the hint + "Sign in →"; `forgot` has "← sign in" and the email alone, without Google; `forgot` sent
  has "← sign in", the "sent to" box and "Back to sign in", without the form —
  `SignInUiStateTest.eachModeShowsItsPartsOfTheFrame`
- [x] Given `signup`, then the password placeholder is "password · 8+ chars" (else "password"), and
  the toggle reads "show" while hidden and "hide" while shown —
  `SignInUiStateTest.signUpAsksForANewPasswordAndTheToggleNamesWhatItDoes`
- [x] Given typed values, when a mode link, "← sign in", "Back to sign in" or system back switches the
  mode, then the email, password and `showPw` are kept and the error and `forgotSent` are cleared —
  `SignInViewModelTest.theModeLinksSwitchModesAndKeepWhatWasTyped`,
  `SignInViewModelTest.changingModeClearsTheError`,
  `SignInViewModelTest.backToSignInFromTheSentConfirmationKeepsTheEmailAndAFreshResetStartsOver`
- [x] Given the password, when "show" / "hide" is tapped, then it is shown / hidden —
  `SignInViewModelTest.thePasswordToggleShowsAndHidesThePassword`
- [x] Given an email that fails `EmailAuthRules.isValidEmail`, when the CTA is tapped in any mode, then
  "enter a valid email" shows and Firebase is not called —
  `SignInViewModelTest.anInvalidEmailShowsItsErrorInEveryModeWithoutCallingFirebase`,
  `SignInUiStateTest.validationChecksTheEmailFirstThenThePasswordOutsideTheReset`
- [x] Given `signup` and a password under 8 characters, when the CTA is tapped, then "password needs
  8+ characters" shows and Firebase is not called —
  `SignInViewModelTest.aShortPasswordOnSignUpAsksForEightCharactersWithoutCallingFirebase`
- [x] Given `signin` and a password under 8 characters, when the CTA is tapped, then "wrong email or
  password" shows and Firebase is not called —
  `SignInViewModelTest.aShortPasswordOnSignInReadsAsAWrongPasswordWithoutCallingFirebase`
- [x] Given an error, when the email or the password is edited, then the error clears —
  `SignInViewModelTest.typingInEitherFieldClearsTheError`
- [x] Given a valid `signin`, when the CTA is tapped, then `EmailAuthManager.signIn` gets the email and
  password as typed, and on return Home replaces SignIn on the back stack, as after Google —
  `SignInViewModelTest.aValidSignInSignsInWithWhatWasTypedAndReplacesSignInWithHome`
- [x] Given a valid `signup`, when the CTA is tapped, then `createAccount` is called and on return Home
  replaces SignIn — `SignInViewModelTest.aValidSignUpCreatesTheAccountAndReplacesSignInWithHome`
- [x] Given a valid email in `forgot`, when "Send reset link" is tapped, then `sendPasswordReset` gets
  it, no password is needed, and on return `forgotSent` shows while SignIn stays —
  `SignInViewModelTest.aValidResetSendsTheLinkWithoutAPasswordAndShowsTheSentConfirmation`
- [x] Given a call in flight, then `authBusy` is `form` (CTA spinner) until Firebase answers, and a
  second submit, the Google button, a Google token and the mode links are ignored while typing and
  show/hide still work — `SignInViewModelTest.theFormIsBusyUntilFirebaseAnswersAndIgnoresAllButTypingMeanwhile`,
  `SignInViewModelTest.aResetIsBusyUntilFirebaseAnswers`
- [x] Given a call that ends cancelled, then the form is no longer busy —
  `SignInViewModelTest.aCancelledCallLeavesTheFormUsable`
- [x] Given each `AuthException` a call can throw, when it is thrown, then its message (table in
  "Decided") shows, `authBusy` clears, the typed values stay and SignIn stays; anything that is not
  an `AuthException` shows "something went wrong, try again" —
  `SignInViewModelTest.eachFirebaseFailureShowsItsMessageAndStaysOnTheForm`
- [x] Given a failed sign-in, when the CTA is tapped again, then Firebase is called again —
  `SignInViewModelTest.aFailedSignInCanBeTriedAgain`
- [x] Given nothing busy, when "Continue with Google" is tapped, then the account picker opens; while
  anything is busy it does not, and a second token is ignored —
  `SignInViewModelTest.theGoogleButtonOpensThePickerWhileNothingIsBusy`,
  `SignInViewModelTest.aTokenThatArrivesWhileSigningInIsIgnored`
- [x] Given a Google token, then `authBusy` is `google` ("authorizing…") and the earlier error is gone
  until Firebase answers, and on success Home replaces SignIn —
  `SignInViewModelTest.aGoogleTokenShowsAuthorizingUntilFirebaseAnswersThenReplacesSignInWithHome`,
  `SignInViewModelTest.aGoogleTokenClearsTheEarlierError`
- [x] Given a Google failure, then a network failure shows "no connection, check it and try again", a
  rejected credential "Google couldn’t confirm your account, try again" and anything else, the
  picker's own failures included, "couldn’t sign in with Google, try again", keeping what was typed;
  a cancellation is rethrown, not shown —
  `SignInViewModelTest.aGoogleTokenThatFailsShowsItsMessageAndKeepsWhatWasTyped`,
  `SignInViewModelTest.aPickerFailureShowsTheGenericGoogleMessage`,
  `SignInViewModelTest.aCancelledPickerIsRethrownNotShown`
- [x] Given the previews, then every mode, `forgotSent` on and off, both `authBusy` values, an error in
  every mode and `showPw` are drawn, in light and in dark —
  `SignInUiStateTest.previewsCoverEveryStateInLightAndDark`

Layout, insets, scrolling, the keyboard and the caret blink are drawn by Compose, and
`runComposeUiTest` does not run on the Android host here (no Robolectric), so they are covered by the
previews and checked on a device: Android and iOS, light and dark, with the keyboard open.

## Data contract

- **Module and package:** `:data:auth`, `br.com.weslleycampos.chat.data.auth`
- **Interface:**

  ```kotlin
  @Provided
  interface EmailAuthManager {
      suspend fun signIn(email: String, password: String)
      suspend fun createAccount(email: String, password: String)
      suspend fun sendPasswordReset(email: String)
  }
  ```

  `AuthManager` is unchanged: `currentUser: Flow<AuthUser?>`, `signInWithGoogle(idToken, accessToken)`,
  `signOut()`.

- **Validation:**

  ```kotlin
  object EmailAuthRules {
      const val MIN_PASSWORD_LENGTH = 8
      fun isValidEmail(email: String): Boolean     // trimmed email matches /.+@.+\..+/
      fun isValidPassword(password: String): Boolean // password.length >= 8, not trimmed
  }
  ```

- **Errors:** every call throws an `AuthException` when Firebase refuses it, and rethrows
  `CancellationException` untouched.

  | `AuthException` | Android | iOS (`FIRAuthErrorCode…`) |
  | --- | --- | --- |
  | `InvalidEmail` | `ERROR_INVALID_EMAIL` | `InvalidEmail` (17008) |
  | `InvalidCredentials` | `ERROR_WRONG_PASSWORD`, `ERROR_INVALID_CREDENTIAL`, any other `FirebaseAuthInvalidCredentialsException` | `WrongPassword` (17009), `InvalidCredential` (17004) |
  | `UserNotFound` | `ERROR_USER_NOT_FOUND` | `UserNotFound` (17011) |
  | `EmailAlreadyInUse` | `ERROR_EMAIL_ALREADY_IN_USE` | `EmailAlreadyInUse` (17007) |
  | `WeakPassword` | `FirebaseAuthWeakPasswordException`, `ERROR_WEAK_PASSWORD` | `WeakPassword` (17026) |
  | `TooManyRequests` | `FirebaseTooManyRequestsException` | `TooManyRequests` (17010) |
  | `NetworkError` | `FirebaseNetworkException` | `NetworkError` (17020) |
  | `Unknown` | everything else, e.g. `ERROR_USER_DISABLED`, `ERROR_OPERATION_NOT_ALLOWED` | everything else |

  Which call can throw which: `signIn` → `InvalidEmail`, `InvalidCredentials`, `UserNotFound`,
  `TooManyRequests`, `NetworkError`, `Unknown`. `createAccount` → `InvalidEmail`,
  `EmailAlreadyInUse`, `WeakPassword`, `TooManyRequests`, `NetworkError`, `Unknown`.
  `sendPasswordReset` → `InvalidEmail`, `UserNotFound`, `TooManyRequests`, `NetworkError`, `Unknown`.

- **Koin binding:** `AndroidFirebaseEmailAuthManager` (androidMain) and `IosFirebaseEmailAuthManager`
  (iosMain), both `@Single internal`, bound as `EmailAuthManager` by `DataAuthModule`'s
  `@ComponentScan`, which `AppModule` already includes. Inject `EmailAuthManager` next to
  `AuthManager` in the ViewModel.
- **Behaviour:**
  - A `signIn` or `createAccount` that returns has signed the user in: `AuthManager.currentUser`
    emits the `AuthUser` (`email` set, `displayName` and `photoUrl` usually `null`), exactly as after
    Google sign-in. Navigate after the call returns, as `SignInViewModel` does for Google.
  - `sendPasswordReset` signs nobody in; when it returns, show `forgotSent`.
  - The email is trimmed before it is sent; the password is sent as typed.
  - Firebase's email enumeration protection, on by default for new projects, changes two answers:
    a sign-in to an email with no account throws `InvalidCredentials`, not `UserNotFound`, and
    `sendPasswordReset` returns for such an email without sending anything. `createAccount` still
    throws `EmailAlreadyInUse`. Treat `InvalidCredentials` and `UserNotFound` alike on sign in.
  - The prototype's copy covers three failures: "enter a valid email" (`isValidEmail` false or
    `InvalidEmail`), "password needs 8+ characters" (sign up, `isValidPassword` false) and "wrong
    email or password" (sign in). `EmailAlreadyInUse`, `WeakPassword`, `TooManyRequests` and
    `NetworkError` have no copy in the prototype.

## Decided

- Decided: a separate `EmailAuthManager` interface, not new methods on `AuthManager`. The Android
  `AuthManager` implementation, `AndroidFirebaseAuthManager.kt`, carries the user's uncommitted
  changes and must not change, and existing `AuthManager` fakes keep compiling.
- Decided: the names `signIn`, `createAccount` and `sendPasswordReset`. The interface name already
  says email, so `emailAuthManager.signIn(email, password)` reads without repeating it, and the
  verbs match the prototype's CTAs ("Sign in", "Create account", "Send reset link").
- Decided: the checks live in `:data:auth` as `EmailAuthRules`, not in the feature. They state what
  an account accepts, need no Compose, are tested in `commonTest`, and any later screen that takes
  an email or a password (Settings, for example) reuses them. The copy for each failure stays in
  `:feature:auth`.
- Decided: the email check is the prototype's regex on the trimmed email, and both implementations
  trim the email before sending it, because mobile keyboards add a space after an autocompleted
  word. Passwords are never trimmed, so spaces count toward the 8 characters.
- Decided: 8 characters, counted as `String.length` (UTF-16 units, like the prototype's JS
  `length`). Firebase's default policy accepts 6, so this is the app's own, stricter rule; a stricter
  policy set in the Firebase console arrives as `WeakPassword`.
- Decided: on Android, mapping goes by Firebase error code, because one class,
  `FirebaseAuthInvalidCredentialsException`, covers both a malformed email and a wrong password. Any
  other code of that class still maps to `InvalidCredentials`, so Google sign-in maps as before.
- Decided: `TooManyRequests` is now its own case on both platforms; it was `Unknown`. The Google
  sign-in screen still shows its generic error for it, through its `else` branch.
- Decided: a disabled user, an account that exists with a different credential and a disabled
  provider stay `Unknown`: the prototype has no copy for them.
- Decided: the waiting and mapping live in `utils/FirebaseAuthCalls.kt` per platform
  (`awaitAuthTask` on Android, `awaitAuthCompletion` on iOS), so they are tested without Firebase.
  `AndroidFirebaseAuthManager` and `IosFirebaseAuthManager` keep their own code.
- Decided: `kotlinx-coroutines-test` joins `:data:auth`'s `commonTest`, as in `:feature:auth`, for
  `runTest`.
- Decided: the iOS `AuthException` keeps a `null` cause, as before: an `NSError` is not a `Throwable`.

### UI (`:feature:auth`)

- Decided: the three modes are one screen on the existing `SignInEntry`, with `mode` in
  `SignInUiState`, and no new navigation keys. The prototype switches modes in place (frames.md, 4:
  "same screen"), keeps the email and password across them and has no transitions between them.
  System back in `signup` and `forgot` returns to `signin`, like "← sign in", through a
  `NavigationBackHandler` in `SignInEntryProvider`; in `signin` it does what it did before. The
  prototype has no system back. A later row that opens Auth straight in `signup` (Settings' delete
  account) can give `SignInEntry` a mode argument then.
- Decided: the wordmark shows "Chat" (`sign_in_app_name`), not "ctx", in the logo style
  (`displaySmall`: mono 18, 600, −0.02em), followed by the `▍` caret in `accent.primary` blinking
  every 1.1 s with a step easing (`blink 1.1s steps(1)`). It replaces the `ChatVoiceSphere` the old
  screen used as its logo. Screen readers read "Chat"; the caret is hidden from them, and the mode
  title is the heading.
- Decided: the Google badge holds the existing four-colour Google logo (`ic_google`, 20 dp in the
  prototype's 22 dp badge box, without the `--bg3` fill) instead of a mono "G": Google's sign-in
  branding asks for the logo, and the screen already used it. The gap to the label stays 12.
- Decided: "authorizing…" (`authBusy:'google'`) shows from when the picker hands over a token until
  Firebase answers, not from the tap. A dismissed picker calls neither callback, so a busy state set
  on tap would never clear; while the picker is open it covers the screen anyway.
- Decided: while anything is busy, the CTA (its `ChatButton` is `loading`, so not clickable), a
  second submit, the Google button and every mode switch, system back included, are ignored, so the
  answer always lands in the mode that asked. Typing and show/hide stay live, as in the prototype.
  The Google button is not faded while the form is busy; its taps just do nothing.
- Decided: one inline error line, above the CTA as in the prototype, for every failure, Google's
  included; mono 12 (`labelMedium`) `status.danger`, a polite live region. It clears on typing in
  either field, on any mode switch, on a new submit and when a Google token arrives.
- Decided: the checks run in the prototype's order: the email in every mode, then, except in
  `forgot`, the password. A sign-in password under 8 characters shows "wrong email or password"
  without calling Firebase, as the prototype does, since Chat creates no account with one.
- Decided: copy for every failure, in the prototype's lowercase, unpunctuated error style:

  | Failure | Copy |
  | --- | --- |
  | `isValidEmail` false, `InvalidEmail` | enter a valid email |
  | `signup`, `isValidPassword` false | password needs 8+ characters |
  | `signin`: password under 8, `InvalidCredentials`, `UserNotFound` | wrong email or password |
  | `forgot`: `UserNotFound` (only with enumeration protection off) | no account uses this email |
  | `EmailAlreadyInUse` | this email already has an account |
  | `WeakPassword` | password is too weak, try a longer one |
  | `TooManyRequests` | too many attempts, try again later |
  | `NetworkError` (email or Google) | no connection, check it and try again |
  | `Unknown`, anything else | something went wrong, try again |
  | Google: `InvalidCredentials` | Google couldn’t confirm your account, try again |
  | Google: anything else, the picker's own failures included | couldn’t sign in with Google, try again |

  The Google messages keep their meaning from the old screen; their keys moved to `sign_in_error_*`
  and their copy to the same style. Google still maps `TooManyRequests` to its generic message.
- Decided: the sub of `signin` and `signup` replaces the sync copy, since nothing syncs: "An account
  keeps you signed in. Chat history, memory, personas and settings stay on this device."
- Decided: the `forgot` sub replaces the API-key copy, since there are no API keys: "We’ll email a
  one-time link to choose a new password. Your chats on this device stay as they are."
- Decided: the sign-up hint replaces "we never see your API keys" with a rule the form really has:
  "8+ chars · spaces count" (passwords are not trimmed).
- Decided: the "sent to" box shows the trimmed email the link went to (the prototype's
  "you@example.dev" fallback cannot happen: an empty email never passes the check). Its second line,
  "link expires in 15 min" in the prototype, becomes "not there? check spam or the address": the
  link's lifetime is Firebase's to set, and with enumeration protection on, an email with no account
  gets nothing.
- Decided: keyboard: the email field uses the email keyboard with no autocorrect or capitalization
  and `ImeAction.Next` to the password (`Done` in `forgot`, where it is the last field); the password
  uses the password keyboard, also while shown, and `ImeAction.Done`. `Done` submits, like the CTA,
  and leaves the keyboard up, as the prototype's CTA keeps focus in the field (`preventBlur`); it
  goes away when Home replaces the screen. Autofill hints: email + username on the email field,
  `Password` on sign in and `NewPassword` on sign up.
- Decided: insets and scrolling: the screen pads by `WindowInsets.safeDrawing` (status bar,
  navigation bar, cutout and keyboard) for the prototype's `--st` / `--sb`, then 22 at the sides and
  14 at the bottom. The column is at least the safe area's height, so the 1 : 1.2 spacers centre the
  content as in the prototype, and it scrolls once the keyboard makes it shorter. Android needs
  `android:windowSoftInputMode="adjustResize"` on `MainActivity` to report the keyboard as an inset
  instead of panning the window, so the manifest gains it. iOS keeps Compose's default
  `OnFocusBehavior`, which only offsets the view when a focused field would still be under the
  keyboard.
- Decided: the text links ("← sign in", "Forgot password?", "Create account →", "Sign in →") are at
  least 44 tall for touch, with the links row 8 below the CTA instead of 14 so the text sits where the
  prototype puts it, and they dim to 60 % while pressed: the prototype has no pressed state. The
  show/hide toggle is the prototype's 36 tall button in the field's trailing slot and reads "Show
  password" / "Hide password" to screen readers.
- Decided: the pieces `:core:ui` has no component for (wordmark, text link, show/hide toggle, "or"
  rule) stay private to `:feature:auth` in `SignInParts.kt`: no other frame uses them yet.
- Decided: no `runComposeUiTest`: the Android host tests have no Robolectric, so it would only run on
  the iOS simulator. The logic is tested through the ViewModel and pure state functions instead, and
  the previews cover every state in light and dark (`widthDp = 402`, `heightDp = 874`, the
  prototype's frame).
- Decided: `SignInUiState` lives in the ViewModel and survives configuration changes, not process
  death: after one, the screen starts over in `signin`, and the password is never written to saved
  state.
- Decided: success navigates exactly as Google does: `navigate(HomeEntry) { popUpTo<SignInEntry>
  { inclusive = true } }`. The prototype's `finishAuth` lands on an empty Chat; a later row replaces
  `HomeEntry`.

## Out of scope

- The prototype's **GitHub** and **Apple** buttons (lines 64–65) and **"Continue without an account"**
  (95–100, `continueLocal` → `finishAuth(true)`, `localOnly`): out of scope by the user's decision of
  2026-10-22.
- The prototype's sync: the sub "An account syncs chat history, memory, personas and settings across
  your devices" describes a server Chat does not have. Nothing is synced; the replacement copy is in
  "Decided".
- Sign out, delete account and the Chat banner's "sign in" link, which open Auth from elsewhere:
  later rows (Settings, Chat).
- Email verification, changing the email or password, linking a Google and an email account,
  deleting an account and the Settings sign-in methods list: later rows.
- Firebase console setup: enabling Email/Password, the reset email's template and the link's
  lifetime. The prototype's "link expires in 15 min" is copy; the real lifetime is Firebase's.
- Empty input: callers check with `EmailAuthRules` first. If they do not, Android's
  `IllegalArgumentException` for an empty string arrives as `Unknown`.
- Room and `:core:database`: this row stores nothing.
