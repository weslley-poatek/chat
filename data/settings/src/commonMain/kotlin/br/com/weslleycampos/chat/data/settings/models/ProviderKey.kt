package br.com.weslleycampos.chat.data.settings.models

/**
 * One row of the "api keys" group: a provider and what is shown of its key. The key itself never
 * leaves the repository; only [masked] does.
 *
 * @property masked The stored key as [KeyMask] shows it, such as "sk-ant-…4f2a", or `null` while
 *   none is stored: the row's "not set".
 */
data class ProviderKey(val provider: ModelProvider, val masked: String?) {
    /** Whether a key is stored: the row's filled dot, its "edit" instead of "add", and "remove". */
    val hasKey: Boolean get() = masked != null
}
