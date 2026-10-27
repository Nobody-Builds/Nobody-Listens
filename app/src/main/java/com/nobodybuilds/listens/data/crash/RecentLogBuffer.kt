package com.nobodybuilds.listens.data.crash

import android.util.Log
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small in-memory ring buffer of the app's own recent log lines (INFO and above), so a crash
 * report can include what happened just before the crash. Fixed size, no I/O, no threads.
 * Timber skips formatting for DEBUG/VERBOSE because [isLoggable] rejects them.
 */
object RecentLogBuffer : Timber.Tree() {
    private const val CAPACITY = 200
    private const val MAX_LINE = 400

    private val times = LongArray(CAPACITY)
    private val priorities = IntArray(CAPACITY)
    private val lines = arrayOfNulls<String>(CAPACITY)
    private var next = 0
    private var size = 0
    private val lock = Any()

    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val text = if (message.length > MAX_LINE) message.substring(0, MAX_LINE) + "…" else message
        val line = if (tag != null) "$tag: $text" else text
        synchronized(lock) {
            times[next] = System.currentTimeMillis()
            priorities[next] = priority
            lines[next] = line
            next = (next + 1) % CAPACITY
            if (size < CAPACITY) size++
        }
    }

    /** Appends the buffered lines (oldest first) to [out]. Safe to call from a crash handler. */
    fun dumpTo(out: StringBuilder) {
        val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        synchronized(lock) {
            val start = (next - size + CAPACITY) % CAPACITY
            for (i in 0 until size) {
                val idx = (start + i) % CAPACITY
                out.append(format.format(Date(times[idx]))).append(' ')
                    .append(priorityChar(priorities[idx])).append('/')
                    .append(lines[idx]).append('\n')
            }
        }
    }

    private fun priorityChar(priority: Int): Char = when (priority) {
        Log.INFO -> 'I'
        Log.WARN -> 'W'
        Log.ERROR -> 'E'
        Log.ASSERT -> 'A'
        else -> 'D'
    }
}
