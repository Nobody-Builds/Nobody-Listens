package com.nobodybuilds.listens.presentation.party

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import com.nobodybuilds.listens.data.party.PartyHostState
import com.nobodybuilds.listens.data.party.PartyManager
import com.nobodybuilds.listens.data.party.PartyQr
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PartyViewModel @Inject constructor(
    private val manager: PartyManager,
) : ViewModel() {

    val state: StateFlow<PartyHostState> = manager.state

    private var qrFor: String? = null
    private var qrBitmap: ImageBitmap? = null

    fun start(durationHours: Int, approveEach: Boolean) =
        manager.start(durationMs = durationHours * 3_600_000L, approveEach = approveEach)

    fun end() = manager.end()
    fun approve(id: String) = manager.approve(id)
    fun approveAll() = manager.approveAll()
    fun remove(id: String) = manager.remove(id)
    fun kick(guestId: String) = manager.kick(guestId)
    fun setApproveEach(enabled: Boolean) = manager.setApproveEach(enabled)
    fun dismissSummary() = manager.dismissSummary()

    /** QR of the join link, encoded locally and cached per URL. */
    fun qrFor(url: String): ImageBitmap? {
        if (url == qrFor) return qrBitmap
        val bitmap = runCatching {
            val (w, h, px) = PartyQr.pixels(url, scale = 12, dark = 0xFF111113.toInt(), light = 0xFFFFFFFF.toInt())
            Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull()
        qrFor = url
        qrBitmap = bitmap
        return bitmap
    }
}
