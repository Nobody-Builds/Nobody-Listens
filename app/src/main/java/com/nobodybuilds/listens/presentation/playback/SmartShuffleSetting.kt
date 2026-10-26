package com.nobodybuilds.listens.presentation.playback

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.playback.SmartShuffleController
import com.nobodybuilds.listens.presentation.screens.SwitchSettingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmartShuffleSettingViewModel @Inject constructor(
    private val controller: SmartShuffleController,
) : ViewModel() {
    val enabled: StateFlow<Boolean> = controller.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { controller.setEnabled(enabled) }
    }
}

/** "Smart shuffle" switch for Settings › Playback. */
@Composable
fun SmartShuffleSetting(viewModel: SmartShuffleSettingViewModel = hiltViewModel()) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    SwitchSettingItem(
        title = "Smart shuffle",
        subtitle = "Plays recent songs later and spreads out artists",
        checked = enabled,
        onCheckedChange = viewModel::setEnabled,
        leadingIcon = { Icon(painterResource(R.drawable.rounded_shuffle_24), null, tint = MaterialTheme.colorScheme.secondary) }
    )
}
