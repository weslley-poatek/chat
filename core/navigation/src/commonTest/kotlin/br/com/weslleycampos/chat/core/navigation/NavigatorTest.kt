package br.com.weslleycampos.chat.core.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private data object StartKey : NavKey
private data object FormKey : NavKey
private data object ConfirmKey : NavKey
private data object TargetKey : NavKey
private data class PageKey(val page: Int) : NavKey

private val FirstPage = PageKey(1)
private val SecondPage = PageKey(2)

private fun navigator(vararg keys: NavKey) = NavigatorImpl(NavBackStack<NavKey>().apply { addAll(keys) })

private fun NavigatorImpl.stack() = navBackStack.toList()

class NavigatorTest {

    @Test
    fun navigatePushesEveryKeyButOneEqualToTheTop() {
        val navigator = navigator(StartKey)

        navigator.navigate(FormKey)
        navigator.navigate(FormKey)
        navigator.navigate(FirstPage)
        navigator.navigate(SecondPage)

        assertEquals(listOf(StartKey, FormKey, FirstPage, SecondPage), navigator.stack())
    }

    @Test
    fun launchSingleTopReplacesATopOfTheSameTypeOnly() {
        val navigator = navigator(StartKey, FirstPage)

        navigator.navigate(SecondPage) { launchSingleTop = true }
        assertEquals(listOf(StartKey, SecondPage), navigator.stack())

        navigator.navigate(FormKey) { launchSingleTop = true }
        assertEquals(listOf(StartKey, SecondPage, FormKey), navigator.stack())
    }

    @Test
    fun popUpToRemovesEverythingAboveTheTopmostMatch() {
        val stack = arrayOf(StartKey, FirstPage, SecondPage, FormKey)

        val toKey = navigator(*stack).apply { navigate(TargetKey) { popUpTo(FirstPage) } }
        val toType = navigator(*stack).apply { navigate(TargetKey) { popUpTo<PageKey>() } }
        val toTypeInclusive = navigator(*stack).apply {
            navigate(TargetKey) { popUpTo<PageKey> { inclusive = true } }
        }

        assertEquals(listOf(StartKey, FirstPage, TargetKey), toKey.stack())
        assertEquals(listOf(StartKey, FirstPage, SecondPage, TargetKey), toType.stack())
        assertEquals(listOf(StartKey, FirstPage, TargetKey), toTypeInclusive.stack())
    }

    @Test
    fun popUpToWithoutAMatchRemovesNothing() {
        val navigator = navigator(StartKey, FormKey)

        navigator.navigate(TargetKey) { popUpTo<ConfirmKey> { inclusive = true } }

        assertEquals(listOf(StartKey, FormKey, TargetKey), navigator.stack())
    }

    @Test
    fun popUpToTheRootInclusiveLeavesOnlyTheNewKey() {
        val navigator = navigator(StartKey, FormKey, FirstPage)

        navigator.navigate(TargetKey) { popUpTo<StartKey> { inclusive = true } }

        assertEquals(listOf(TargetKey), navigator.stack())
    }

    @Test
    fun popUpToRunsBeforeTheTopIsCompared() {
        val navigator = navigator(TargetKey, FormKey)

        navigator.navigate(TargetKey) { popUpTo<TargetKey>() }

        assertEquals(listOf(TargetKey), navigator.stack())
    }

    @Test
    fun launchSingleTopComparesTheTopThatPopUpToLeft() {
        val navigator = navigator(StartKey, FirstPage, FormKey)

        navigator.navigate(SecondPage) {
            popUpTo<PageKey>()
            launchSingleTop = true
        }

        assertEquals(listOf(StartKey, SecondPage), navigator.stack())
    }

    @Test
    fun navigateUpPopsTheTopButKeepsTheRoot() {
        val navigator = navigator(StartKey, FormKey)

        assertTrue(navigator.navigateUp())
        assertFalse(navigator.navigateUp())
        assertEquals(listOf(StartKey), navigator.stack())
    }
}
