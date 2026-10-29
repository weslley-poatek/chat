package br.com.weslleycampos.chat.feature.settings

import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.data.auth.AuthUser
import br.com.weslleycampos.chat.data.auth.SignInMethod
import br.com.weslleycampos.chat.data.settings.models.Account
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import br.com.weslleycampos.chat.data.settings.models.ProviderKey
import br.com.weslleycampos.chat.feature.settings.resources.FeatureSettingsRes
import br.com.weslleycampos.chat.feature.settings.resources.allStringResources
import br.com.weslleycampos.chat.feature.settings.resources.settings_delete_confirm
import br.com.weslleycampos.chat.feature.settings.resources.settings_deleting
import br.com.weslleycampos.chat.feature.settings.resources.settings_key_add
import br.com.weslleycampos.chat.feature.settings.resources.settings_key_edit
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_delete_failed
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_delete_network
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_delete_recent_login
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_delete_too_many_requests
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SettingsUiStateTest {
    private fun user(displayName: String?, email: String?) =
        AuthUser(id = "id", displayName = displayName, email = email, photoUrl = null)

    // The account

    @Test
    fun theProfileShowsTheDisplayNamesInitialsTheNameAndTheEmail() {
        val account = Account(
            user = user(displayName = " Ana Ribeiro ", email = "ana.ribeiro@example.dev"),
            signInMethods = listOf(SignInMethod.GOOGLE),
        )

        assertEquals(
            AccountUi(
                initials = "AR",
                name = "Ana Ribeiro",
                email = "ana.ribeiro@example.dev",
                signInMethods = "Google",
            ),
            account.toUi(),
        )
    }

    @Test
    fun anAccountWithNoDisplayNameIsNamedAndInitialledFromItsEmailsLocalPart() {
        for (displayName in listOf(null, "", "   ")) {
            val account = Account(
                user = user(displayName = displayName, email = "weslley.campos@example.dev"),
                signInMethods = listOf(SignInMethod.EMAIL_PASSWORD),
            )

            assertEquals(
                AccountUi(
                    initials = "WC",
                    name = "weslley.campos",
                    email = "weslley.campos@example.dev",
                    signInMethods = "email",
                ),
                account.toUi(),
            )
        }
        assertEquals("you", accountName(user(displayName = null, email = "you@example.dev")))
        assertEquals("", accountName(user(displayName = null, email = null)))
        assertNull(Account(user(displayName = "Ana", email = " "), emptyList()).toUi().email)
    }

    @Test
    fun theInitialsAreTheFirstAndLastWordsFirstLettersUpperCased() {
        assertEquals("AR", accountInitials("Ana Ribeiro"))
        assertEquals("AR", accountInitials("ana maria  ribeiro"))
        assertEquals("AR", accountInitials("ana_maria-ribeiro"))
        assertEquals("A", accountInitials("Ana"))
        assertEquals("Y", accountInitials("you"))
        assertEquals("J2", accountInitials("j-2"))
        assertEquals("ÉO", accountInitials("élodie o'neil"))
        assertEquals("?", accountInitials(""))
        assertEquals("?", accountInitials("··· —"))
    }

    @Test
    fun theSignInMethodsSummaryJoinsGoogleAndEmailOrSaysNone() {
        assertEquals("Google", signInMethodsSummary(listOf(SignInMethod.GOOGLE)))
        assertEquals("email", signInMethodsSummary(listOf(SignInMethod.EMAIL_PASSWORD)))
        assertEquals("Google, email", signInMethodsSummary(listOf(SignInMethod.GOOGLE, SignInMethod.EMAIL_PASSWORD)))
        assertEquals("none", signInMethodsSummary(emptyList()))
    }

    @Test
    fun deleteIsArmedFor3000MsAndItsHintSaysTapAgainThenDeleting() {
        assertEquals(3000.milliseconds, DeleteArmDuration)
        assertNull(SettingsUiState().deleteHint)
        assertEquals(FeatureSettingsRes.string.settings_delete_confirm, SettingsUiState(deleteArmed = true).deleteHint)
        assertEquals(FeatureSettingsRes.string.settings_deleting, SettingsUiState(deleting = true).deleteHint)
    }

    @Test
    fun eachRefusedDeleteHasItsOwnCopyAndAnythingElseTheGenericOne() {
        assertEquals(
            FeatureSettingsRes.string.settings_toast_delete_recent_login,
            AuthException.RequiresRecentLogin(message = null, cause = null).deleteFailureMessage(),
        )
        assertEquals(
            FeatureSettingsRes.string.settings_toast_delete_network,
            AuthException.NetworkError(message = null, cause = null).deleteFailureMessage(),
        )
        assertEquals(
            FeatureSettingsRes.string.settings_toast_delete_too_many_requests,
            AuthException.TooManyRequests(message = null, cause = null).deleteFailureMessage(),
        )
        for (other in listOf(
            AuthException.NotSignedIn(message = null, cause = null),
            AuthException.UserNotFound(message = null, cause = null),
            AuthException.Unknown(message = null, cause = null),
            IllegalStateException("disk"),
        )) {
            assertEquals(FeatureSettingsRes.string.settings_toast_delete_failed, other.deleteFailureMessage())
        }
    }

    // The keys

    @Test
    fun aKeyRowShowsItsDotItsNameItsMaskedKeyOrNotSetAndEditOrAdd() {
        val rows = listOf("sk-test…4f2a", null).map { masked ->
            SettingsUiState(
                keys = listOf(ProviderKey(provider = ModelProvider.FUELIX, masked = masked))
            ).keyRows.single()
        }

        assertEquals(listOf(ModelProvider.FUELIX, ModelProvider.FUELIX), rows.map { it.provider })
        assertEquals(listOf("Fuelix", "Fuelix"), rows.map { it.name })
        assertEquals(listOf(true, false), rows.map { it.hasKey })
        assertEquals(listOf("sk-test…4f2a", null), rows.map { it.masked })
        assertEquals(
            listOf(FeatureSettingsRes.string.settings_key_edit, FeatureSettingsRes.string.settings_key_add),
            rows.map { it.action },
        )
        assertEquals(listOf("paste key", "paste key"), rows.map { it.placeholder })
        assertTrue(rows.none { it.editing || it.removable })
    }

    @Test
    fun theRowBeingEditedHasNoActionAndOffersRemoveOnlyForAStoredKey() {
        val rows = listOf("sk-test…4f2a", null).map { masked ->
            val keys = listOf(ProviderKey(provider = ModelProvider.FUELIX, masked = masked))
            SettingsUiState(keys = keys, editing = ModelProvider.FUELIX).keyRows.single()
        }

        assertEquals(listOf(true, true), rows.map { it.editing })
        assertEquals(listOf(null, null), rows.map { it.action })
        assertEquals(listOf(true, false), rows.map { it.removable })
    }

    // The footer

    @Test
    fun theFooterIsChatItsVersionNameAndCodeAndThePlatform() {
        assertEquals(
            "Chat 1.0 (1) · android",
            settingsFooter(AppVersion(name = "1.0", code = "1", platform = "android")),
        )
        assertEquals(
            "Chat 0.9.2 (418) · ios",
            settingsFooter(AppVersion(name = "0.9.2", code = "418", platform = "ios")),
        )
        assertEquals("Chat 1.0 · ios", settingsFooter(AppVersion(name = "1.0", code = "", platform = "ios")))
        assertEquals("Chat · android", settingsFooter(AppVersion(name = " ", code = "", platform = "android")))
    }

    // The frame

    @Test
    fun settingsHasOnlyTheAccountAndApiKeysGroups() {
        val labels = FeatureSettingsRes.allStringResources.keys

        assertTrue("settings_account" in labels)
        assertTrue("settings_api_keys" in labels)
        // Biometric unlock, defaults, appearance, data and linking methods are not built: the module
        // has no label any of their controls could be shown or announced by.
        val dropped = listOf("biometric", "default", "voice", "speech", "read", "theme", "export", "connect", "local")
        for (word in dropped) {
            assertEquals(emptyList(), labels.filter { word in it }, word)
        }
    }

    @Test
    fun previewsCoverEveryStateInLightAndDark() {
        val cases = SettingsScreenPreviewParameterProvider().values.toList()
        val names = listOf(
            "Signed in",
            "Email account",
            "Delete armed",
            "Deleting",
            "Delete refused",
            "No key",
            "Editing a key",
            "Adding a key",
            "Key stored",
        )

        assertEquals(names + names, cases.map { it.name })
        assertEquals(List(names.size) { false } + List(names.size) { true }, cases.map { it.darkTheme })
        // A preview's draft is an obvious fake, never a real key.
        assertTrue(cases.all { it.draft.isEmpty() || it.draft == FakeKey })
    }
}
