package br.com.weslleycampos.chat.core.preferences.encrypted

internal interface Aead {
    fun encrypt(plaintext: ByteArray, aad: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray, aad: ByteArray): ByteArray
}
