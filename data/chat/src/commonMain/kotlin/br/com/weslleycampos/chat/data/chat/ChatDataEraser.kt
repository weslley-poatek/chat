package br.com.weslleycampos.chat.data.chat

/**
 * Erases everything the chat keeps on this device, as deleting the account does. Signing out
 * erases nothing: conversations and choices belong to the device, and nothing syncs.
 */
interface ChatDataEraser {
    /**
     * Deletes every conversation with its messages, archived and pinned ones included, and the
     * favorites and default model, which then read as on a fresh install:
     * [ModelRepository.DEFAULT_FAVORITE_IDS] and `ModelCatalogue.default`. The persona presets are
     * catalogue data and stay. A reply still streaming stops at its next tick, as after
     * [ChatRepository.delete]. Running it again erases nothing more.
     */
    suspend fun eraseAll()
}
