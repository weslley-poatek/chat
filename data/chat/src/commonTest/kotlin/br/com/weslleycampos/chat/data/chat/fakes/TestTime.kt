package br.com.weslleycampos.chat.data.chat.fakes

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/** 2026-10-22T12:00:00Z, the instant every test clock starts at. */
internal val TEST_START: Instant = Instant.fromEpochMilliseconds(1_792_670_400_000)

/** A clock on the test's virtual time: it reads [TEST_START] plus the scheduler's elapsed time. */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TestClock(private val scheduler: TestCoroutineScheduler) : Clock {
    override fun now(): Instant = TEST_START + scheduler.currentTime.milliseconds
}
