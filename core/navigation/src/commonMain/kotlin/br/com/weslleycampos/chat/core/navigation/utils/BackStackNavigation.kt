package br.com.weslleycampos.chat.core.navigation.utils

import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.NavOptions
import br.com.weslleycampos.chat.core.navigation.PopUpTo

internal fun MutableList<NavKey>.navigate(key: NavKey, options: NavOptions) {
    options.popUpTo?.let { popUpTo(it) }
    val top = lastOrNull()
    when {
        top == key -> Unit
        options.launchSingleTop && top != null && top::class == key::class -> this[lastIndex] = key
        else -> add(key)
    }
}

private fun MutableList<NavKey>.popUpTo(target: PopUpTo) {
    val match = when (target) {
        is PopUpTo.Key -> lastIndexOf(target.key)
        is PopUpTo.Type -> indexOfLast { target.type.isInstance(it) }
    }
    if (match < 0) return
    val keep = if (target.inclusive) match else match + 1
    while (size > keep) removeAt(lastIndex)
}
