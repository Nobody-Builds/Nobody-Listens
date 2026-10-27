package com.nobodybuilds.listens.data.crash

import android.content.Context
import android.os.Process
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Safe mode: entered automatically after two launches in a row that crashed early (an uncaught
 * exception recorded by [com.nobodybuilds.listens.utils.CrashHandler] within [EARLY_CRASH_WINDOW_MS]
 * of process start). Processes that are simply killed (widget / worker / alarm / receiver starts)
 * never count. While active, optional / experimental features must stay off.
 *
 * How a feature checks it (synchronous, cheap after the first call, safe on any thread):
 *
 * ```
 * if (SafeMode.isActive(context)) return   // don't autostart / schedule / attach listeners
 * ```
 *
 * Features skip their autostart / scheduling while it is active. Saved settings are never
 * changed by safe mode: everything comes back as it was after "Turn features back on". To react
 * to that tap without a restart, register with [addExitListener].
 *
 * Storage: SharedPreferences "lmusic_safe_mode" (keys "active", "early_crashes"), written with commit().
 */
object SafeMode {
    private const val PREFS = "lmusic_safe_mode"
    private const val KEY_ACTIVE = "active"
    private const val KEY_EARLY_CRASHES = "early_crashes"
    private const val KEY_LEGACY_UNSTABLE_LAUNCHES = "unstable_launches"
    private const val KEY_ENTERED_AT = "entered_at"

    /** Consecutive early crashes that trigger safe mode on the next start. */
    private const val THRESHOLD = 2

    /** A crash this soon after process start counts as an early (launch) crash. */
    private const val EARLY_CRASH_WINDOW_MS = 30_000L

    @Volatile
    private var cachedActive: Boolean? = null

    /** True when safe mode was entered during this process start. */
    @Volatile
    var enteredThisLaunch: Boolean = false
        private set

    private val exitListeners = CopyOnWriteArrayList<() -> Unit>()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether optional features must stay off. Never throws. */
    fun isActive(context: Context): Boolean {
        cachedActive?.let { return it }
        val value = runCatching { prefs(context).getBoolean(KEY_ACTIVE, false) }.getOrDefault(false)
        cachedActive = value
        return value
    }

    /**
     * Called first thing in Application.onCreate. Enters safe mode if the previous [THRESHOLD]
     * launches crashed early (see [recordCrash]). Does not count this launch as anything.
     * Returns whether safe mode is active for this launch.
     */
    fun onAppStart(context: Context): Boolean {
        val p = prefs(context)
        val crashes = p.getInt(KEY_EARLY_CRASHES, 0)
        val alreadyActive = p.getBoolean(KEY_ACTIVE, false)
        val enter = !alreadyActive && crashes >= THRESHOLD
        if (enter || p.contains(KEY_LEGACY_UNSTABLE_LAUNCHES)) {
            val editor = p.edit().remove(KEY_LEGACY_UNSTABLE_LAUNCHES)
            if (enter) {
                editor.putBoolean(KEY_ACTIVE, true)
                    .putLong(KEY_ENTERED_AT, System.currentTimeMillis())
                    .putInt(KEY_EARLY_CRASHES, 0)
            }
            editor.commit()
        }
        enteredThisLaunch = enter
        val active = alreadyActive || enter
        cachedActive = active
        return active
    }

    /**
     * Called from the uncaught exception handler (synchronously, before the process dies). Counts
     * the crash only when it happened within [EARLY_CRASH_WINDOW_MS] of process start. Never throws.
     */
    fun recordCrash(context: Context) {
        runCatching {
            val sinceStart = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
            if (sinceStart < 0 || sinceStart > EARLY_CRASH_WINDOW_MS) return
            val p = prefs(context)
            p.edit().putInt(KEY_EARLY_CRASHES, p.getInt(KEY_EARLY_CRASHES, 0) + 1).commit()
        }
    }

    /** This launch is stable (MainActivity resumed ~10 s, or the process lived 60 s): resets the count. */
    fun markStable(context: Context) {
        runCatching {
            val p = prefs(context)
            if (p.getInt(KEY_EARLY_CRASHES, 0) != 0) p.edit().putInt(KEY_EARLY_CRASHES, 0).apply()
        }
    }

    /** "Turn features back on": clears the flag; listeners restart the features that stayed off. */
    fun exit(context: Context) {
        runCatching {
            prefs(context).edit()
                .putBoolean(KEY_ACTIVE, false)
                .putInt(KEY_EARLY_CRASHES, 0)
                .remove(KEY_ENTERED_AT)
                .commit()
        }
        cachedActive = false
        enteredThisLaunch = false
        exitListeners.forEach { runCatching { it() } }
    }

    fun addExitListener(listener: () -> Unit) {
        exitListeners += listener
    }
}
