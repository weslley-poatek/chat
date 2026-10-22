package br.com.weslleycampos.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.weslleycampos.chat.core.navigation.utils.EntriesAggregator
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val entriesAggregator: EntriesAggregator by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AppHost(entries = entriesAggregator)
        }
    }
}
