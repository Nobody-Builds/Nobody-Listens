package com.twilighty.lmusic.presentation.party

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.twilighty.lmusic.data.party.PartyHostState
import com.twilighty.lmusic.data.party.PartyRequest
import com.twilighty.lmusic.data.party.PartyStatus
import com.twilighty.lmusic.data.party.PartySummary
import com.twilighty.lmusic.presentation.nb.Nb
import com.twilighty.lmusic.presentation.nb.NbArt
import com.twilighty.lmusic.presentation.nb.NbButton
import com.twilighty.lmusic.presentation.nb.NbButtonSize
import com.twilighty.lmusic.presentation.nb.NbButtonStyle
import com.twilighty.lmusic.presentation.nb.NbCallout
import com.twilighty.lmusic.presentation.nb.NbCard
import com.twilighty.lmusic.presentation.nb.NbIconButton
import com.twilighty.lmusic.presentation.nb.NbIconButtonStyle
import com.twilighty.lmusic.presentation.nb.NbOverline
import com.twilighty.lmusic.presentation.nb.NbSectionHeader
import com.twilighty.lmusic.presentation.nb.NbSegmented
import com.twilighty.lmusic.presentation.nb.NbSettingRow
import com.twilighty.lmusic.presentation.nb.NbShapes
import com.twilighty.lmusic.presentation.nb.NbSongRow
import com.twilighty.lmusic.presentation.nb.NbStatTile
import com.twilighty.lmusic.presentation.nb.NbStatus
import com.twilighty.lmusic.presentation.nb.NbStatusChip
import com.twilighty.lmusic.presentation.nb.NbSwitch
import com.twilighty.lmusic.presentation.nb.NbTile
import com.twilighty.lmusic.presentation.nb.NbTopBar
import com.twilighty.lmusic.presentation.nb.NbType
import kotlinx.coroutines.delay

private val DurationHours = listOf(1, 2, 4)

@Composable
fun PartyScreen(
    onBack: () -> Unit,
    bottomInset: Dp = 0.dp,
    viewModel: PartyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val batteryTip = com.twilighty.lmusic.presentation.nb.rememberBatteryTipState()
    Box(
        Modifier
            .fillMaxSize()
            .background(Nb.canvas)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = bottomInset + 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "top") {
                NbTopBar(
                    title = "Party queue",
                    kicker = when (state.status) {
                        PartyStatus.Running -> "Live on your Wi-Fi"
                        PartyStatus.Starting -> "Starting…"
                        PartyStatus.Idle -> "Friends add songs from your phone"
                    },
                    navigationIcon = {
                        NbIconButton(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            onClick = onBack
                        )
                    }
                )
            }
            if (batteryTip.visible) {
                item(key = "battery_tip") {
                    com.twilighty.lmusic.presentation.nb.BatteryOptimizationTip(batteryTip, Modifier.padding(horizontal = 16.dp))
                }
            }
            state.lastSummary?.let { summary ->
                item(key = "summary") {
                    SummaryCard(summary, onDismiss = viewModel::dismissSummary, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            state.error?.takeIf { state.status == PartyStatus.Idle }?.let { error ->
                item(key = "error") {
                    NbCallout(boldText = "Heads up.", text = error, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            when (state.status) {
                PartyStatus.Idle -> item(key = "setup") {
                    SetupCard(onStart = viewModel::start, modifier = Modifier.padding(horizontal = 16.dp))
                }
                PartyStatus.Starting -> item(key = "starting") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Nb.ink)
                    }
                }
                PartyStatus.Running -> runningContent(state, viewModel)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.runningContent(
    state: PartyHostState,
    viewModel: PartyViewModel,
) {
    item(key = "join") { JoinCard(state, viewModel, Modifier.padding(horizontal = 16.dp)) }
    item(key = "stats") {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(state.endsAt) {
            while (true) {
                now = System.currentTimeMillis()
                delay(30_000)
            }
        }
        val left = ((state.endsAt - now) / 60_000L).coerceAtLeast(0)
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NbStatTile(label = "Guests", value = state.guests.size.toString(), modifier = Modifier.weight(1f))
            NbStatTile(
                label = "Played",
                value = (state.summarySoFar?.songsPlayed ?: 0).toString(),
                supporting = "${state.upNext.size} up next",
                modifier = Modifier.weight(1f)
            )
            NbStatTile(
                label = "Ends in",
                value = if (left >= 60) "${left / 60}h ${left % 60}m" else "${left}m",
                modifier = Modifier.weight(1f)
            )
        }
    }
    item(key = "approve") {
        NbSettingRow(
            title = "Approve each request",
            subtitle = "Songs wait for your OK before they join the queue",
            trailing = { NbSwitch(checked = state.approveEach, onCheckedChange = viewModel::setApproveEach) },
            onClick = { viewModel.setApproveEach(!state.approveEach) },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }

    state.nowPlaying?.let { np ->
        item(key = "np") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                NbOverline("Now playing")
                NbSongRow(
                    title = np.title.ifBlank { "Nothing playing" },
                    subtitle = listOfNotNull(np.artist.ifBlank { null }, np.addedBy?.let { "added by $it" }).joinToString(" · "),
                    artModel = np.artUri,
                    artKey = np.album.ifBlank { np.title },
                    isCurrent = true
                )
            }
        }
    }

    if (state.awaiting.isNotEmpty()) {
        item(key = "awaiting_h") {
            NbSectionHeader(
                title = "Waiting for you · ${state.awaiting.size}",
                actionLabel = "Approve all",
                onAction = viewModel::approveAll,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        items(state.awaiting, key = { "aw_${it.id}" }) { r ->
            RequestRow(r, Modifier.padding(horizontal = 16.dp)) {
                NbIconButton(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove",
                    onClick = { viewModel.remove(r.id) },
                    style = NbIconButtonStyle.Outline,
                    size = 40.dp
                )
                NbIconButton(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Approve",
                    onClick = { viewModel.approve(r.id) },
                    style = NbIconButtonStyle.Accent,
                    size = 40.dp
                )
            }
        }
    }

    item(key = "next_h") {
        NbSectionHeader(title = "Up next", modifier = Modifier.padding(horizontal = 16.dp))
    }
    if (state.upNext.isEmpty()) {
        item(key = "next_empty") {
            Text(
                "No requests yet. Friends scan the code, pick a name and add songs.",
                style = NbType.meta,
                color = Nb.textMuted,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
    items(state.upNext, key = { "nx_${it.id}" }) { r ->
        RequestRow(r, Modifier.padding(horizontal = 16.dp)) {
            if (r.status == com.twilighty.lmusic.data.party.PartyRequestStatus.Staged) {
                NbStatusChip("Next", NbStatus.Accent)
            }
            NbIconButton(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove",
                onClick = { viewModel.remove(r.id) },
                size = 40.dp
            )
        }
    }

    item(key = "guests_h") {
        NbSectionHeader(title = "Guests · ${state.guests.size}", modifier = Modifier.padding(horizontal = 16.dp))
    }
    items(state.guests, key = { "g_${it.id}" }) { g ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NbArt(model = null, title = g.name, size = 40.dp, shape = CircleShape)
            val added = state.upNext.count { it.guestId == g.id } + state.awaiting.count { it.guestId == g.id }
            Column(Modifier.weight(1f)) {
                Text(g.name, style = NbType.title, color = Nb.text)
                Text(if (added == 1) "1 song waiting" else "$added songs waiting", style = NbType.caption, color = Nb.textMuted)
            }
            NbIconButton(
                imageVector = Icons.Rounded.PersonRemove,
                contentDescription = "Remove ${g.name}",
                onClick = { viewModel.kick(g.id) },
                size = 40.dp
            )
        }
    }

    item(key = "end") {
        NbButton(
            text = "End party",
            onClick = viewModel::end,
            style = NbButtonStyle.Danger,
            size = NbButtonSize.Large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun RequestRow(
    r: PartyRequest,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    NbSongRow(
        title = r.song.title,
        subtitle = "${r.song.artist} · ${r.guestName}",
        artKey = r.song.album.ifBlank { r.song.title },
        modifier = modifier,
        trailing = {
            if (r.votes > 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(rememberVectorPainter(Icons.Rounded.ThumbUp), null, tint = Nb.textMuted, modifier = Modifier.size(14.dp))
                    Text(" ${r.votes}", style = NbType.caption, color = Nb.textMuted)
                }
            }
            actions()
        }
    )
}

@Composable
private fun JoinCard(state: PartyHostState, viewModel: PartyViewModel, modifier: Modifier = Modifier) {
    val joinUrl = state.joinUrl ?: return
    val qr = remember(joinUrl) { viewModel.qrFor(joinUrl) }
    NbCard(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            NbOverline("Scan to join")
            Spacer(Modifier.height(12.dp))
            if (qr != null) {
                Box(
                    Modifier
                        .widthIn(max = 280.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(NbShapes.lg)
                        .background(Color.White)
                        .padding(8.dp)
                ) {
                    Image(
                        bitmap = qr,
                        contentDescription = "QR code for the party link",
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("PIN", style = NbType.caption, color = Nb.textMuted)
            Text(
                state.pin.orEmpty().chunked(3).joinToString(" "),
                style = NbType.display(40.sp),
                color = Nb.text
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Or open ${state.baseUrl.orEmpty()} and enter the PIN",
                style = NbType.meta,
                color = Nb.textMuted,
                textAlign = TextAlign.Center
            )
            state.tvUrl?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Big screen: open ${state.baseUrl}/tv on a laptop or TV",
                    style = NbType.caption,
                    color = Nb.textMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SetupCard(onStart: (Int, Boolean) -> Unit, modifier: Modifier = Modifier) {
    var durationIndex by rememberSaveable { mutableIntStateOf(1) }
    var approve by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NbTile(background = Nb.accentSoft) {
            Text("Let friends pick the music", style = NbType.h2, color = Nb.text)
            Spacer(Modifier.height(6.dp))
            Text(
                "Friends on the same Wi-Fi scan a code, pick a name, search your library and add songs. " +
                    "Votes decide what plays first. Nothing leaves your network, and it stops when the party ends.",
                style = NbType.body,
                color = Nb.textSecondary
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Party length", style = NbType.label, color = Nb.text)
            NbSegmented(
                options = DurationHours.map { "$it h" },
                selectedIndex = durationIndex,
                onSelect = { durationIndex = it },
                modifier = Modifier.fillMaxWidth()
            )
        }
        NbSettingRow(
            title = "Approve each request",
            subtitle = "Check every song before it joins the queue",
            trailing = { NbSwitch(checked = approve, onCheckedChange = { approve = it }) },
            onClick = { approve = !approve }
        )
        NbButton(
            text = "Start party",
            onClick = { onStart(DurationHours[durationIndex], approve) },
            style = NbButtonStyle.Accent,
            size = NbButtonSize.Large,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Guests can add one song every 10 s, up to 5 waiting at a time.",
            style = NbType.caption,
            color = Nb.textMuted
        )
    }
}

@Composable
private fun SummaryCard(summary: PartySummary, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    NbTile(background = Nb.accent, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("PARTY'S OVER", style = NbType.overline, color = Nb.onAccent.copy(alpha = 0.8f))
                Spacer(Modifier.height(4.dp))
                val songs = if (summary.songsPlayed == 1) "1 song" else "${summary.songsPlayed} songs"
                val friends = if (summary.contributors == 1) "1 friend" else "${summary.contributors} friends"
                Text("$songs from $friends", style = NbType.display(30.sp), color = Nb.onAccent)
                Spacer(Modifier.height(6.dp))
                Text(
                    summary.topContributor?.let { "Top pick-maker: $it (${summary.topContributorSongs})" }
                        ?: "${summary.guests} joined · ${summary.songsRequested} requested",
                    style = NbType.meta.copy(fontWeight = FontWeight.SemiBold),
                    color = Nb.onAccent.copy(alpha = 0.9f)
                )
            }
            NbIconButton(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Dismiss",
                onClick = onDismiss,
                tint = Nb.onAccent
            )
        }
    }
}
