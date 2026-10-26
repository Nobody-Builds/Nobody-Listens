package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.components.ExpressiveTopBarContent
import androidx.compose.ui.res.stringResource

// --- Top Bar Component ---
@Composable
fun GenreCollapsibleTopBar(
    title: String,
    collapseFraction: Float,
    headerHeight: Dp,
    onBackPressed: () -> Unit,
    startColor: Color,
    containerColor: Color,
    contentColor: Color,
    collapsedContentColor: Color
) {
    val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
    val animatedContentColor = androidx.compose.ui.graphics.lerp(
        start = contentColor,
        stop = collapsedContentColor,
        fraction = solidAlpha
    )

    // Optimization: Pre-calculate alpha values
    val gradientAlpha = 0.8f * (1f - solidAlpha)
    
    // Optimization: Reuse gradient brush to avoid allocation on every pixel scroll
    val verticalGradient = remember(startColor, gradientAlpha) {
        Brush.verticalGradient(
            colors = listOf(
                startColor.copy(alpha = gradientAlpha),
                startColor.copy(alpha = 0f)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .zIndex(5f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(containerColor.copy(alpha = solidAlpha)) 
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(verticalGradient)
        )

        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
             FilledIconButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 12.dp, top = 4.dp)
                    .zIndex(10f),
                onClick = onBackPressed,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = animatedContentColor.copy(alpha = 0.1f),
                    contentColor = animatedContentColor
                )
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.auth_cd_back), tint = animatedContentColor)
            }

            ExpressiveTopBarContent(
                title = title,
                collapseFraction = collapseFraction,
                modifier = Modifier.fillMaxSize(),
                collapsedTitleStartPadding = 68.dp,
                expandedTitleStartPadding = 20.dp,
                maxLines = 1,
                contentColor = animatedContentColor
            )
        }
    }
}
