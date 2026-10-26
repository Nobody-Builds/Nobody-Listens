package com.twilighty.lmusic.data.crash

import android.content.Context
import android.net.Uri
import android.os.Build
import com.twilighty.lmusic.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A crash saved by the crash handler in `filesDir/crash/last_crash.txt`. */
data class CrashReport(
    val timestamp: Long,
    val time: String,
    val appVersion: String,
    val android: String,
    val device: String,
    val thread: String,
    val stackTrace: String,
    val recentLog: String,
    /** The whole file, as written. Used for "Copy details" and sharing. */
    val fullText: String,
) {
    private val headline: String = stackTrace.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    /** Fully qualified exception class, e.g. `java.lang.IllegalStateException`. */
    val exceptionType: String = headline.substringBefore(':').trim().ifEmpty { "Unknown error" }

    /** Short class name, e.g. `IllegalStateException`. */
    val exceptionShortType: String = exceptionType.substringAfterLast('.')

    val message: String = if (headline.contains(':')) headline.substringAfter(':').trim() else ""

    /** The innermost "Caused by" line, when the trace has one. */
    val rootCause: String? = stackTrace.lineSequence()
        .filter { it.trimStart().startsWith("Caused by:") }
        .lastOrNull()?.trim()?.removePrefix("Caused by:")?.trim()

    /** Up to [max] frames from the app's own code, most relevant first. */
    fun topAppFrames(max: Int = 5): List<String> = stackTrace.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("at ") && it.contains(APP_PACKAGE) }
        .distinct()
        .take(max)
        .map { it.removePrefix("at ").replace("$APP_PACKAGE.", "") }
        .toList()

    companion object {
        const val APP_PACKAGE = "com.twilighty.lmusic"
    }
}

/**
 * Reads, writes and clears the last crash report. The write path ([writeCrash]) is called from
 * the uncaught exception handler: synchronous, plain java.io, no coroutines or DataStore.
 */
object CrashReportStore {
    private const val DIR = "crash"
    private const val FILE = "last_crash.txt"
    private const val PREFS = "lmusic_crash"
    private const val KEY_SHOWN_TIMESTAMP = "shown_timestamp"

    private const val MARK_TRACE = "--- stack trace ---"
    private const val MARK_LOG = "--- recent log ---"

    const val ISSUES_URL = "https://github.com/Nobody-Builds/Nobody-Listens/issues/new"
    private const val MAX_URL_LENGTH = 7000

    fun file(context: Context): File = File(File(context.filesDir, DIR), FILE)

    fun exists(context: Context): Boolean = runCatching { file(context).isFile }.getOrDefault(false)

    // ------------------------------------------------------------------ write (crash handler)

    fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val now = System.currentTimeMillis()
        val sb = StringBuilder(16 * 1024)
        sb.append("Nobody Listens crash report\n")
        sb.append("time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(now))).append('\n')
        sb.append("timestamp: ").append(now).append('\n')
        sb.append("app: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")")
            .append(if (BuildConfig.DEBUG) " debug" else "").append('\n')
        sb.append("android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append("thread: ").append(thread.name).append('\n')
        sb.append(MARK_TRACE).append('\n')
        val sw = StringWriter(8 * 1024)
        PrintWriter(sw).use { throwable.printStackTrace(it) } // includes "Caused by" chains
        sb.append(sw.buffer).append('\n')
        runCatching {
            val logStart = sb.length
            sb.append(MARK_LOG).append('\n')
            RecentLogBuffer.dumpTo(sb)
            if (sb.length - logStart <= MARK_LOG.length + 1) sb.setLength(logStart)
        }

        val dir = File(context.filesDir, DIR)
        if (!dir.isDirectory) dir.mkdirs()
        val target = File(dir, FILE)
        val tmp = File(dir, "$FILE.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    // ------------------------------------------------------------------ read

    /** Parses the saved report, or null if there is none. Does disk I/O: call off the main thread. */
    fun read(context: Context): CrashReport? = runCatching {
        val f = file(context)
        if (!f.isFile) return null
        parse(f.readText(Charsets.UTF_8))
    }.getOrNull()

    /** The saved report if it has not been shown to the user yet. Disk I/O. */
    fun readUnshown(context: Context): CrashReport? {
        val report = read(context) ?: return null
        val shown = prefs(context).getLong(KEY_SHOWN_TIMESTAMP, -1L)
        return if (shown == report.timestamp) null else report
    }

    fun markShown(context: Context, report: CrashReport) {
        runCatching { prefs(context).edit().putLong(KEY_SHOWN_TIMESTAMP, report.timestamp).apply() }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun parse(text: String): CrashReport {
        val header = HashMap<String, String>()
        val traceIdx = text.indexOf(MARK_TRACE)
        val headerPart = if (traceIdx >= 0) text.substring(0, traceIdx) else ""
        headerPart.lineSequence().forEach { line ->
            val i = line.indexOf(": ")
            if (i > 0) header[line.substring(0, i).trim()] = line.substring(i + 2).trim()
        }
        val afterTrace = if (traceIdx >= 0) text.substring(traceIdx + MARK_TRACE.length) else text
        val logIdx = afterTrace.indexOf(MARK_LOG)
        val trace = (if (logIdx >= 0) afterTrace.substring(0, logIdx) else afterTrace).trim('\n', '\r')
        val log = if (logIdx >= 0) afterTrace.substring(logIdx + MARK_LOG.length).trim('\n', '\r') else ""
        return CrashReport(
            timestamp = header["timestamp"]?.toLongOrNull() ?: 0L,
            time = header["time"].orEmpty(),
            appVersion = header["app"].orEmpty(),
            android = header["android"].orEmpty(),
            device = header["device"].orEmpty(),
            thread = header["thread"].orEmpty(),
            stackTrace = trace,
            recentLog = log,
            fullText = text,
        )
    }

    // ------------------------------------------------------------------ GitHub issue

    /**
     * A prefilled "new issue" URL. The trace is trimmed so the whole URL stays under ~7000
     * characters (GitHub and some browsers reject longer ones).
     */
    fun githubIssueUrl(report: CrashReport?, safeMode: Boolean = false): String {
        val title = when {
            report != null -> "Crash: ${report.exceptionShortType}" +
                (if (report.message.isNotBlank()) ": ${report.message}" else "")
            else -> "Repeated crashes on start (safe mode)"
        }.take(120)

        fun body(trace: String): String = buildString {
            append("**What were you doing when it happened?**\n\n<!-- e.g. opened the player, changed a setting -->\n\n")
            append("**Details**\n")
            append("- App: ").append(report?.appVersion?.ifBlank { null } ?: "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").append('\n')
            append("- Android: ").append(report?.android?.ifBlank { null } ?: "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})").append('\n')
            append("- Device: ").append(report?.device?.ifBlank { null } ?: "${Build.MANUFACTURER} ${Build.MODEL}").append('\n')
            if (report != null) {
                append("- Time: ").append(report.time).append('\n')
                append("- Thread: ").append(report.thread).append('\n')
            }
            if (safeMode) append("- Safe mode was turned on after repeated crashes\n")
            if (report != null && trace.isNotEmpty()) {
                append("\n**Stack trace**\n```\n").append(trace).append("\n```\n")
            }
        }

        val fullTrace = report?.stackTrace.orEmpty()
        var limit = fullTrace.length
        while (true) {
            val trace = if (limit >= fullTrace.length) fullTrace else fullTrace.take(limit).substringBeforeLast('\n') + "\n…(trimmed)"
            val url = "$ISSUES_URL?title=${Uri.encode(title)}&body=${Uri.encode(body(trace))}"
            if (url.length <= MAX_URL_LENGTH || limit <= 0) return url
            limit = (limit * 0.8).toInt().let { if (it < 200) 0 else it }
        }
    }
}
