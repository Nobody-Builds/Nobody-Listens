package com.twilighty.lmusic.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.twilighty.lmusic.BuildConfig
import com.twilighty.lmusic.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Asks GitHub for the latest LMusic release. Never downloads APKs; it only links to the release page. */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    private val preferences: UpdatePreferences,
) {
    private val client = okHttpClient.newBuilder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun check(notify: Boolean): UpdateCheckResult = withContext(Dispatchers.IO) {
        val release = try {
            fetchLatestRelease()
        } catch (e: Exception) {
            Timber.w(e, "Update check failed")
            return@withContext UpdateCheckResult.Failed(e.message ?: "Network error")
        } ?: return@withContext UpdateCheckResult.Failed("No release found")

        preferences.saveResult(release, System.currentTimeMillis())

        if (!VersionComparator.isNewer(release.version, BuildConfig.VERSION_NAME)) {
            return@withContext UpdateCheckResult.UpToDate(release.version)
        }
        val skipped = preferences.state.first().skippedVersion
        if (notify && !release.version.equals(skipped, ignoreCase = true)) {
            UpdateNotifier.notify(context, release)
        }
        UpdateCheckResult.UpdateAvailable(release)
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("GitHub returned ${response.code}")
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
            val url = json.optString("html_url").takeIf { it.isNotBlank() } ?: RELEASES_PAGE_URL
            val notes = json.optString("body")
                .lineSequence()
                .map { it.trim().trimStart('#', '*', '-', ' ').trim() }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
            return ReleaseInfo(version = VersionComparator.normalize(tag), url = url, notesFirstLine = notes)
        }
    }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/Nobody-Builds/Nobody-Listens/releases/latest"
        const val RELEASES_PAGE_URL = "https://github.com/Nobody-Builds/Nobody-Listens/releases"
    }
}

/** Posts the "update available" notification on the "App updates" channel. */
object UpdateNotifier {
    const val CHANNEL_ID = "lmusic_updates"
    private const val NOTIFICATION_ID = 7301

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when a new Nobody Listens version is out"
                }
            )
        }
    }

    fun openReleaseIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notify(context: Context, release: ReleaseInfo) {
        // Without the permission we stay silent; the result is still stored for the Home banner.
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        ensureChannel(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openReleaseIntent(release.url),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = release.notesFirstLine.ifBlank { "Tap to see what's new." }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.twilighty.lmusic.shared.R.drawable.nl_mark_mono)
            .setContentTitle("Nobody Listens ${release.version} is available")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .addAction(0, "Download", pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
            .onFailure { Timber.w(it, "Could not post update notification") }
    }
}
