package br.com.weslleycampos.chat.feature.auth.signin

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
import kotlin.test.Test
import kotlin.test.assertEquals

class SignInUiStateTest {
    private val signIn = SignInUiState()
    private val signUp = SignInUiState(mode = AuthMode.SignUp)
    private val forgot = SignInUiState(mode = AuthMode.Forgot)
    private val forgotSent = SignInUiState(mode = AuthMode.Forgot, forgotSent = true)

    @Test
    fun eachModeShowsItsTitleSubtitleAndCallToAction() {
        data class Copy(val title: StringResource, val subtitle: StringResource?, val cta: StringResource)

        val expected = mapOf(
            signIn to Copy(
                title = FeatureAuthRes.string.sign_in_title_sign_in,
                subtitle = FeatureAuthRes.string.sign_in_subtitle,
                cta = FeatureAuthRes.string.sign_in_cta_sign_in,
            ),
            signUp to Copy(
                title = FeatureAuthRes.string.sign_in_title_sign_up,
                subtitle = FeatureAuthRes.string.sign_in_subtitle,
                cta = FeatureAuthRes.string.sign_in_cta_sign_up,
            ),
            forgot to Copy(
                title = FeatureAuthRes.string.sign_in_title_forgot,
                subtitle = FeatureAuthRes.string.sign_in_subtitle_forgot,
                cta = FeatureAuthRes.string.sign_in_cta_forgot,
            ),
            forgotSent to Copy(
                title = FeatureAuthRes.string.sign_in_title_forgot_sent,
                subtitle = null,
                cta = FeatureAuthRes.string.sign_in_cta_forgot,
            ),
        )
        for ((state, copy) in expected) {
            assertEquals(copy, Copy(title = state.title, subtitle = state.subtitle, cta = state.cta), state.toString())
        }
    }

    @Test
    fun eachModeShowsItsPartsOfTheFrame() {
        data class Parts(
            val backToSignIn: Boolean,
            val google: Boolean,
            val form: Boolean,
            val passwordField: Boolean,
            val sentConfirmation: Boolean,
        )

        fun SignInUiState.parts() = Parts(
            backToSignIn = showsBackToSignIn,
            google = showsGoogle,
            form = showsForm,
            passwordField = showsPasswordField,
            sentConfirmation = showsSentConfirmation,
        )

        assertEquals(
            Parts(backToSignIn = false, google = true, form = true, passwordField = true, sentConfirmation = false),
            signIn.parts(),
        )
        assertEquals(
            Parts(backToSignIn = true, google = true, form = true, passwordField = true, sentConfirmation = false),
            signUp.parts(),
        )
        assertEquals(
            Parts(backToSignIn = true, google = false, form = true, passwordField = false, sentConfirmation = false),
            forgot.parts(),
        )
        assertEquals(
            Parts(backToSignIn = true, google = false, form = false, passwordField = false, sentConfirmation = true),
            forgotSent.parts(),
        )
    }

    @Test
    fun signUpAsksForANewPasswordAndTheToggleNamesWhatItDoes() {
        assertEquals(FeatureAuthRes.string.sign_in_password_placeholder, signIn.passwordPlaceholder)
        assertEquals(FeatureAuthRes.string.sign_in_password_placeholder_new, signUp.passwordPlaceholder)
        assertEquals(FeatureAuthRes.string.sign_in_password_show, signIn.passwordToggleLabel)
        assertEquals(FeatureAuthRes.string.sign_in_password_hide, signIn.copy(showPassword = true).passwordToggleLabel)
    }

    @Test
    fun validationChecksTheEmailFirstThenThePasswordOutsideTheReset() {
        val shortPassword = "1234567"
        val expected = listOf(
            signIn.copy(email = "you", password = "") to FeatureAuthRes.string.sign_in_error_invalid_email,
            signUp.copy(email = "you", password = "") to FeatureAuthRes.string.sign_in_error_invalid_email,
            forgot.copy(email = "you") to FeatureAuthRes.string.sign_in_error_invalid_email,
            signIn.copy(email = "you@example.dev", password = shortPassword) to
                FeatureAuthRes.string.sign_in_error_wrong_credentials,
            signUp.copy(email = "you@example.dev", password = shortPassword) to
                FeatureAuthRes.string.sign_in_error_short_password,
            signIn.copy(email = "you@example.dev", password = "12345678") to null,
            signUp.copy(email = " you@example.dev ", password = "       8") to null,
            forgot.copy(email = "you@example.dev", password = "") to null,
        )
        for ((state, error) in expected) {
            assertEquals(error, state.validationError(), state.toString())
        }
    }

    @Test
    fun previewsCoverEveryStateInLightAndDark() {
        val cases = SignInScreenPreviewParameterProvider().values.toList()
        assertEquals(26, cases.size)
        assertEquals(26, cases.map { it.displayName }.distinct().size)
        for (dark in listOf(false, true)) {
            val states = cases.filter { it.darkTheme == dark }.map { it.uiState }
            assertEquals(13, states.distinct().size)
            assertEquals(AuthMode.entries.toSet(), states.map { it.mode }.toSet())
            assertEquals(AuthMode.entries.toSet(), states.filter { it.busy == AuthBusy.Form }.map { it.mode }.toSet())
            assertEquals(AuthMode.entries.toSet(), states.filter { it.error != null }.map { it.mode }.toSet())
            assertEquals(setOf(null, AuthBusy.Google, AuthBusy.Form), states.map { it.busy }.toSet())
            assertEquals(setOf(false, true), states.filter { it.mode == AuthMode.Forgot }.map { it.forgotSent }.toSet())
            assertEquals(setOf(false, true), states.map { it.showPassword }.toSet())
        }
    }
}
