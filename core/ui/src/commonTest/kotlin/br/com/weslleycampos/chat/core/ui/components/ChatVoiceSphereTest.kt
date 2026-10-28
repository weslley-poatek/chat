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
    fun muteStillsTheListeningSphereButNotTheSpeakingOne() {
        val muted = ChatVoiceParticles()
        val heard = ChatVoiceParticles()
        val spoken = ChatVoiceParticles()
        var mutedLoudest = 0f
        var heardLoudest = 0f
        var spokenLoudest = 0f
        // 3 s of frames: the amplitude settles in the first second, then the voice swings for two.
        repeat(180) { frame ->
            muted.step(dt = 1 / 60f, phase = ChatVoicePhase.Listening, muted = true)
            heard.step(dt = 1 / 60f, phase = ChatVoicePhase.Listening)
            spoken.step(dt = 1 / 60f, phase = ChatVoicePhase.Speaking, muted = true)
            if (frame >= 60) {
                mutedLoudest = maxOf(mutedLoudest, muted.amplitude)
                heardLoudest = maxOf(heardLoudest, heard.amplitude)
                spokenLoudest = maxOf(spokenLoudest, spoken.amplitude)
            }
        }

        assertEquals(0.05f, mutedLoudest, absoluteTolerance = 1e-3f)
        assertTrue(heardLoudest > 0.5f, "the unmuted voice peaks at $heardLoudest")
        assertTrue(spokenLoudest > 0.5f, "the muted speaking voice peaks at $spokenLoudest")
    }

    @Test
    fun providerCoversEveryPhaseAndMutedListeningInBothModes() {
        val cases = ChatVoiceSpherePreviewParameterProvider().values.toList()
        assertEquals(8, cases.size)
        assertEquals(8, cases.distinct().size)
        for (dark in listOf(false, true)) {
            val inMode = cases.filter { it.darkTheme == dark }
            assertEquals(ChatVoicePhase.entries.toSet(), inMode.map { it.phase }.toSet())
            assertEquals(listOf(ChatVoicePhase.Listening), inMode.filter { it.muted }.map { it.phase })
        }
    }
}
