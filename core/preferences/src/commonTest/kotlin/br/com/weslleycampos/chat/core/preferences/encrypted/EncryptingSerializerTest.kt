package br.com.weslleycampos.chat.core.preferences.encrypted

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class EncryptingSerializerTest {
    private val aad = "store".encodeToByteArray()
    private val serializer = EncryptingSerializer(StringSerializer, XorAead, aad)

    @Test
    fun roundTrips() = runTest {
        assertEquals("secret", serializer.readFrom(write("secret")))
    }

    @Test
    fun prefixesVersionAndBindsItToAad() = runTest {
        val bytes = write("secret").readByteArray()
        assertEquals(1, bytes.first())
        assertContentEquals(byteArrayOf(1) + aad, XorAead.lastAad)
    }

    @Test
    fun rejectsEmptyInput() = runTest {
        assertFailsWith<CorruptionException> { serializer.readFrom(Buffer()) }
    }

    @Test
    fun rejectsUnsupportedVersion() = runTest {
        val bytes = write("secret").readByteArray().also { it[0] = 2 }
        assertFailsWith<CorruptionException> { serializer.readFrom(Buffer().write(bytes)) }
    }

    @Test
    fun mapsAuthenticationFailureToCorruption() = runTest {
        val failing = EncryptingSerializer(StringSerializer, FailingAead, aad)
        val error = assertFailsWith<CorruptionException> { failing.readFrom(write("secret")) }
        assertIs<AeadAuthenticationException>(error.cause)
    }

    private suspend fun write(value: String): Buffer = Buffer().also { serializer.writeTo(value, it) }

    private object StringSerializer : OkioSerializer<String> {
        override val defaultValue = ""
        override suspend fun readFrom(source: BufferedSource) = source.readUtf8()
        override suspend fun writeTo(t: String, sink: BufferedSink) {
            sink.writeUtf8(t)
        }
    }

    private object XorAead : Aead {
        var lastAad: ByteArray? = null

        override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray {
            lastAad = aad
            return plaintext.xor()
        }

        override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray = ciphertext.xor()

        private fun ByteArray.xor() = ByteArray(size) { (this[it].toInt() xor 0x5A).toByte() }
    }

    private object FailingAead : Aead {
        override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray = plaintext
        override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray = throw AeadAuthenticationException()
    }
}
