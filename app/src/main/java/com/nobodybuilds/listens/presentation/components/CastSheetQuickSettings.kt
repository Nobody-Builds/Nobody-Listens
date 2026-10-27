package com.nobodybuilds.listens.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R
import androidx.compose.ui.unit.sp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.isActive

@Composable
internal fun QuickSettingsRow(
    wifiOn: Boolean,
    wifiConnected: Boolean,
    wifiSsid: String?,
    onWifiClick: () -> Unit,
    bluetoothEnabled: Boolean,
    bluetoothName: String?,
    onBluetoothClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickSettingTile(
            label = if (wifiConnected && !wifiSsid.isNullOrEmpty()) wifiSsid else stringResource(R.string.presentation_batch_g_cast_wifi),
            subtitle = when {
                !wifiOn -> stringResource(R.string.presentation_batch_g_cast_wifi_off)
                wifiConnected -> stringResource(R.string.presentation_batch_g_cast_wifi_connected)
                else -> stringResource(R.string.presentation_batch_g_cast_wifi_on)
            },
            icon = if (wifiOn) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
            isActive = wifiOn,
            onClick = onWifiClick,
            modifier = Modifier.weight(1f)
        )
        QuickSettingTile(
            label = if (bluetoothEnabled && !bluetoothName.isNullOrEmpty()) bluetoothName else stringResource(R.string.presentation_batch_g_cast_bluetooth),
            subtitle = if (bluetoothEnabled) {
                if (!bluetoothName.isNullOrEmpty()) stringResource(R.string.presentation_batch_g_cast_bt_connected) else stringResource(R.string.presentation_batch_g_cast_bt_on)
            } else stringResource(R.string.presentation_batch_g_cast_bt_off),
            icon = if (bluetoothEnabled) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
            isActive = bluetoothEnabled,
            onClick = onBluetoothClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickSettingTile(
    label: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Definimos la forma específica para el estado activo
    val activeShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 18.dp,
            cornerRadiusTR = 18.dp,
            cornerRadiusBL = 18.dp,
            cornerRadiusBR = 18.dp,
            smoothnessAsPercentTL = 70,
            smoothnessAsPercentTR = 70,
            smoothnessAsPercentBL = 70,
            smoothnessAsPercentBR = 70
        )
    }

    // El fondo del Tile ahora siempre es surface, pero mantenemos una transición suave si quisieras cambiarlo levemente
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = MaterialTheme.colorScheme.onSurface

    // Colores dinámicos para el ICONO (Círculo interno)
    val iconBoxColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.1f),
        label = "iconBoxColor"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimary else contentColor,
        label = "iconTint"
    )

    Surface(
        modifier = modifier
            .height(72.dp)
            // Aquí alternamos la forma según el estado
            .clip(if (isActive) activeShape else CircleShape)
            .clickable(onClick = onClick),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // El contenedor del icono es el que lleva el color primario ahora
            Box(
                modifier = Modifier
                    .size(40.dp) // Un poco más grande para lucir la forma y el color
                    .clip(CircleShape)
                    .background(iconBoxColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint
                )
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f) // Asegura que el texto ocupe el espacio restante
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
internal fun WifiOffIllustration(
    onTurnOnWifi: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 38.dp,
        cornerRadiusTR = 20.dp,
        cornerRadiusBL = 20.dp,
        cornerRadiusBR = 38.dp,
        smoothnessAsPercentTL = 70,
        smoothnessAsPercentTR = 70,
        smoothnessAsPercentBL = 70,
        smoothnessAsPercentBR = 70
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val prim1 = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            val prim2 = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            val prim3 = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.size(120.dp)) {
                drawCircle(color = prim1, radius = size.minDimension / 2)
                drawCircle(
                    color = prim2,
                    radius = size.minDimension / 3,
                    style = Stroke(width = 10.dp.toPx())
                )
                drawCircle(color = prim3, radius = size.minDimension / 6)
            }
            Text(
                text = stringResource(R.string.presentation_batch_g_cast_connections_off_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = stringResource(R.string.presentation_batch_g_cast_connections_off_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onTurnOnWifi,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.presentation_batch_g_cast_turn_on_wifi))
                }

                Button(
                    onClick = onOpenBluetoothSettings,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(stringResource(R.string.presentation_batch_g_cast_open_bluetooth))
                }
            }
        }
    }
}

@Composable
internal fun ScanningPlaceholderList() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 28.dp,
                            topEnd = 4.dp,
                            bottomEnd = 28.dp,
                            bottomStart = 4.dp
                        )
                    )
            )
        }
    }
}

@Composable
private fun ScanningIndicator(isActive: Boolean) {
    val infinite = rememberInfiniteTransition(label = "scanPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = FastOutSlowInEasing)),
        label = "pulse"
    )
    val prim = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            drawCircle(
                color = prim,
                radius = (size.minDimension / 2) * if (isActive) pulse else 0.8f,
                alpha = if (isActive) 0.8f else 0.4f
            )
        }
    }
}
