package br.com.weslleycampos.chat.feature.auth.signin

import org.jetbrains.compose.resources.StringResource

data class SignInUiState(
    val loading: Boolean = false,
    val error: StringResource? = null,
)
