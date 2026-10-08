package br.com.weslleycampos.chat.core.navigation.utils

import br.com.weslleycampos.chat.core.navigation.NavOptions
import br.com.weslleycampos.chat.core.navigation.NavOptionsBuilder

fun navOptions(builder: NavOptionsBuilder.() -> Unit): NavOptions = NavOptionsBuilder().apply(builder).build()
