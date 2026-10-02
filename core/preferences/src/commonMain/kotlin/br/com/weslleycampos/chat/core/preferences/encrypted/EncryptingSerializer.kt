package br.com.weslleycampos.chat.core.preferences.encrypted

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource

private const val FORMAT_VERSION: Byte = 1

internal class EncryptingSerializer<T>(
    private val delegate: OkioSerializer<T>,
    private val aead: Aead,
    aad: ByteArray,
) : OkioSerializer<T> {
    private val aad = byteArrayOf(FORMAT_VERSION) + aad

    override val defaultValue: T get() = delegate.defaultValue

    override suspend fun readFrom(source: BufferedSource): T {
        val bytes = source.readByteArray()
        if (bytes.firstOrNull() != FORMAT_VERSION) {
            throw CorruptionException("Unsupported encrypted preferences format")
        }
        val plaintext = try {
            aead.decrypt(bytes.copyOfRange(1, bytes.size), aad)
        } catch (e: AeadAuthenticationException) {
            throw CorruptionException("Encrypted preferences unreadable", e)
        }
        return delegate.readFrom(Buffer().apply { write(plaintext) })
    }

    override suspend fun writeTo(t: T, sink: BufferedSink) {
        val plaintext = Buffer().also { delegate.writeTo(t, it) }.readByteArray()
        sink.writeByte(FORMAT_VERSION.toInt())
        sink.write(aead.encrypt(plaintext, aad))
    }
}
