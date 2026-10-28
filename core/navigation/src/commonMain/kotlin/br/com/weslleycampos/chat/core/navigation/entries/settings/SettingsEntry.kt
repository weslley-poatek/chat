package br.com.weslleycampos.chat.core.navigation.entries.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Settings: the signed-in account (sign out, delete account) and the user's own API key for each
 * model provider. The Settings buttons of Voice and History push it over themselves, so its back
 * returns there. Signing out, or deleting the account, replaces the whole stack with `SignInEntry`.
 */
@Serializable
data object SettingsEntry : NavKey
