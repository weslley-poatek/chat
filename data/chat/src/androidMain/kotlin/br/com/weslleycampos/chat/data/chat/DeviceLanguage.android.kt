package br.com.weslleycampos.chat.data.chat

import java.util.Locale

internal actual fun deviceLanguageTag(): String = Locale.getDefault().toLanguageTag()
