package br.com.weslleycampos.chat.core.preferences.encrypted

/**
 * iOS [Aead] backed by AES-256-CBC + HMAC-SHA256 ([IosCryptoAead]) with a random
 * 64-byte key stored as a generic password in the **Keychain**.
 *
 * Unlike Android Keystore, the raw key bytes are loaded into app memory: the Keychain
 * protects the key at rest (encrypted with hardware-derived data protection keys),
 * not in use. The item is `AfterFirstUnlockThisDeviceOnly`, so it is readable after
 * the first unlock following boot and is never synced to iCloud or restored to
 * another device.
 *
 * Wire format: `[iv(16) || ciphertext || tag(32)]`, see [IosCryptoAead].
 */
internal object IosKeychainAead : Aead {
    private const val KEY_TAG = "chat.preferences.aead.v1"

    private val key: ByteArray by lazy {
        KeychainKey.loadOrCreate(tag = KEY_TAG, byteSize = IosCryptoAead.REQUIRED_KEY_SIZE)
    }

    override fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray =
        IosCryptoAead.seal(key, plaintext, aad)

    override fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray =
        IosCryptoAead.open(key, ciphertext, aad)
}
