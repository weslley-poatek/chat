package br.com.weslleycampos.chat.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import br.com.weslleycampos.chat.core.navigation.utils.navOptions
import br.com.weslleycampos.chat.core.navigation.utils.navigate
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Provided
interface Navigator {
    val navBackStack: NavBackStack<NavKey>
    fun navigate(key: NavKey, builder: NavOptionsBuilder.() -> Unit = {})
    fun navigateUp(): Boolean
}

@Single
class NavigatorImpl(@Provided backStack: NavBackStack<NavKey>) : Navigator {
    override val navBackStack = backStack

    override fun navigate(key: NavKey, builder: NavOptionsBuilder.() -> Unit) {
        navBackStack.navigate(key, navOptions(builder))
    }

    override fun navigateUp(): Boolean {
        if (navBackStack.size <= 1) return false
        navBackStack.removeAt(navBackStack.lastIndex)
        return true
    }
}
