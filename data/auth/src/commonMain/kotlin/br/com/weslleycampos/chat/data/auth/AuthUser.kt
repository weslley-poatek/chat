package br.com.weslleycampos.chat.data.auth

data class AuthUser(
    val id: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
)
