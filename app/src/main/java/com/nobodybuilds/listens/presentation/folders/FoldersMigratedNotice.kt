package com.nobodybuilds.listens.presentation.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.data.library.MusicFoldersNoticePreferences
import com.nobodybuilds.listens.presentation.nb.Nb
import com.nobodybuilds.listens.presentation.nb.NbButton
import com.nobodybuilds.listens.presentation.nb.NbButtonSize
import com.nobodybuilds.listens.presentation.nb.NbButtonStyle
import com.nobodybuilds.listens.presentation.nb.NbIconButton
import com.nobodybuilds.listens.presentation.nb.NbShapes
import com.nobodybuilds.listens.presentation.nb.NbType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FoldersMigratedNoticeViewModel @Inject constructor(
    private val preferences: MusicFoldersNoticePreferences,
) : ViewModel() {
    val visible: StateFlow<Boolean> = preferences.showFoldersMigratedNotice
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismiss() {
        viewModelScope.launch { preferences.setShowFoldersMigratedNotice(false) }
    }
}

/** True when Home should show [FoldersMigratedNotice]. */
@Composable
fun rememberFoldersMigratedNoticeVisible(viewModel: FoldersMigratedNoticeViewModel = hiltViewModel()): Boolean {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    return visible
}

/** One-time Home callout after the music folders were set up from the existing library. */
@Composable
fun FoldersMigratedNotice(
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FoldersMigratedNoticeViewModel = hiltViewModel(),
) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    if (!visible) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Nb.warningSoft, NbShapes.lg)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.Folder, contentDescription = null, tint = Nb.warningText, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.folders_migrated_notice),
            style = NbType.meta,
            color = Nb.warningText,
            modifier = Modifier.weight(1f)
        )
        NbButton(
            text = stringResource(R.string.folders_migrated_review),
            onClick = {
                viewModel.dismiss()
                onReview()
            },
            style = NbButtonStyle.Secondary,
            size = NbButtonSize.Small
        )
        NbIconButton(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.folders_migrated_dismiss),
            onClick = viewModel::dismiss,
            size = 36.dp,
            iconSize = 18.dp
        )
    }
}
