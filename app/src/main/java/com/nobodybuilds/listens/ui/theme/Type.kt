package com.nobodybuilds.listens.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nobodybuilds.listens.R

// Daylight type: Gilroy's open weights (Light 300, ExtraBold 800 — licenses/Gilroy-Free-Font-EULA.txt)
// for display and headings, Google Sans Flex for body and interface text, Unbounded for one
// short accent phrase per layout.

/** Gilroy, open weights only. 600 and up resolve to ExtraBold, 500 and below to Light. */
val GilroyFamily = FontFamily(
    Font(R.font.gilroy_light, FontWeight.Light),
    Font(R.font.gilroy_extrabold, FontWeight.ExtraBold),
)

@OptIn(ExperimentalTextApi::class)
private fun flex(weight: FontWeight) = Font(
    resId = R.font.gflex_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/** Google Sans Flex — body and interface text. */
val NbBody = FontFamily(
    flex(FontWeight.Light),
    flex(FontWeight.Normal),
    flex(FontWeight.Medium),
    flex(FontWeight.SemiBold),
    flex(FontWeight.Bold),
)

/** Gilroy — display and headings. */
val NbDisplay = GilroyFamily

@OptIn(ExperimentalTextApi::class)
val UnboundedFamily = FontFamily(
    Font(
        resId = R.font.unbounded_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    )
)

// The app references `GoogleSansRounded` in hundreds of places, so that name is kept:
// text weights map to Google Sans Flex and bold-and-up weights to Gilroy ExtraBold, so
// every bold title picks up the Daylight display face.
val GoogleSansRounded = FontFamily(
    flex(FontWeight.Light),
    flex(FontWeight.Normal),
    flex(FontWeight.Medium),
    flex(FontWeight.SemiBold),
    Font(R.font.gilroy_extrabold, FontWeight.Bold),
    Font(R.font.gilroy_extrabold, FontWeight.ExtraBold),
    Font(R.font.gilroy_extrabold, FontWeight.Black),
)

/** Older expressive titles asked for Montserrat; they now use Gilroy. */
val MontserratFamily = GilroyFamily

private val HeadingTracking = (-0.025).em

val ExpTitleTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GilroyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 56.sp,
        letterSpacing = HeadingTracking,
        lineHeight = 1.0.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    displayMedium = TextStyle(
        fontFamily = GilroyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        letterSpacing = HeadingTracking,
        lineHeight = 1.0.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    ),
    titleMedium = TextStyle(
        fontFamily = GilroyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        letterSpacing = HeadingTracking,
        lineHeight = 1.0.em,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )
)

// Material 3 type scale at mobile sizes. Display/headline/titleLarge in Gilroy ExtraBold with
// −0.025em tracking; everything else in Google Sans Flex.
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = HeadingTracking),
    displayMedium = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = HeadingTracking),
    displaySmall = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = HeadingTracking),
    headlineLarge = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = HeadingTracking),
    headlineMedium = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = HeadingTracking),
    headlineSmall = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = HeadingTracking),
    titleLarge = TextStyle(fontFamily = NbDisplay, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = HeadingTracking),
    titleMedium = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = NbBody, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp)
)
