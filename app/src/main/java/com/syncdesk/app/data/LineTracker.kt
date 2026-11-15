package com.syncdesk.app.data

/**
 * Matches the speech engine's playback events to the line that is being spoken.
 *
 * The TextToSpeech SDK's events carry no request id, and it reports the end of a line twice
 * (seen as two `onDone` callbacks in the same millisecond on the Pleos Connect v2.1.0 emulator on
 * 2026-11-13). If the second one arrived after the next line had been asked for, that line would
 * count as finished before it was heard and be skipped. So an end only counts for a line that has
 * started playing, and not within [minLineMillis] of asking for it, which no real line is shorter
 * than. An error always ends the line, so a broken engine cannot stall the reading.
 *
 * Thread-safe: the engine calls back on its own threads.
 */
class LineTracker(
    private val clock: () -> Long,
    private val minLineMillis: Long = 200L,
) {
    private class Pending(val askedAt: Long, val finish: () -> Unit) {
        var started = false
    }

    private var pending: Pending? = null

    /** A new line was asked for; [finish] is called once when it ends. */
    @Synchronized
    fun begin(finish: () -> Unit) {
        pending = Pending(clock(), finish)
    }

    /** The engine started playing. */
    @Synchronized
    fun started() {
        pending?.started = true
    }

    /** The engine reports the end of a line. Returns whether it ended the current one. */
    fun ended(): Boolean {
        val finish = synchronized(this) {
            val line = pending ?: return false
            if (!line.started || clock() - line.askedAt < minLineMillis) return false
            pending = null
            line.finish
        }
        finish()
        return true
    }

    /** The engine failed: the current line ends, whatever state it is in. */
    fun failed() {
        val finish = synchronized(this) { pending.also { pending = null }?.finish } ?: return
        finish()
    }

    /** The line was given up on (cancelled or timed out); later events for it are ignored. */
    @Synchronized
    fun abandon() {
        pending = null
    }
}
