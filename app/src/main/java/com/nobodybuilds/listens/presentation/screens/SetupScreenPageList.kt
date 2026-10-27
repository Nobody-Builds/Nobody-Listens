package com.nobodybuilds.listens.presentation.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.nobodybuilds.listens.presentation.viewmodel.SetupUiState

sealed class SetupPage {
    object Welcome : SetupPage()
    object MediaPermission : SetupPage()
    object BackupRestore : SetupPage()
    object DirectorySelection : SetupPage()
    object ThemeSelection : SetupPage()
    object NotificationsPermission : SetupPage()
    object AlarmsPermission : SetupPage()
    object LibraryLayout : SetupPage()
    object NavBarLayout : SetupPage()
    object BatteryOptimization : SetupPage()
    object Finish : SetupPage()
}

internal fun buildSetupPages(sdkInt: Int): List<SetupPage> {
    val pages = mutableListOf<SetupPage>(
        SetupPage.Welcome,
        SetupPage.MediaPermission
    )

    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
        pages += SetupPage.NotificationsPermission
    }

    pages += SetupPage.BackupRestore
    pages += SetupPage.DirectorySelection
    pages += SetupPage.ThemeSelection
    pages += SetupPage.LibraryLayout
    pages += SetupPage.NavBarLayout

    if (sdkInt >= Build.VERSION_CODES.S) {
        pages += SetupPage.AlarmsPermission
    }

    pages += SetupPage.BatteryOptimization
    pages += SetupPage.Finish
    return pages
}

internal fun firstBlockedForwardPageIndex(
    pages: List<SetupPage>,
    fromPageIndex: Int,
    toPageIndex: Int,
    context: Context,
    uiState: SetupUiState
): Int? {
    if (toPageIndex <= fromPageIndex) return null
    return (fromPageIndex until toPageIndex).firstOrNull { pageIndex ->
        !isPermissionGateSatisfied(context, pages[pageIndex], uiState)
    }
}

internal fun isPermissionGateSatisfied(
    context: Context,
    page: SetupPage,
    uiState: SetupUiState
): Boolean {
    return when (page) {
        SetupPage.MediaPermission -> {
            uiState.mediaPermissionGranted || hasMediaPermissionNow(context)
        }
        SetupPage.NotificationsPermission -> {
            uiState.notificationsPermissionGranted ||
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        }
        else -> true
    }
}

internal fun allRequiredPermissionsGrantedNow(context: Context): Boolean {
    val mediaGranted = hasMediaPermissionNow(context)
    val notificationsGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    return mediaGranted && notificationsGranted
}

private fun hasMediaPermissionNow(context: Context): Boolean {
    val mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, mediaPermission) == PackageManager.PERMISSION_GRANTED
}

private fun hasExactAlarmPermissionNow(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    return alarmManager.canScheduleExactAlarms()
}

internal fun isIgnoringBatteryOptimizationsNow(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
