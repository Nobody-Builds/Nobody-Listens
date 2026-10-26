package com.twilighty.lmusic.presentation.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.lmusic.data.update.UpdateNotifier
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbButton
import com.twilighty.lmusic.presentation.nb.NbButtonSize
import com.twilighty.lmusic.presentation.nb.NbButtonStyle
import com.twilighty.lmusic.presentation.nb.NbIconButton
import com.twilighty.lmusic.presentation.nb.NbShapes
import com.twilighty.lmusic.presentation.nb.NbType

/** Small dismissible "Update available" banner for Home. Renders nothing when there's no update. */
@Composable
fun UpdateBanner(
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.showBanner) return
    val version = state.availableVersion ?: return
    val url = state.url ?: return
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(Nb.accentSoft, NbShapes.lg)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = Nb.accentText, modifier = Modifier.size(20.dp))
        Text(
            "Update available · LMusic $version",
            style = NbType.label,
            color = Nb.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        NbButton(
            text = "Open",
            onClick = { runCatching { context.startActivity(UpdateNotifier.openReleaseIntent(url)) } },
            style = NbButtonStyle.Primary,
            size = NbButtonSize.Small
        )
        NbIconButton(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Dismiss",
            onClick = { viewModel.dismissBanner(version) },
            size = 36.dp,
            iconSize = 18.dp
        )
    }
}

/** True when Home should show [UpdateBanner] (lets Home skip the list item entirely). */
@Composable
fun rememberUpdateBannerVisible(viewModel: UpdateViewModel = hiltViewModel()): Boolean {
    val state by viewModel.state.collectAsStateWithLifecycle()
    return state.showBanner && state.url != null
}
