package br.com.weslleycampos.chat

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
