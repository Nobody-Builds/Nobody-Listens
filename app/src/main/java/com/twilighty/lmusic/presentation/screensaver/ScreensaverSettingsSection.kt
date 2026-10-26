package com.twilighty.lmusic.presentation.screensaver

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.twilighty.lmusic.data.screensaver.BurnIn
import com.twilighty.lmusic.data.screensaver.GeoPlace
import com.twilighty.lmusic.data.screensaver.ScreensaverClockStyle
import com.twilighty.lmusic.data.screensaver.ScreensaverPreferences
import com.twilighty.lmusic.data.screensaver.ScreensaverSettings
import com.twilighty.lmusic.data.screensaver.ScreensaverWeatherRepository
import com.twilighty.lmusic.presentation.screens.SettingsItem
import com.twilighty.lmusic.presentation.screens.SwitchSettingItem
import com.twilighty.lmusic.presentation.screens.ThemeSelectorItem
import com.twilighty.lmusic.presentation.nb.NbButton
import com.twilighty.lmusic.presentation.nb.NbButtonSize
import com.twilighty.lmusic.presentation.nb.NbButtonStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScreensaverSettingsViewModel @Inject constructor(
    private val preferences: ScreensaverPreferences,
    private val weatherRepository: ScreensaverWeatherRepository,
) : ViewModel() {
    val settings: StateFlow<ScreensaverSettings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreensaverSettings())

    private val _results = MutableStateFlow<List<GeoPlace>>(emptyList())
    val results: StateFlow<List<GeoPlace>> = _results
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching
    private val _searchMessage = MutableStateFlow<String?>(null)
    val searchMessage: StateFlow<String?> = _searchMessage

    fun hasLocationPermission() = weatherRepository.hasLocationPermission()

    fun setOffer(v: Boolean) = viewModelScope.launch { preferences.setOfferWhenCharging(v) }
    fun setShowWeather(v: Boolean) = viewModelScope.launch { preferences.setShowWeather(v) }
    fun setUseDeviceLocation(v: Boolean) = viewModelScope.launch { preferences.setUseDeviceLocation(v) }
    fun setClockStyle(v: ScreensaverClockStyle) = viewModelScope.launch { preferences.setClockStyle(v) }
    fun setShiftInterval(sec: Int) = viewModelScope.launch { preferences.setShiftIntervalSec(sec) }
    fun setShowArtwork(v: Boolean) = viewModelScope.launch { preferences.setShowArtwork(v) }

    fun search(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _searching.value = true
            _searchMessage.value = null
            val found = runCatching { weatherRepository.searchCity(name) }.getOrDefault(emptyList())
            _results.value = found
            _searchMessage.value = if (found.isEmpty()) "No places found. Check the spelling or your connection." else null
            _searching.value = false
        }
    }

    fun pick(place: GeoPlace) {
        viewModelScope.launch {
            preferences.setCity(place)
            _results.value = emptyList()
        }
    }
}

/** Settings › Appearance › Screensaver. */
@Composable
fun ScreensaverSettingsSection() {
    val context = LocalContext.current
    val vm: ScreensaverSettingsViewModel = hiltViewModel()
    val s by vm.settings.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val searchMessage by vm.searchMessage.collectAsStateWithLifecycle()
    var cityQuery by remember { mutableStateOf("") }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setUseDeviceLocation(granted)
        permissionDenied = !granted
    }
    val tint = MaterialTheme.colorScheme.secondary

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsItem(
            title = "Start screensaver",
            subtitle = "Clock, weather and now playing on a dim black screen",
            leadingIcon = { Icon(Icons.Rounded.Bedtime, null, tint = tint) },
            trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = { ScreensaverActivity.start(context) }
        )
        SwitchSettingItem(
            title = "Offer screensaver when charging in landscape",
            subtitle = "Asks when music plays and the phone is on its charger, sideways",
            checked = s.offerWhenCharging,
            onCheckedChange = { vm.setOffer(it) },
            leadingIcon = { Icon(Icons.Rounded.ScreenRotation, null, tint = tint) }
        )
        ThemeSelectorItem(
            label = "Clock style",
            description = "How the time looks on the screensaver",
            options = ScreensaverClockStyle.entries.associate { it.key to it.label },
            selectedKey = s.clockStyle.key,
            onSelectionChanged = { vm.setClockStyle(ScreensaverClockStyle.fromKey(it)) },
            leadingIcon = { Icon(Icons.Rounded.Schedule, null, tint = tint) }
        )
        SwitchSettingItem(
            title = "Show album art",
            subtitle = "Small and dimmed, large clock only",
            checked = s.showArtwork,
            onCheckedChange = { vm.setShowArtwork(it) },
            leadingIcon = { Icon(Icons.Rounded.Image, null, tint = tint) }
        )
        ThemeSelectorItem(
            label = "Burn-in protection",
            description = "Always on. The layout shifts a few pixels and swaps sides every 10 minutes.",
            options = BurnIn.SHIFT_INTERVAL_CHOICES_SEC.associate { sec ->
                sec.toString() to when (sec) {
                    30 -> "Shift every 30 seconds"
                    60 -> "Shift every minute"
                    else -> "Shift every ${sec / 60} minutes"
                }
            },
            selectedKey = s.shiftIntervalSec.toString(),
            onSelectionChanged = { vm.setShiftInterval(it.toIntOrNull() ?: 60) },
            leadingIcon = { Icon(Icons.Rounded.Shield, null, tint = tint) }
        )
        SwitchSettingItem(
            title = "Show weather",
            subtitle = "From Open-Meteo, refreshed every 30 minutes while the screensaver is on",
            checked = s.showWeather,
            onCheckedChange = { vm.setShowWeather(it) },
            leadingIcon = { Icon(Icons.Rounded.Cloud, null, tint = tint) }
        )
        if (s.showWeather) {
            SwitchSettingItem(
                title = "Use my approximate location",
                subtitle = when {
                    permissionDenied -> "Location permission denied. Pick a city below instead."
                    else -> "Coarse location only, read when the screensaver opens"
                },
                checked = s.location.useDeviceLocation && vm.hasLocationPermission(),
                onCheckedChange = { on ->
                    if (!on) vm.setUseDeviceLocation(false)
                    else if (vm.hasLocationPermission()) vm.setUseDeviceLocation(true)
                    else permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                leadingIcon = { Icon(Icons.Rounded.LocationOn, null, tint = tint) }
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("City", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        s.location.cityName?.let { "Current: $it" + if (s.location.useDeviceLocation) " (used when location is unavailable)" else "" }
                            ?: "Type a city to use instead of your location",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = cityQuery,
                            onValueChange = { cityQuery = it },
                            singleLine = true,
                            placeholder = { Text("e.g. Kochi") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { vm.search(cityQuery) }),
                            modifier = Modifier.weight(1f)
                        )
                        NbButton(
                            text = if (searching) "…" else "Search",
                            onClick = { vm.search(cityQuery) },
                            style = NbButtonStyle.Secondary,
                            size = NbButtonSize.Small,
                            enabled = !searching && cityQuery.isNotBlank()
                        )
                    }
                    searchMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    results.forEach { place ->
                        Text(
                            place.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    vm.pick(place)
                                    cityQuery = ""
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }
        SettingsItem(
            title = "System screen saver",
            subtitle = "Pick LMusic in Settings › Display › Screen saver",
            leadingIcon = { Icon(Icons.Rounded.Tv, null, tint = tint) },
            trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        )
    }
}
