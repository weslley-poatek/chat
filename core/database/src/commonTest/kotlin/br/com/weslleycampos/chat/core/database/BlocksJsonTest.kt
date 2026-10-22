package br.com.weslleycampos.chat.core.database

import br.com.weslleycampos.chat.core.database.entities.StoredBlock
import kotlin.test.Test
import kotlin.test.assertEquals

class BlocksJsonTest {
    @Test
    fun anEmptyReplyIsAnEmptyArray() {
        assertEquals("[]", encodeBlocks(emptyList()))
        assertEquals(emptyList(), decodeBlocks("[]"))
    }

    @Test
    fun aBlockTypeThisVersionDoesNotKnowIsLeftOutAndUnknownKeysAreIgnored() {
        val value = """[{"type":"p","text":"a","tone":"calm"},""" +
            """{"type":"image","src":"x.png"},""" +
            """{"type":"list","items":["b"]}]"""

        assertEquals(listOf(StoredBlock.Paragraph("a"), StoredBlock.NumberedList(listOf("b"))), decodeBlocks(value))
    }
}
