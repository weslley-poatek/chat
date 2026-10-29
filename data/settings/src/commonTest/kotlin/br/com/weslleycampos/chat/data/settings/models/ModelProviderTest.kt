package br.com.weslleycampos.chat.data.settings.models

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelProviderTest {
    @Test
    fun fuelixIsTheOnlyProviderWithAStableIdAndThePasteKeyPlaceholder() {
        assertEquals(
            listOf(Triple("fuelix", "Fuelix", "paste key")),
            ModelProvider.entries.map { Triple(it.id, it.displayName, it.placeholder) },
        )
    }
}
