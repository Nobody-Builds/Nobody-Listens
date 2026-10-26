package com.nobodybuilds.listens.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.components.PermissionIconCollage
import com.nobodybuilds.listens.presentation.components.subcomps.MaterialYouVectorDrawable
import com.nobodybuilds.listens.presentation.components.subcomps.SineWaveLine
import kotlinx.collections.immutable.persistentListOf

@Composable
fun WelcomePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.setup_welcome_prefix).trim(),
                style = com.nobodybuilds.listens.presentation.nb.NbType.h2,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.app_name),
                style = com.nobodybuilds.listens.presentation.nb.NbType.display(56.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Your music, on your phone.",
                style = com.nobodybuilds.listens.presentation.nb.NbType.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        // Placeholder for vector art
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                //.background(color = Color.Red)
                .clip(RoundedCornerShape(20.dp))
        ){
            MaterialYouVectorDrawable(
                modifier = Modifier.fillMaxSize(),
                drawableResId = R.drawable.welcome_art
            )
            SineWaveLine(
                modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(32.dp)
                .padding(horizontal = 8.dp)
                .padding(bottom = 4.dp),
                animate = true,
                color = MaterialTheme.colorScheme.surface,
                alpha = 0.95f,
                strokeWidth = 16.dp,
                amplitude = 4.dp,
                waves = 7.6f,
                phase = 0f
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(22.dp)
                    .background(color = MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 4.dp)
            ){

            }
            SineWaveLine(
                modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(32.dp)
                .padding(horizontal = 8.dp)
                .padding(bottom = 4.dp),
                animate = true,
                color = MaterialTheme.colorScheme.primary, //Container.copy(alpha = 0.9f),
                alpha = 0.95f,
                strokeWidth = 4.dp,
                amplitude = 4.dp,
                waves = 7.6f,
                phase = 0f
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.setup_intro_body), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun FinishPage() {
    val finishIcons = persistentListOf(
        R.drawable.rounded_check_circle_24,
        R.drawable.round_favorite_24,
        R.drawable.rounded_celebration_24,
        R.drawable.round_favorite_24,
        R.drawable.rounded_explosion_24
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(text = stringResource(R.string.setup_all_set_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(16.dp))
        PermissionIconCollage(
            modifier = Modifier.height(230.dp),
            icons = finishIcons
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = stringResource(R.string.setup_all_set_body), style = MaterialTheme.typography.bodyLarge)
    }
}
