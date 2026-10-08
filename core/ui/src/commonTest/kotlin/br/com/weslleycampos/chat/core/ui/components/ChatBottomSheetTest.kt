package br.com.weslleycampos.chat.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatBottomSheetTest {
    @Test
    fun providerCoversHiddenPartialAndExpandedStatesInBothModes() {
        val cases = ChatBottomSheetPreviewParameterProvider().values.toList()
        assertEquals(6, cases.size)
        assertEquals(6, cases.distinct().size)
        for (dark in listOf(false, true)) {
            assertEquals(
                ChatBottomSheetPreviewState.entries.toSet(),
                cases.filter { it.darkTheme == dark }.map { it.state }.toSet(),
            )
        }
    }
}
