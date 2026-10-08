package br.com.weslleycampos.chat.core.navigation.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.SavedStateConfiguration
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import br.com.weslleycampos.chat.core.navigation.Navigator
import kotlinx.serialization.PolymorphicSerializer
import org.koin.compose.currentKoinScope
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Composable
fun rememberNavigator(startEntry: NavKey): Navigator {
    val configuration = koinInject<SavedStateConfiguration>()

    val koinScope = currentKoinScope()
    val saver = remember(koinScope, configuration) {
        val serializer = NavBackStackSerializer(PolymorphicSerializer(NavKey::class))

        Saver<Navigator, SavedState>(
            save = { navigator ->
                encodeToSavedState(serializer, navigator.navBackStack, configuration)
            },
            restore = { savedState ->
                val backStack = decodeFromSavedState(
                    serializer,
                    savedState,
                    configuration,
                )
                koinScope.get<Navigator> { parametersOf(backStack) }
            },
        )
    }

    return rememberSaveable(saver = saver) {
        koinScope.get<Navigator> {
            parametersOf(NavBackStack(startEntry))
        }
    }
}
