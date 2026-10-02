package br.com.weslleycampos.chat

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.plugin.module.dsl.startKoin

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin<AppModule> {
            androidContext(this@MainApplication)
        }
    }
}
