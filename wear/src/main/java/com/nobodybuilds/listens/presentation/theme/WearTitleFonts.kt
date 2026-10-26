package com.nobodybuilds.listens.presentation.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.nobodybuilds.listens.R

/** Gilroy ExtraBold: the Nobody Builds heading face, as on the phone (free-font EULA in licenses/). */
val WearTitleFont = FontFamily(Font(R.font.gilroy_extrabold))

/** Titles of the browse sub-screens (artists, albums, playlists…). */
fun rememberBrowseSubscreenTitleFont(): FontFamily = WearTitleFont
