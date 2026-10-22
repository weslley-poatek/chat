package br.com.weslleycampos.chat.core.ui.icons

import br.com.weslleycampos.chat.core.ui.resources.CoreUiRes
import br.com.weslleycampos.chat.core.ui.resources.allDrawableResources
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatIconsTest {
    @Test
    fun everyBundledIconIsReachableFromOtherModulesOnce() {
        assertEquals(ChatIcons.all.size, ChatIcons.all.toSet().size)
        assertEquals(CoreUiRes.allDrawableResources.values.toSet(), ChatIcons.all.toSet())
    }
}
