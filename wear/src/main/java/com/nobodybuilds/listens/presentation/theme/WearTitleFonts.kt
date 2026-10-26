package com.nobodybuilds.listens.presentation.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material.Typography
import com.nobodybuilds.listens.R

// Nobody Builds type, as on the phone (app ui/theme/Type.kt): Gilroy's open weights for display and
// headings (free-font EULA in licenses/), Google Sans Flex for all other text.

/** Gilroy: 600 and up resolve to ExtraBold, 500 and below to Light. */
val WearTitleFont = FontFamily(
    Font(R.font.gilroy_light, FontWeight.Light),
    Font(R.font.gilroy_extrabold, FontWeight.ExtraBold),
)

@OptIn(ExperimentalTextApi::class)
private fun flex(weight: FontWeight) = Font(
    resId = R.font.gflex_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Google Sans Flex: body, labels, buttons. */
val WearTextFont = FontFamily(
    flex(FontWeight.Normal),
    flex(FontWeight.Medium),
    flex(FontWeight.SemiBold),
    flex(FontWeight.Bold),
)

private fun TextStyle.heading() = copy(fontFamily = WearTitleFont, fontWeight = FontWeight.ExtraBold)

/** Every Text gets Flex; display and title styles get Gilroy ExtraBold. */
val WearTypography: Typography = Typography(defaultFontFamily = WearTextFont).let { type ->
    type.copy(
        display1 = type.display1.heading(),
        display2 = type.display2.heading(),
        display3 = type.display3.heading(),
        title1 = type.title1.heading(),
        title2 = type.title2.heading(),
        title3 = type.title3.heading(),
    )
}

/** Titles of the browse sub-screens (artists, albums, playlists…). */
fun rememberBrowseSubscreenTitleFont(): FontFamily = WearTitleFont
