package com.twilighty.lmusic.presentation.nb

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/** [NbArt] that fills the available width as a square (grid cells, search results). */
@Composable
fun NbArtSquare(
    model: Any?,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = NbShapes.lg,
    contentDescription: String? = null,
) {
    BoxWithConstraints(modifier.aspectRatio(1f)) {
        NbArt(
            model = model,
            title = title,
            size = maxWidth,
            shape = shape,
            contentDescription = contentDescription
        )
    }
}
