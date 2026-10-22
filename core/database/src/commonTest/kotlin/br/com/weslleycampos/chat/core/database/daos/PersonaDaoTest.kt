package br.com.weslleycampos.chat.core.database.daos

import br.com.weslleycampos.chat.core.database.entities.PersonaEntity
import br.com.weslleycampos.chat.core.database.inMemoryChatDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonaDaoTest {
    private val database = inMemoryChatDatabase()
    private val dao = database.personaDao()

    private val reviewer = PersonaEntity(
        id = "reviewer",
        name = "Code reviewer",
        modelId = "sonnet",
        prompt = "Correctness first, style last. Cite line numbers. No praise.",
        position = 0,
    )
    private val terse = PersonaEntity(
        id = "terse",
        name = "Terse",
        modelId = "gpt5",
        prompt = "One paragraph max. Code over prose.",
        position = 1,
    )
    private val digest = PersonaEntity(
        id = "digest",
        name = "Paper digest",
        modelId = "gemini",
        prompt = "Claim, method, evidence, caveats. Bullets.",
        position = 2,
    )

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun aNewDatabaseHasNoPersonas() = runTest {
        assertTrue(dao.observeAll().first().isEmpty())
    }

    @Test
    fun personasAreObservedInTheirPositionOrderWhateverTheirIds() = runTest {
        dao.insertIfAbsent(listOf(digest, reviewer, terse))

        assertEquals(listOf(reviewer, terse, digest), dao.observeAll().first())
    }

    @Test
    fun insertingPersonasAgainKeepsTheRowsAlreadyThereAndAddsTheMissingOnes() = runTest {
        val renamed = reviewer.copy(name = "Strict reviewer", prompt = "Be strict.")
        dao.insertIfAbsent(listOf(renamed))

        dao.insertIfAbsent(listOf(reviewer, terse, digest))
        dao.insertIfAbsent(listOf(reviewer, terse, digest))

        assertEquals(listOf(renamed, terse, digest), dao.observeAll().first())
    }

    @Test
    fun aTieInPositionIsOrderedById() = runTest {
        dao.insertIfAbsent(listOf(terse.copy(position = 0), reviewer))

        assertEquals(listOf("reviewer", "terse"), dao.observeAll().first().map { it.id })
    }
}
