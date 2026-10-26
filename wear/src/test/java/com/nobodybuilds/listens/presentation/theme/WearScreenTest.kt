package com.nobodybuilds.listens.presentation.theme

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WearScreenTest {

    @Test
    fun `round screens inset to the inscribed square, square screens barely inset`() {
        assertEquals(28.1f, WearScreen(isRound = true, widthDp = 192, heightDp = 192).contentInset.value, 0.1f)
        assertEquals(33.4f, WearScreen(isRound = true, widthDp = 228, heightDp = 228).contentInset.value, 0.1f)
        assertEquals(12.dp, WearScreen(isRound = false, widthDp = 198, heightDp = 228).contentInset)
    }

    @Test
    fun `controls grow from the 192 dp design size and stop at 1_1x`() {
        assertEquals(1f, WearScreen(isRound = true, widthDp = 170, heightDp = 170).controlScale)
        assertEquals(1f, WearScreen(isRound = true, widthDp = 192, heightDp = 192).controlScale)
        assertEquals(204f / 192f, WearScreen(isRound = true, widthDp = 204, heightDp = 204).controlScale, 0.001f)
        // Galaxy Watch5 Pro: 450 px at 320 dpi = 225 dp, capped.
        assertEquals(1.1f, WearScreen(isRound = true, widthDp = 225, heightDp = 225).controlScale)
        // A tall square watch scales by its short side.
        assertEquals(1f, WearScreen(isRound = false, widthDp = 184, heightDp = 224).controlScale)
        assertEquals(66f, WearScreen(isRound = true, widthDp = 225, heightDp = 225).scaled(60.dp).value, 0.01f)
    }
}
