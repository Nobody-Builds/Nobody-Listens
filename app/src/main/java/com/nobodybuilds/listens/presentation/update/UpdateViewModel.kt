package com.nobodybuilds.listens.presentation.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.data.update.UpdateCheckResult
import com.nobodybuilds.listens.data.update.UpdateChecker
import com.nobodybuilds.listens.data.update.UpdatePreferences
import com.nobodybuilds.listens.data.update.UpdateScheduler
import com.nobodybuilds.listens.data.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val preferences: UpdatePreferences,
    private val checker: UpdateChecker,
    private val scheduler: UpdateScheduler,
) : ViewModel() {

    val state: StateFlow<UpdateState> = preferences.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdateState())

    val checksEnabled: StateFlow<Boolean> = preferences.checksEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun setChecksEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setChecksEnabled(enabled)
            scheduler.apply(enabled)
        }
    }

    fun checkNow() {
        if (_isChecking.value) return
        _isChecking.value = true
        viewModelScope.launch {
            val message = when (val result = checker.check(notify = false)) {
                is UpdateCheckResult.UpdateAvailable -> "Nobody Listens ${result.release.version} is available"
                is UpdateCheckResult.UpToDate -> "You're on the latest version"
                is UpdateCheckResult.Failed -> "Couldn't check for updates"
            }
            _isChecking.value = false
            _messages.tryEmit(message)
        }
    }

    fun skipVersion(version: String) {
        viewModelScope.launch { preferences.skipVersion(version) }
    }

    fun dismissBanner(version: String) {
        viewModelScope.launch { preferences.dismissBanner(version) }
    }
}
