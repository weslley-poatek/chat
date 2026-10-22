package br.com.weslleycampos.chat.feature.auth.signin

import br.com.weslleycampos.chat.data.auth.EmailAuthRules
import br.com.weslleycampos.chat.feature.auth.resources.FeatureAuthRes
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_cta_forgot
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_cta_sign_in
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_cta_sign_up
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_invalid_email
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_short_password
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_error_wrong_credentials
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_password_hide
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_password_placeholder
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_password_placeholder_new
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_password_show
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_subtitle
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_subtitle_forgot
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_title_forgot
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_title_forgot_sent
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_title_sign_in
import br.com.weslleycampos.chat.feature.auth.resources.sign_in_title_sign_up
import org.jetbrains.compose.resources.StringResource

/** The prototype's `authMode`: one screen, three forms. */
enum class AuthMode {
    SignIn,
    SignUp,
    Forgot,
}

/** The prototype's `authBusy`: what the screen is waiting on, if anything. */
enum class AuthBusy {
    Google,
    Form,
}

/**
 * Everything the Auth frame shows. [error] is the prototype's `authErr`, the one inline message
 * above the call to action; [forgotSent] is the reset-link confirmation of [AuthMode.Forgot].
 */
data class SignInUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val email: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val busy: AuthBusy? = null,
    val forgotSent: Boolean = false,
    val error: StringResource? = null,
)

internal val SignInUiState.title: StringResource
    get() = when (mode) {
        AuthMode.SignIn -> FeatureAuthRes.string.sign_in_title_sign_in
        AuthMode.SignUp -> FeatureAuthRes.string.sign_in_title_sign_up
        AuthMode.Forgot -> if (forgotSent) {
            FeatureAuthRes.string.sign_in_title_forgot_sent
        } else {
            FeatureAuthRes.string.sign_in_title_forgot
        }
    }

/** `null` once the reset link is sent: the prototype leaves the sub empty there. */
internal val SignInUiState.subtitle: StringResource?
    get() = when {
        mode != AuthMode.Forgot -> FeatureAuthRes.string.sign_in_subtitle
        forgotSent -> null
        else -> FeatureAuthRes.string.sign_in_subtitle_forgot
    }

internal val SignInUiState.cta: StringResource
    get() = when (mode) {
        AuthMode.SignIn -> FeatureAuthRes.string.sign_in_cta_sign_in
        AuthMode.SignUp -> FeatureAuthRes.string.sign_in_cta_sign_up
        AuthMode.Forgot -> FeatureAuthRes.string.sign_in_cta_forgot
    }

/** "← sign in" in the bar, and system back returning to sign in instead of leaving the screen. */
internal val SignInUiState.showsBackToSignIn: Boolean get() = mode != AuthMode.SignIn

internal val SignInUiState.showsGoogle: Boolean get() = mode != AuthMode.Forgot

internal val SignInUiState.showsSentConfirmation: Boolean get() = mode == AuthMode.Forgot && forgotSent

internal val SignInUiState.showsForm: Boolean get() = !showsSentConfirmation

internal val SignInUiState.showsPasswordField: Boolean get() = mode != AuthMode.Forgot

internal val SignInUiState.passwordPlaceholder: StringResource
    get() = if (mode == AuthMode.SignUp) {
        FeatureAuthRes.string.sign_in_password_placeholder_new
    } else {
        FeatureAuthRes.string.sign_in_password_placeholder
    }

internal val SignInUiState.passwordToggleLabel: StringResource
    get() = if (showPassword) {
        FeatureAuthRes.string.sign_in_password_hide
    } else {
        FeatureAuthRes.string.sign_in_password_show
    }

/**
 * The prototype's `authSubmit` checks, in its order: the email first in every mode, then, outside
 * [AuthMode.Forgot], the password. A short password on sign in cannot belong to an account Chat
 * created, so it reads as a wrong one. `null` when the form can be sent.
 */
internal fun SignInUiState.validationError(): StringResource? = when {
    !EmailAuthRules.isValidEmail(email) -> FeatureAuthRes.string.sign_in_error_invalid_email
    mode == AuthMode.Forgot || EmailAuthRules.isValidPassword(password) -> null
    mode == AuthMode.SignUp -> FeatureAuthRes.string.sign_in_error_short_password
    else -> FeatureAuthRes.string.sign_in_error_wrong_credentials
}
