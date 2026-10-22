package br.com.weslleycampos.chat.feature.chat

import kotlinx.datetime.TimeZone
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import kotlin.time.Clock

@Module
@ComponentScan
class ChatModule

/** The time zone is read on every use, so a change in the device's settings shows on the next message. */
@Single
fun chatTimes(): ChatTimes = ChatTimes(clock = Clock.System, timeZone = TimeZone::currentSystemDefault)
