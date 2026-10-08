package br.com.weslleycampos.chat.core.navigation.utils

import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

@Single
class EntriesAggregator(@Provided val entries: List<EntryProvider>)
