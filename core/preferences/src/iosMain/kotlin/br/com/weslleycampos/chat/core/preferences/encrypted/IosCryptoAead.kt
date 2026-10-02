@file:OptIn(ExperimentalForeignApi::class)

package br.com.weslleycampos.chat.core.preferences.encrypted

import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import okio.Buffer
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CCOperation
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCSuccess
import platform.Security.SecRandomCopyBytes
import platform.Security.errSecSuccess
import platform.posix.size_tVar

/**
 * AES-256-CBC + HMAC-SHA256 authenticated encryption (Encrypt-then-MAC).
 *
 * Uses only primitives exposed by Kotlin/Native's public `platform.CoreCrypto` bindings
 * (`CCCrypt`, `CCHmac`) — avoids the SPI-only `CCCryptorGCMOneshot*` functions that are
 * not in the default cinterop headers.
 *
 * Key format: 64 bytes — first 32 used as the AES-256 key, last 32 as the HMAC-SHA256 key.
 * Wire format: `[iv(16) || ciphertext || tag(32)]`.
 * The HMAC covers `aadLength(8-byte big-endian byte count) || aad || iv || ciphertext`.
 */
internal object IosCryptoAead {
    private const val AES_KEY_LEN = 32
    private const val HMAC_KEY_LEN = 32
    private const val IV_LEN = 16
    private const val TAG_LEN = 32

    const val REQUIRED_KEY_SIZE: Int = AES_KEY_LEN + HMAC_KEY_LEN

    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val (aesKey, macKey) = splitKey(key)
        val iv = secureRandomBytes(IV_LEN)
        val ciphertext = aesCbc(kCCEncrypt, aesKey, iv, plaintext, outSize = plaintext.size + IV_LEN)
        val tag = hmacSha256(macKey, aad, iv, ciphertext)
        return iv + ciphertext + tag
    }

    fun open(key: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray {
        val (aesKey, macKey) = splitKey(key)
        if (sealed.size < IV_LEN + TAG_LEN) throw AeadAuthenticationException()

        val iv = sealed.copyOfRange(0, IV_LEN)
        val tag = sealed.copyOfRange(sealed.size - TAG_LEN, sealed.size)
        val ciphertext = sealed.copyOfRange(IV_LEN, sealed.size - TAG_LEN)

        val expected = hmacSha256(macKey, aad, iv, ciphertext)
        if (!constantTimeEquals(tag, expected)) throw AeadAuthenticationException()
        return aesCbc(kCCDecrypt, aesKey, iv, ciphertext, outSize = ciphertext.size)
    }

    private fun splitKey(key: ByteArray): Pair<ByteArray, ByteArray> {
        require(key.size == REQUIRED_KEY_SIZE) { "AEAD key must be $REQUIRED_KEY_SIZE bytes" }
        return key.copyOfRange(0, AES_KEY_LEN) to key.copyOfRange(AES_KEY_LEN, REQUIRED_KEY_SIZE)
    }

    private fun aesCbc(op: CCOperation, key: ByteArray, iv: ByteArray, input: ByteArray, outSize: Int): ByteArray {
        val out = ByteArray(outSize)
        memScoped {
            val outMoved = alloc<size_tVar>()
            key.usePinned { kp ->
                iv.usePinned { ivp ->
                    input.usePinned { ip ->
                        out.usePinned { outPinned ->
                            val status = CCCrypt(
                                op = op,
                                alg = kCCAlgorithmAES,
                                options = kCCOptionPKCS7Padding,
                                key = kp.addressOf(0),
                                keyLength = AES_KEY_LEN.convert(),
                                iv = ivp.addressOf(0),
                                dataIn = if (input.isEmpty()) null else ip.addressOf(0) as COpaquePointer?,
                                dataInLength = input.size.convert(),
                                dataOut = outPinned.addressOf(0),
                                dataOutAvailable = outSize.convert(),
                                dataOutMoved = outMoved.ptr,
                            )
                            check(status == kCCSuccess) { "CCCrypt failed (op=$op, status=$status)" }
                        }
                    }
                }
            }
            return out.copyOf(outMoved.value.toInt())
        }
    }

    private fun hmacSha256(macKey: ByteArray, aad: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val message = Buffer().writeLong(aad.size.toLong()).write(aad).write(iv).write(ciphertext).readByteArray()
        val tag = ByteArray(TAG_LEN)
        macKey.usePinned { kp ->
            message.usePinned { mp ->
                tag.usePinned { tp ->
                    CCHmac(
                        algorithm = kCCHmacAlgSHA256,
                        key = kp.addressOf(0),
                        keyLength = HMAC_KEY_LEN.convert(),
                        data = if (message.isEmpty()) null else mp.addressOf(0) as COpaquePointer?,
                        dataLength = message.size.convert(),
                        macOut = tp.addressOf(0),
                    )
                }
            }
        }
        return tag
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}

internal fun secureRandomBytes(size: Int): ByteArray {
    val out = ByteArray(size)
    out.usePinned { pinned ->
        val rc = SecRandomCopyBytes(null, size.convert(), pinned.addressOf(0))
        check(rc == errSecSuccess) { "SecRandomCopyBytes failed (OSStatus=$rc)" }
    }
    return out
}
