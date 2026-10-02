package br.com.weslleycampos.chat.core.preferences.encrypted

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android [Aead] backed by AES-GCM 256-bit with the key generated inside the
 * **Android Keystore**.
 *
 * The raw key bytes never leave the Keystore — the app can use the key to
 * encrypt/decrypt but cannot read its material. On devices with a TEE the key is
 * hardware-backed, so even root or a disk dump cannot extract it (StrongBox is not
 * requested). The key is deleted when the app is uninstalled.
 *
 * Wire format: `[iv(12) || ciphertext || tag(16)]`. The provider generates a random IV
 * per call and appends the 16-byte GCM tag to the ciphertext.
 */
internal object AndroidKeystoreAead : Aead {
    private const val KEY_ALIAS = "br.com.weslleycampos.chat.preferences.v1"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE_BITS = 256
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 16

    private val key: SecretKey by lazy {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .build(),
            )
            generateKey()
        }
    }

    override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        check(cipher.iv.size == IV_SIZE)
        cipher.updateAAD(aad)
        return cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray {
        if (ciphertext.size < IV_SIZE + TAG_SIZE) throw AeadAuthenticationException()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_SIZE * Byte.SIZE_BITS, ciphertext.copyOfRange(0, IV_SIZE)),
        )
        cipher.updateAAD(aad)
        return try {
            cipher.doFinal(ciphertext, IV_SIZE, ciphertext.size - IV_SIZE)
        } catch (e: AEADBadTagException) {
            throw AeadAuthenticationException(e)
        }
    }
}
