package br.com.weslleycampos.chat.data.auth

sealed class AuthException(message: String?, cause: Throwable?) : Exception(message, cause) {
    class InvalidCredentials(message: String?, cause: Throwable?) : AuthException(message, cause)
    class NetworkError(message: String?, cause: Throwable?) : AuthException(message, cause)
    class Unknown(message: String?, cause: Throwable?) : AuthException(message, cause)
}
