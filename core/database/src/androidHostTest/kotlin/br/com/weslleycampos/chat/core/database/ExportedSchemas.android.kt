package br.com.weslleycampos.chat.core.database

import java.io.File

internal actual fun environmentVariable(name: String): String? = System.getenv(name)

internal actual fun readTextFile(path: String): String? = File(path).takeIf(File::isFile)?.readText()
