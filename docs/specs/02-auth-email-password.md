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

<!-- FEATURE AGENT: add the UI criteria of this row here, one `- [ ]` per behaviour, each naming its test. -->

> **To be written by the feature agent.** The UI criteria for the modes (`signin`, `signup`,
> `forgot`), `forgotSent`, `authBusy` (`google`, `form`), `authErr`, `showPw` and the navigation
> after sign-in go here, each with its ViewModel or UI test.

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

## Out of scope

- The prototype's **GitHub** and **Apple** buttons (lines 64–65) and **"Continue without an account"**
  (95–100, `continueLocal` → `finishAuth(true)`, `localOnly`): out of scope by the user's decision of
  2026-10-22.
- The prototype's sync: the sub "An account syncs chat history, memory, personas and settings across
  your devices" describes a server Chat does not have. Nothing is synced; the copy is the feature's
  call.
- The UI of this row (`:feature:auth`): written by the feature agent against the contract above.
- Email verification, changing the email or password, linking a Google and an email account,
  deleting an account and the Settings sign-in methods list: later rows.
- Firebase console setup: enabling Email/Password, the reset email's template and the link's
  lifetime. The prototype's "link expires in 15 min" is copy; the real lifetime is Firebase's.
- Empty input: callers check with `EmailAuthRules` first. If they do not, Android's
  `IllegalArgumentException` for an empty string arrives as `Unknown`.
- Room and `:core:database`: this row stores nothing.
