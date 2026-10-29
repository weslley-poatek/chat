package br.com.weslleycampos.chat.feature.settings

import androidx.compose.runtime.Immutable
import br.com.weslleycampos.chat.data.auth.AuthException
import br.com.weslleycampos.chat.data.auth.AuthUser
import br.com.weslleycampos.chat.data.auth.SignInMethod
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguage
import br.com.weslleycampos.chat.data.chat.models.VoiceLanguages
import br.com.weslleycampos.chat.data.settings.models.Account
import br.com.weslleycampos.chat.data.settings.models.ModelProvider
import br.com.weslleycampos.chat.data.settings.models.ProviderKey
import br.com.weslleycampos.chat.feature.settings.resources.FeatureSettingsRes
import br.com.weslleycampos.chat.feature.settings.resources.settings_delete_confirm
import br.com.weslleycampos.chat.feature.settings.resources.settings_deleting
import br.com.weslleycampos.chat.feature.settings.resources.settings_key_add
import br.com.weslleycampos.chat.feature.settings.resources.settings_key_edit
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_delete_failed
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_delete_network
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_delete_recent_login
import br.com.weslleycampos.chat.feature.settings.resources.settings_toast_delete_too_many_requests
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** The prototype's `deleteArmed` timer: a first tap on "Delete account" arms it for 3000 ms. */
val DeleteArmDuration: Duration = 3000.milliseconds

/**
 * Everything the Settings frame shows, but the key being typed: that draft is
 * [SettingsViewModel.draft], kept out of this state so nothing that prints, compares or saves the
 * state ever carries a key.
 *
 * @property account The "account" group, or `null` until the signed-in account is read.
 * @property deleteArmed "Delete account" was tapped once in the last [DeleteArmDuration]: a second
 *   tap deletes, and the row says "tap again to confirm" (the prototype's `deleteArmed`).
 * @property deleting The account is being deleted: the row says "deleting…", and nothing else in
 *   Settings, back included, answers until it is done.
 * @property keys The "api keys" group's rows, in [ModelProvider] order, or empty until they are read.
 * @property editing The provider whose edit row is open, the prototype's `keyEdit`; one at most.
 * @property languages The "voice" group's languages, those voice mode listens and speaks in, or
 *   `null` until they are read.
 * @property toast The toast's text while it shows.
 */
@Immutable
data class SettingsUiState(
    val account: AccountUi? = null,
    val deleteArmed: Boolean = false,
    val deleting: Boolean = false,
    val keys: List<ProviderKey> = emptyList(),
    val editing: ModelProvider? = null,
    val languages: VoiceLanguages? = null,
    val toast: StringResource? = null,
)

/**
 * The "account" group's rows: the profile row's [initials], [name] and [email], and the
 * "Sign-in methods" row's [signInMethods] summary.
 */
@Immutable
data class AccountUi(
    val initials: String,
    val name: String,
    val email: String?,
    val signInMethods: String,
)

/**
 * One row of the "api keys" group, the prototype's `keyRows` (860–861): a dot filled while
 * [hasKey], the provider's [name], the [masked] key or "not set", and [action] ("edit" / "add",
 * none while [editing]).
 */
@Immutable
data class KeyRowUi(
    val provider: ModelProvider,
    val masked: String?,
    val editing: Boolean,
) {
    val name: String get() = provider.displayName

    val hasKey: Boolean get() = masked != null

    /** The edit row's input placeholder, the provider's: "paste key" for Fuelix. */
    val placeholder: String get() = provider.placeholder

    /** "edit" for a stored key, "add" for none, and nothing while the row is being edited. */
    val action: StringResource?
        get() = when {
            editing -> null
            hasKey -> FeatureSettingsRes.string.settings_key_edit
            else -> FeatureSettingsRes.string.settings_key_add
        }

    /** "remove" shows in the edit row only when there is a key to remove. */
    val removable: Boolean get() = editing && hasKey
}

/** The rows, in the repository's order, with the one being edited marked. */
val SettingsUiState.keyRows: List<KeyRowUi>
    get() = keys.map { KeyRowUi(provider = it.provider, masked = it.masked, editing = it.provider == editing) }

/** "Delete account"'s right-hand hint: "deleting…" while it runs, "tap again to confirm" while armed. */
val SettingsUiState.deleteHint: StringResource?
    get() = when {
        deleting -> FeatureSettingsRes.string.settings_deleting
        deleteArmed -> FeatureSettingsRes.string.settings_delete_confirm
        else -> null
    }

/**
 * The "voice" group's segments: each language alone, "pt-BR" and "en-US", then "both", which keeps
 * the language listened for first as it is.
 */
fun VoiceLanguages.options(): List<VoiceLanguages> =
    VoiceLanguage.entries.map { VoiceLanguages(it) } + copy(bilingual = true)

/** How screen readers announce a segment: "Português (Brasil)", or both names for "both". */
val VoiceLanguages.description: String
    get() = all.joinToString(" + ") { it.displayName }

/** Whether a provider has a key stored, as the rows last read. */
internal fun SettingsUiState.hasKey(provider: ModelProvider): Boolean =
    keys.any { it.provider == provider && it.hasKey }

/** The prototype's separator between connected methods, `join(', ')`. */
private const val MethodSeparator = ", "

/** The summary with no method connected, the prototype's `|| 'none'`. */
private const val NoMethod = "none"

/** What stands in for initials when neither the name nor the email has a letter or digit. */
private const val NoInitials = "?"

/** What splits a name into words for the initials: whitespace, and an email's ".", "_", "+" and "-". */
private val WordSeparators = Regex("[\\s._+\\-]+")

/** How each method reads in the "Sign-in methods" summary: the brand for Google, "email" for the rest. */
val SignInMethod.label: String
    get() = when (this) {
        SignInMethod.GOOGLE -> "Google"
        SignInMethod.EMAIL_PASSWORD -> "email"
    }

/**
 * The "Sign-in methods" summary, the prototype's `methodsSummary`: the connected methods' labels
 * joined by ", " in [SignInMethod] order, "Google, email", or "none".
 */
fun signInMethodsSummary(methods: List<SignInMethod>): String =
    methods.joinToString(MethodSeparator) { it.label }.ifEmpty { NoMethod }

/**
 * The profile row's name: the account's display name, or for an email + password account, which
 * usually has none, the email's local part ("you" for "you@example.dev"), so the full email under
 * it is not repeated. Empty when there is neither.
 */
fun accountName(user: AuthUser): String =
    user.displayName?.trim()?.takeIf { it.isNotEmpty() }
        ?: user.email?.trim()?.substringBefore('@').orEmpty()

/**
 * The avatar's initials, as the prototype's "AR" for "Ana Ribeiro": the first letter or digit of
 * the [name]'s first and last words, upper-cased; one for a one-word name. An email's local part
 * splits on ".", "_", "-" and "+" too, so "ana.ribeiro" is "AR". "?" when there is nothing to take.
 */
fun accountInitials(name: String): String {
    val words = name.split(WordSeparators).mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit) }
    val initials = when (words.size) {
        0 -> return NoInitials
        1 -> words.first().toString()
        else -> "${words.first()}${words.last()}"
    }
    return initials.uppercase()
}

/** The account as its group shows it. */
fun Account.toUi(): AccountUi {
    val name = accountName(user)
    return AccountUi(
        initials = accountInitials(name),
        name = name,
        email = user.email?.trim()?.takeIf { it.isNotEmpty() },
        signInMethods = signInMethodsSummary(signInMethods),
    )
}

/**
 * The toast for a delete that did not happen. Firebase refused, so nothing was deleted or wiped and
 * the user is still signed in; only an expired sign-in has a remedy of its own, signing in again.
 */
internal fun Throwable.deleteFailureMessage(): StringResource = when (this) {
    is AuthException.RequiresRecentLogin -> FeatureSettingsRes.string.settings_toast_delete_recent_login
    is AuthException.NetworkError -> FeatureSettingsRes.string.settings_toast_delete_network
    is AuthException.TooManyRequests -> FeatureSettingsRes.string.settings_toast_delete_too_many_requests
    else -> FeatureSettingsRes.string.settings_toast_delete_failed
}
