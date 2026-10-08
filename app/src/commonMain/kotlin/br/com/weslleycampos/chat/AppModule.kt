package br.com.weslleycampos.chat

import br.com.weslleycampos.chat.core.navigation.NavigationModule
import br.com.weslleycampos.chat.core.preferences.PreferencesModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

/**
 * The root Koin module. `@Configuration` is what makes the typed `startKoin<AppModule>` load it:
 * without it everything still compiles, Koin starts with no modules, and the first injection throws
 * at runtime. Only the root is tagged — feature modules join through `includes`.
 */
@Configuration
@Module(
    includes = [
        PreferencesModule::class,
        NavigationModule::class,
    ]
)
@ComponentScan
class AppModule
