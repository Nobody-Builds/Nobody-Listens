package com.nobodybuilds.listens.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.viewmodel.SetupUiState
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NavBarLayoutPage(
    uiState: SetupUiState,
    onModeSelected: (String) -> Unit,
    onCustomizeRadius: () -> Unit,
    onSkip: () -> Unit
) {
    val isDefault = uiState.navBarStyle != "full_width" // Default or null is default

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.setup_app_navigation_title),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = GoogleSansRounded,
                    fontSize = 32.sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.setup_app_navigation_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Preview Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            NavBarPreview(isDefault = isDefault)
        }
        
        // Controls Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(24.dp),
                onClick = { onModeSelected(if (isDefault) "full_width" else "default") }
            ) {
                Column(
                    modifier = Modifier
                       .padding(horizontal = 20.dp, vertical = 16.dp)
                       .fillMaxWidth()
                ) {
                    Row(
                       modifier = Modifier.fillMaxWidth(),
                       verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.setup_navbar_default_style),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isDefault) stringResource(R.string.setup_nav_floating_pill_description) else stringResource(R.string.setup_nav_full_width_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isDefault,
                            onCheckedChange = { checked ->
                                onModeSelected(if (checked) "default" else "full_width")
                            }
                        )
                    }
                    
                    AnimatedVisibility(
                        visible = true, // Always visible now
                        enter =   androidx.compose.animation.expandVertically() + fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + fadeOut()
                    ) {
                         Column {
                             Spacer(modifier = Modifier.height(16.dp))
                             FilledTonalButton(
                                 onClick = onCustomizeRadius,
                                 modifier = Modifier.fillMaxWidth(),
                                 colors = ButtonDefaults.filledTonalButtonColors(
                                     containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                     contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                 )
                             ) {
                                 Icon(Icons.Rounded.RoundedCorner, contentDescription = null, modifier = Modifier.size(18.dp))
                                 Spacer(modifier = Modifier.width(8.dp))
                                 Text(stringResource(R.string.customize_corner_radius))
                             }
                         }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = stringResource(R.string.setup_navbar_footer),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun NavBarPreview(isDefault: Boolean) {
    val gradientColors = listOf(
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f), // Lighter top
        MaterialTheme.colorScheme.surfaceContainer, // Darker bottom
    )
    
    // Simulate the bottom of a screen
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp) // Taller to show bottom part clearly
            .padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Content placeholder
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                 // Fake content lines
                 repeat(3) {
                     Box(
                         modifier = Modifier
                             .fillMaxWidth(if(it==1) 0.7f else 1f)
                             .height(12.dp)
                             .clip(RoundedCornerShape(6.dp))
                             .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                     )
                 }
            }
            
            // Navbar
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                AnimatedContent(
                    targetState = isDefault,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(400)) + slideInVertically { it })
                            .togetherWith(fadeOut(animationSpec = tween(200)) + slideOutVertically { it })
                    },
                    label = "NavbarPreviewAnim"
                ) { default ->
                    if (default) {
                        // Default Pill Style
                         Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .height(80.dp),
                            shape = AbsoluteSmoothCornerShape(
                                cornerRadiusTL = 28.dp,
                                cornerRadiusTR = 28.dp,
                                cornerRadiusBL = 28.dp,
                                cornerRadiusBR = 28.dp,
                                smoothnessAsPercentTL = 60,
                                smoothnessAsPercentTR = 60,
                                smoothnessAsPercentBL = 60,
                                smoothnessAsPercentBR = 60
                            ),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(painterResource(R.drawable.rounded_home_24), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(painterResource(R.drawable.rounded_search_24), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(painterResource(R.drawable.rounded_library_music_24), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        // Full Width Style
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 6.dp,
                            // Simulated rounded top corners for preview if desired, or simplified
                            shape = AbsoluteSmoothCornerShape(
                                cornerRadiusTL = 28.dp, // Default preview radius
                                smoothnessAsPercentTL = 60,
                                cornerRadiusTR = 28.dp,
                                smoothnessAsPercentTR = 60,
                                cornerRadiusBL = 0.dp,
                                smoothnessAsPercentBL = 60,
                                cornerRadiusBR = 0.dp,
                                smoothnessAsPercentBR = 60
                            )
                        ) {
                             Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(painterResource(R.drawable.rounded_home_24), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(painterResource(R.drawable.rounded_search_24), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(painterResource(R.drawable.rounded_library_music_24), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
