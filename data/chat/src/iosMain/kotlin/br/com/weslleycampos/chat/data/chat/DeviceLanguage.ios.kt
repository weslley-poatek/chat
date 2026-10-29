package br.com.weslleycampos.chat.data.chat

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import platform.Foundation.preferredLanguages

/** The first of the user's preferred languages in iOS Settings, else the current locale's id. */
internal actual fun deviceLanguageTag(): String =
    NSLocale.preferredLanguages.firstOrNull() as? String ?: NSLocale.currentLocale.localeIdentifier
