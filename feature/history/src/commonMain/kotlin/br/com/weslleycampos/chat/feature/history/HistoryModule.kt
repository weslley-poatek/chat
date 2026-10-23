package br.com.weslleycampos.chat.feature.history

import kotlinx.datetime.TimeZone
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.time.Clock

@Module
@ComponentScan
class HistoryModule

/** The device's clock and time zone, the time zone read on every use. */
@Single
fun historyTimes(): HistoryTimes = HistoryTimes(clock = Clock.System, zone = TimeZone::currentSystemDefault)
