package br.com.weslleycampos.chat.core.preferences.encrypted

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class IosCryptoAeadTest {
    private val key = ByteArray(IosCryptoAead.REQUIRED_KEY_SIZE) { it.toByte() }
    private val aad = "aad".encodeToByteArray()
    private val plaintext = "secret".encodeToByteArray()

    @Test
    fun roundTrips() {
        assertContentEquals(plaintext, IosCryptoAead.open(key, IosCryptoAead.seal(key, plaintext, aad), aad))
    }

    @Test
    fun rejectsTamperedTag() {
        val sealed = IosCryptoAead.seal(key, plaintext, aad)
        sealed[sealed.lastIndex] = (sealed.last() + 1).toByte()
        assertFailsWith<AeadAuthenticationException> { IosCryptoAead.open(key, sealed, aad) }
    }

    @Test
    fun rejectsWrongAssociatedData() {
        val sealed = IosCryptoAead.seal(key, plaintext, aad)
        assertFailsWith<AeadAuthenticationException> {
            IosCryptoAead.open(key, sealed, "other".encodeToByteArray())
        }
    }

    @Test
    fun rejectsIvShiftedIntoAssociatedData() {
        val blockSize = 16
        val sealed = IosCryptoAead.seal(key, ByteArray(blockSize * 3) { it.toByte() }, aad)
        // Without length framing the MAC input is unchanged, and CBC drops the first plaintext block.
        val shiftedAad = aad + sealed.copyOfRange(0, blockSize)
        val shiftedCiphertext = sealed.copyOfRange(blockSize, sealed.size)
        assertFailsWith<AeadAuthenticationException> {
            IosCryptoAead.open(key, shiftedCiphertext, shiftedAad)
        }
    }
}
