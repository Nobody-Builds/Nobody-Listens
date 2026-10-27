package com.nobodybuilds.listens.presentation.crash

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nobodybuilds.listens.data.crash.CrashReport
import com.nobodybuilds.listens.data.crash.CrashReportStore
import com.nobodybuilds.listens.data.crash.SafeMode
import com.nobodybuilds.listens.presentation.screens.SettingsItem
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonSize
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbCard
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbOverline
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbTopBar
import com.nobodybuilds.listens.presentation.nb.NbType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- actions

internal fun openCrashIssue(context: Context, report: CrashReport?, safeMode: Boolean = false) {
    val url = CrashReportStore.githubIssueUrl(report, safeMode)
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
    }
}

internal fun copyCrashDetails(context: Context, report: CrashReport) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Nobody Listens crash report", report.fullText))
        Toast.makeText(context, "Details copied", Toast.LENGTH_SHORT).show()
    }
}

internal fun shareCrashDetails(context: Context, report: CrashReport) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Nobody Listens crash report")
            putExtra(Intent.EXTRA_TEXT, report.fullText)
        }
        context.startActivity(Intent.createChooser(send, "Share crash report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

// ---------------------------------------------------------------- full-screen report

/**
 * Full-screen Daylight dialog for a saved crash. On launch it shows "Something went wrong last
 * time"; from Settings ([fromSettings]) it also offers Share and Clear.
 */
@Composable
fun CrashReportFullScreen(
    report: CrashReport,
    onDismiss: () -> Unit,
    fromSettings: Boolean = false,
    onClear: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var showFull by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Nb.canvas) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                NbTopBar(
                    title = if (fromSettings) "Last crash report" else "Something went wrong last time",
                    kicker = "Crash report",
                    titleStyle = NbType.h2,
                    actions = {
                        NbIconButton(imageVector = Icons.Rounded.Close, contentDescription = "Close", onClick = onDismiss)
                    },
                )
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        if (fromSettings) "The most recent time Nobody Listens closed unexpectedly."
                        else "Nobody Listens closed unexpectedly. Reporting it helps get it fixed. Nothing is sent unless you choose to.",
                        style = NbType.body,
                        color = Nb.textMuted,
                    )
                    CrashSummaryCard(report)
                    NbButton(
                        text = if (showFull) "Hide full details" else "Show full details",
                        onClick = { showFull = !showFull },
                        style = NbButtonStyle.Quiet,
                        size = NbButtonSize.Small,
                    )
                    if (showFull) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(Nb.subtle, NbShapes.md)
                                .border(1.dp, Nb.border, NbShapes.md)
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                report.fullText,
                                style = NbType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                                color = Nb.text,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NbButton(
                        text = "Report on GitHub",
                        onClick = { openCrashIssue(context, report, SafeMode.isActive(context)) },
                        modifier = Modifier.fillMaxWidth(),
                        size = NbButtonSize.Large,
                        icon = rememberVectorPainter(Icons.Rounded.BugReport),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NbButton(
                            text = "Copy details",
                            onClick = { copyCrashDetails(context, report) },
                            modifier = Modifier.weight(1f),
                            style = NbButtonStyle.Secondary,
                        )
                        if (fromSettings) {
                            NbButton(
                                text = "Share",
                                onClick = { shareCrashDetails(context, report) },
                                modifier = Modifier.weight(1f),
                                style = NbButtonStyle.Secondary,
                            )
                        } else {
                            NbButton(
                                text = "Dismiss",
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                style = NbButtonStyle.Quiet,
                            )
                        }
                    }
                    if (fromSettings && onClear != null) {
                        NbButton(
                            text = "Clear report",
                            onClick = onClear,
                            modifier = Modifier.fillMaxWidth(),
                            style = NbButtonStyle.DangerQuiet,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashSummaryCard(report: CrashReport) {
    NbCard(modifier = Modifier.fillMaxWidth()) {
        NbOverline(report.exceptionShortType, color = Nb.danger)
        if (report.message.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(report.message.take(300), style = NbType.title, color = Nb.text)
        }
        report.rootCause?.let { cause ->
            Spacer(Modifier.height(8.dp))
            Text("Caused by ${cause.take(240)}", style = NbType.meta, color = Nb.textMuted)
        }
        val frames = report.topAppFrames()
        if (frames.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Nb.subtle, NbShapes.sm)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                frames.forEach {
                    Text(
                        it,
                        style = NbType.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                        color = Nb.text,
                        maxLines = 2,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            listOf(report.appVersion.ifBlank { null }?.let { "Nobody Listens $it" }, report.android.ifBlank { null }?.let { "Android $it" }, report.device.ifBlank { null }, report.time.ifBlank { null })
                .filterNotNull().joinToString(" · "),
            style = NbType.caption,
            color = Nb.textMuted,
        )
    }
}

// ---------------------------------------------------------------- launch check (MainActivity)

/**
 * Shows the crash report once after a crash. Reads the file off the main thread and marks it
 * shown as soon as it is displayed, so a crash inside the dialog can't loop.
 */
@Composable
fun CrashReportOnLaunch(enabled: Boolean = true) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<CrashReport?>(null) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        val unshown = withContext(Dispatchers.IO) {
            runCatching { CrashReportStore.readUnshown(context.applicationContext) }.getOrNull()
        } ?: return@LaunchedEffect
        CrashReportStore.markShown(context.applicationContext, unshown)
        report = unshown
    }
    report?.let { r -> CrashReportFullScreen(report = r, onDismiss = { report = null }) }
}

// ---------------------------------------------------------------- safe mode banner

@Composable
fun SafeModeBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var active by remember { mutableStateOf(SafeMode.isActive(context)) }
    var hidden by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    if (!active || hidden) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Nb.warningSoft, NbShapes.lg)
            .border(1.dp, Nb.border, NbShapes.lg)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.HealthAndSafety, contentDescription = null, tint = Nb.warningText)
            Text(
                "Safe mode: some features were turned off after repeated crashes",
                style = NbType.label,
                color = Nb.warningText,
                modifier = Modifier.weight(1f).padding(start = 10.dp, end = 4.dp),
            )
            NbIconButton(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Hide",
                onClick = { hidden = true },
                size = 36.dp,
                iconSize = 18.dp,
                tint = Nb.warningText,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NbButton(
                text = "Report",
                onClick = {
                    scope.launch {
                        val report = withContext(Dispatchers.IO) { CrashReportStore.read(context.applicationContext) }
                        openCrashIssue(context, report, safeMode = true)
                    }
                },
                style = NbButtonStyle.Secondary,
                size = NbButtonSize.Small,
            )
            NbButton(
                text = "Turn features back on",
                onClick = {
                    SafeMode.exit(context.applicationContext)
                    active = false
                    Toast.makeText(context, "Features are back on", Toast.LENGTH_SHORT).show()
                },
                style = NbButtonStyle.Primary,
                size = NbButtonSize.Small,
            )
        }
    }
}

// ---------------------------------------------------------------- Settings › About row

/** "Last crash report" row; renders nothing when there is no saved report. */
@Composable
fun LastCrashReportSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<CrashReport?>(null) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        report = withContext(Dispatchers.IO) { CrashReportStore.read(context.applicationContext) }
    }
    val r = report ?: return

    Spacer(Modifier.height(4.dp))
    SettingsItem(
        title = "Last crash report",
        subtitle = listOf(r.exceptionShortType, r.time.substringBeforeLast(' ').ifBlank { null })
            .filterNotNull().joinToString(" · "),
        leadingIcon = { Icon(Icons.Rounded.BugReport, null, tint = MaterialTheme.colorScheme.secondary) },
        trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = { open = true },
    )
    if (open) {
        CrashReportFullScreen(
            report = r,
            onDismiss = { open = false },
            fromSettings = true,
            onClear = {
                open = false
                scope.launch {
                    withContext(Dispatchers.IO) { CrashReportStore.clear(context.applicationContext) }
                    report = null
                }
            },
        )
    }
}
