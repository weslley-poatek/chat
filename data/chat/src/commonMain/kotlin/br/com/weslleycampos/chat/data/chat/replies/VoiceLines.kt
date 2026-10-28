package br.com.weslleycampos.chat.data.chat.replies

import br.com.weslleycampos.chat.data.chat.models.VoiceExchange

/**
 * Voice mode's canned exchanges, in turn: a conversation's first exchange is the first one, its
 * fifth the first again. The first is the design prototype's `USER_T` and `MODEL_T`, to the
 * character; the others go on with the same debounce hook, in the same voice, so the loop does not
 * repeat one sentence.
 *
 * Every line after the first fits its phase at one word every 230 ms: a transcript in at most 28
 * words (listening lasts 6500 ms), a reply in at most 30 (speaking lasts 7000 ms). The prototype's
 * `MODEL_T` has 33, so its last words are still grey when speaking ends, as they are there.
 */
internal object VoiceLines {
    val exchanges: List<VoiceExchange> = listOf(
        VoiceExchange(
            transcript = "So the debounce hook works, but I also want it to fire on the leading edge of a burst, " +
                "not just the trailing one.",
            reply = "Add a leading flag. On the first change in a burst set the value immediately, then suppress " +
                "until the window closes. I will put the timer in a ref so it survives renders.",
        ),
        VoiceExchange(
            transcript = "Okay, and if the component unmounts while the timer is still pending, does the trailing " +
                "update still fire?",
            reply = "It would, so clear the timer in the effect cleanup. Then an unmount mid-burst drops the " +
                "pending update instead of setting state on a component that is gone.",
        ),
        VoiceExchange(
            transcript = "Should the delay be allowed to change between renders, or is it fine to read it once on " +
                "mount?",
            reply = "Let it change. Keep the delay in the dependency array, so a new value restarts the window. " +
                "Reading it once hides a stale closure until someone tunes it.",
        ),
        VoiceExchange(
            transcript = "Last thing. How would you test the leading edge without waiting on real timers?",
            reply = "Use fake timers. Render the hook, change the value twice and check the first change shows at " +
                "once. Then advance past the delay and check nothing else fires.",
        ),
    )

    /** The exchange for a conversation that already has [exchangesSoFar] exchanges: its user messages. */
    fun next(exchangesSoFar: Int): VoiceExchange = exchanges[exchangesSoFar.mod(exchanges.size)]
}
