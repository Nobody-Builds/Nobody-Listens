package com.nobodybuilds.listens.utils

import android.content.Context
import com.nobodybuilds.listens.data.crash.CrashReportStore
import com.nobodybuilds.listens.data.crash.SafeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data class representing a saved crash log entry (kept for the older [com.nobodybuilds.listens.presentation.components.CrashReportDialog]).
 */
data class CrashLogData(
    val timestamp: Long,
    val formattedDate: String,
    val exceptionMessage: String,
    val stackTrace: String
) {
    /**
     * Returns the full crash log formatted for display or sharing.
     */
    fun getFullLog(): String {
        return buildString {
            appendLine("=== Nobody Listens Crash Report ===")
            appendLine("Date: $formattedDate")
            appendLine("Exception: $exceptionMessage")
            appendLine()
            appendLine("Stack Trace:")
            appendLine(stackTrace)
        }
    }
}

/**
 * Default uncaught exception handler. Writes the crash synchronously to
 * `filesDir/crash/last_crash.txt` (see [CrashReportStore.writeCrash]) and then hands the crash to
 * the previous default handler, so the normal crash behaviour is unchanged.
 * Installed in Application.attachBaseContext (before Hilt), so it needs no injection.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private const val LEGACY_PREFS_NAME = "crash_handler_prefs"

    private lateinit var appContext: Context
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    @Volatile
    private var handling = false

    /**
     * Installs this crash handler as the default uncaught exception handler.
     * Should be called in Application.onCreate().
     */
    fun install(context: Context) {
        // From Application.attachBaseContext the application context isn't set yet: use the app itself.
        appContext = context.applicationContext ?: context
        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current === this) return
        defaultHandler = current
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Guard against a second crash while writing the first one.
        if (!handling) {
            handling = true
            try {
                if (::appContext.isInitialized) CrashReportStore.writeCrash(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Never let saving the report get in the way of the crash itself.
            }
            // Counts towards safe mode only when the crash happened soon after process start.
            if (::appContext.isInitialized) SafeMode.recordCrash(appContext)
        }
        defaultHandler?.uncaughtException(thread, throwable)
    }

    /** True when a crash report exists on disk. Disk check — prefer CrashReportStore off the main thread. */
    fun hasCrashLog(): Boolean {
        if (!::appContext.isInitialized) return false
        return CrashReportStore.exists(appContext)
    }

    /** The saved crash in the older shape, or null. */
    fun getCrashLog(): CrashLogData? {
        if (!::appContext.isInitialized) return null
        val report = CrashReportStore.read(appContext) ?: return null
        return CrashLogData(
            timestamp = report.timestamp,
            formattedDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(report.timestamp)),
            exceptionMessage = report.message.ifBlank { report.exceptionType },
            stackTrace = report.stackTrace
        )
    }

    /** Clears the saved crash log (and the pre-1.3 SharedPreferences copy). */
    fun clearCrashLog() {
        if (!::appContext.isInitialized) return
        CrashReportStore.clear(appContext)
        runCatching { appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply() }
    }
}
