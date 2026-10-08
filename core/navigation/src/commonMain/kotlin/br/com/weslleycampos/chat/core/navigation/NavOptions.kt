package br.com.weslleycampos.chat.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlin.reflect.KClass

data class NavOptions(val launchSingleTop: Boolean = false, val popUpTo: PopUpTo? = null)

sealed interface PopUpTo {
    val inclusive: Boolean

    data class Key(val key: NavKey, override val inclusive: Boolean = false) : PopUpTo
    data class Type(val type: KClass<out NavKey>, override val inclusive: Boolean = false) : PopUpTo
}

class NavOptionsBuilder {
    var launchSingleTop: Boolean = false
    private var popUpTo: PopUpTo? = null

    fun popUpTo(key: NavKey, builder: PopUpToBuilder.() -> Unit = {}) {
        popUpTo = PopUpTo.Key(key, PopUpToBuilder().apply(builder).inclusive)
    }

    fun popUpTo(type: KClass<out NavKey>, builder: PopUpToBuilder.() -> Unit = {}) {
        popUpTo = PopUpTo.Type(type, PopUpToBuilder().apply(builder).inclusive)
    }

    inline fun <reified T : NavKey> popUpTo(noinline builder: PopUpToBuilder.() -> Unit = {}) =
        popUpTo(T::class, builder)

    internal fun build() = NavOptions(launchSingleTop, popUpTo)
}

class PopUpToBuilder {
    var inclusive: Boolean = false
}
