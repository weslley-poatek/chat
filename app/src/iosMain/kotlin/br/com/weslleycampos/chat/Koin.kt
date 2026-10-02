package br.com.weslleycampos.chat

import org.koin.core.context.startKoin
import org.koin.plugin.module.dsl.module

/**
 * Called from the SwiftUI `App` initializer as `KoinKt.doInitKoin()` — Swift sees a top-level
 * function as a static method on a class named after this file, and Kotlin/Native prefixes names
 * starting with `init` with `do`. Renaming this file or function changes that Swift call.
 *
 * The untyped start on purpose: a typed `startKoin<AppModule>()` in the iOS compilation makes
 * compiler plugin 1.2.1 report KOIN-D002 for valid lookups of definitions from other Gradle modules.
 */
fun initKoin() {
    startKoin { module<AppModule>() }
}
