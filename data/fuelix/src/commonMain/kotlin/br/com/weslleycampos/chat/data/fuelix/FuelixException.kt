package br.com.weslleycampos.chat.data.fuelix

/** Why a [FuelixClient] call got no answer. Its message names the reason, never the key. */
sealed class FuelixException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No key is stored: the call was never sent. */
    class MissingKey : FuelixException("No Fuelix key is stored")

    /** Fuelix refused the key: HTTP 401. */
    class KeyRefused : FuelixException("Fuelix refused the key")

    /** The key may not use the model, or Fuelix has no such model: HTTP 403 or 404. */
    class ModelUnavailable(val status: Int) : FuelixException("The model is not available: HTTP $status")

    /** Too many requests for now: HTTP 429. */
    class RateLimited : FuelixException("Fuelix is rate limiting the key")

    /** Fuelix could not be reached, or the connection broke or timed out on the way. */
    class Unreachable(cause: Throwable) : FuelixException("Fuelix could not be reached", cause)

    /**
     * Any other failure: another HTTP [status], an error sent in the middle of a reply (`null`
     * status), or an answer that is not what the API describes.
     */
    class Failed(val status: Int?) : FuelixException("Fuelix could not answer" + (status?.let { ": HTTP $it" } ?: ""))
}
