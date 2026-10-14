package br.com.weslleycampos.chat.core.ui.components

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatVoiceSphereTest {
    @Test
    fun particlesStartEvenlyOnTheUnitSphere() {
        val particles = ChatVoiceParticles()
        val positions = particles.positions
        for (i in 0 until particles.count) {
            val x = positions[i * 3]
            val y = positions[i * 3 + 1]
            val z = positions[i * 3 + 2]
            assertEquals(1f, sqrt(x * x + y * y + z * z), absoluteTolerance = 1e-4f)
        }
        assertEquals(1f, particles.mix(ChatVoicePhase.Listening))
    }

    @Test
    fun thinkingGathersTheParticlesIntoATiltedRing() {
        val particles = ChatVoiceParticles()
        repeat(200) { particles.step(dt = 0.05f, phase = ChatVoicePhase.Thinking) }

        assertEquals(1f, particles.mix(ChatVoicePhase.Thinking), absoluteTolerance = 1e-3f)
        val positions = particles.positions
        for (i in 0 until particles.count) {
            val x = positions[i * 3]
            val y = positions[i * 3 + 1]
            val z = positions[i * 3 + 2]
            val ring = sqrt(x * x + z * z)
            assertTrue(ring in 0.6f..0.9f, "particle $i is $ring from the axis")
            assertTrue(abs(y) < 0.4f, "particle $i is $y off the ring")
        }
    }

    @Test
    fun phaseMixAlwaysAddsUpToOne() {
        val particles = ChatVoiceParticles()
        for (phase in listOf(ChatVoicePhase.Speaking, ChatVoicePhase.Thinking, ChatVoicePhase.Listening)) {
            repeat(10) {
                particles.step(dt = 0.016f, phase = phase)
                val total = ChatVoicePhase.entries.sumOf { particles.mix(it).toDouble() }
                assertEquals(1.0, total, absoluteTolerance = 1e-4)
            }
        }
    }

    @Test
    fun providerCoversEveryPhaseInBothModes() {
        val cases = ChatVoiceSpherePreviewParameterProvider().values.toList()
        assertEquals(6, cases.size)
        assertEquals(6, cases.distinct().size)
        for (dark in listOf(false, true)) {
            assertEquals(
                ChatVoicePhase.entries.toSet(),
                cases.filter { it.darkTheme == dark }.map { it.phase }.toSet(),
            )
        }
    }
}
