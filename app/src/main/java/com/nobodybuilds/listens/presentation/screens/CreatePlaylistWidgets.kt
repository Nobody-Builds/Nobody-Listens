@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Slider
import com.nobodybuilds.listens.utils.resolvePlaylistCoverContentColor
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SliderDefaults

fun getIconByName(name: String?): ImageVector? {
    return when (name) {
        "MusicNote" -> Icons.Rounded.MusicNote
        "Headphones" -> Icons.Rounded.Headphones
        "Album" -> Icons.Rounded.Album
        "Mic" -> Icons.Rounded.MicExternalOn 
        "Speaker" -> Icons.Rounded.Speaker
        "Favorite" -> Icons.Rounded.Favorite
        "Piano" -> Icons.Rounded.Piano
        "Queue" -> Icons.AutoMirrored.Rounded.QueueMusic
        else -> null
    }
}

@Composable
fun ExpressiveButtonGroup(
    items: List<String>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Transparent, CircleShape), // Optional container background
            //.padding(1.dp), // Check if padding is needed
        horizontalArrangement = Arrangement.spacedBy(4.dp) // Gap between buttons
    ) {
        items.forEachIndexed { index, title ->
            val isSelected = selectedIndex == index
            val shape = if (isSelected) CircleShape else RoundedCornerShape(10.dp) // Pill vs RoundedRect
            val containerColor by androidx.compose.animation.animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "ButtonColor"
            )
            val contentColor by androidx.compose.animation.animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                label = "ContentColor"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp) // Taller button
                    .clip(shape)
                    .background(containerColor)
                    .clickable { onItemClick(index) },
                contentAlignment = Alignment.Center
            ) {
                 Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                     androidx.compose.animation.AnimatedVisibility(visible = isSelected) {
                         Icon(
                             Icons.Rounded.Check, 
                             contentDescription = null, 
                             modifier = Modifier.padding(end = 8.dp).size(18.dp),
                             tint = contentColor
                         )
                     }
                     Text(
                         text = title,
                         style = MaterialTheme.typography.labelLarge,
                         color = contentColor,
                         fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Medium
                     )
                 }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShapeParameterCard(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueDisplay: (Float) -> String,
    steps: Int = 0
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                     text = title,
                     style = MaterialTheme.typography.labelMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                     text = valueDisplay(value),
                     style = MaterialTheme.typography.labelLarge,
                     color = MaterialTheme.colorScheme.onSurface,
                     fontWeight = FontWeight.Bold
                )
            }
            
            // Custom Thick Slider
            ThickSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps
            )
        }
    }
}

@Composable
fun ThickSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
             thumbColor = MaterialTheme.colorScheme.primary,
             activeTrackColor = MaterialTheme.colorScheme.primary,
             inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        thumb = {
             Box(
                 modifier = Modifier
                    .size(width = 6.dp, height = 18.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
             )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier.height(8.dp),
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 4.dp,
                drawStopIndicator = null
            )
        }
    )
}


fun getThemeContentColor(colorArgb: Int, scheme: androidx.compose.material3.ColorScheme): Color {
    return resolvePlaylistCoverContentColor(colorArgb, scheme)
}

// End of file
