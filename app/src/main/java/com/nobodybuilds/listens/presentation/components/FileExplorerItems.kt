@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.nobodybuilds.listens.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.nobodybuilds.listens.R
import java.io.File

@Composable
internal fun ExplorerEmptyState(
    text: String,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOff,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
internal fun ExplorerLoadingState(
    message: String,
    supportingText: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ContainedLoadingIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (supportingText != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
internal fun FileExplorerItem(
    file: File,
    audioCount: Int,
    displayName: String?,
    isBlocked: Boolean,
    onNavigate: () -> Unit,
    onToggleAllowed: () -> Unit,
    navigationEnabled: Boolean,
    pickMode: Boolean = false,
    isPicked: Boolean = false
) {
    val shape = RoundedCornerShape(18.dp)

    val isAllowed = !isBlocked

    val containerColor = when {
        pickMode && isPicked -> MaterialTheme.colorScheme.tertiaryContainer
        pickMode -> MaterialTheme.colorScheme.surfaceContainerHigh
        isBlocked -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = when {
        pickMode && isPicked -> MaterialTheme.colorScheme.onTertiaryContainer
        pickMode -> MaterialTheme.colorScheme.onSurface
        isBlocked -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    val badgeColor = when {
        pickMode && isPicked -> MaterialTheme.colorScheme.onTertiaryContainer
        pickMode -> MaterialTheme.colorScheme.secondary
        isBlocked -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.secondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .clickable(enabled = navigationEnabled) { onNavigate() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(shape)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = contentColor
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName ?: file.name.ifEmpty { file.path },
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = file.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis
            )
            Spacer(
                modifier = Modifier
                    .height(6.dp)
                    .fillMaxWidth()
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeColor.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when {
                        audioCount < 0 -> "Scanning..."
                        audioCount == 1 -> "1 song"
                        audioCount > 99 -> "99+ songs"
                        else -> "$audioCount songs"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = badgeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (navigationEnabled) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = contentColor
            )
        } else {
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (pickMode) {
                Text(
                    text = if (isPicked) "Added" else "Add",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor
                )
                RadioButton(
                    selected = isPicked,
                    onClick = onToggleAllowed,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            } else {
            Text(
                text = if (isBlocked) "Excluded" else "Included",
                style = MaterialTheme.typography.labelMedium,
                color = if (isBlocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            RadioButton(
                selected = isBlocked,
                onClick = onToggleAllowed,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.onErrorContainer,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileExplorerHeader(
    modifier: Modifier,
    currentPath: File,
    rootDirectory: File,
    isAtRoot: Boolean,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateTo: (File) -> Unit,
    navigationEnabled: Boolean
) {
    // 1. Cambiamos ScrollState por LazyListState para manejar mejor los ítems y el scroll automático
    val listState = rememberLazyListState()

    val breadcrumbs by remember(currentPath, rootDirectory) {
        mutableStateOf(buildList {
            var cursor: File? = currentPath
            val rootPath = rootDirectory.path
            while (cursor != null) {
                add(cursor)
                if (cursor.path == rootPath) break
                cursor = cursor.parentFile
            }
            reverse()
        })
    }

    val rootLabel = remember(rootDirectory) {
        when (rootDirectory.name) {
            "0", "" -> "Internal storage"
            else -> rootDirectory.name
        }
    }

    // 2. Lógica para detectar si hay contenido oculto a los lados
    val showStartFade by remember { derivedStateOf { listState.canScrollBackward } }
    val showEndFade by remember { derivedStateOf { listState.canScrollForward } }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Botón de "Atrás" (Back Arrow) - Se mantiene igual fuera del scroll
            if (!isAtRoot && navigationEnabled) {
                IconButton(
                    onClick = onNavigateUp,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.presentation_batch_g_file_explorer_cd_navigate_up),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (!isAtRoot) {
                // 3. Auto-scroll al final cuando cambia el path
                LaunchedEffect(breadcrumbs.size) {
                    if (breadcrumbs.isNotEmpty()) {
                        listState.animateScrollToItem(breadcrumbs.lastIndex)
                    }
                }

                // 4. Reemplazamos el Row + horizontalScroll por LazyRow con el efecto gráfico
                LazyRow(
                    state = listState,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        // APLICACIÓN DEL EFECTO DE GRADIENTE
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val gradientWidth = 24.dp.toPx()

                            // Fade Izquierdo
                            if (showStartFade) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        endX = gradientWidth
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }

                            // Fade Derecho
                            if (showEndFade) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startX = size.width - gradientWidth
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                ) {
                    // Spacer inicial para que el primer ítem no quede pegado al borde o debajo del fade
                    item { Spacer(modifier = Modifier.width(4.dp)) }

                    items(breadcrumbs.size, key = { breadcrumbs[it].path }) { index ->
                        val file = breadcrumbs[index]
                        val isRoot = file.path == rootDirectory.path
                        val isLast = index == breadcrumbs.lastIndex
                        val label = if (isRoot) rootLabel else file.name.ifEmpty { file.path }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Diseño del Chip (Mantenemos tu estilo visual original)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(enabled = !isLast && navigationEnabled) {
                                        if (isRoot) onNavigateHome() else onNavigateTo(file)
                                    }
                                    .background(
                                        color = if (isLast) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                        shape = CircleShape
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                if (isRoot) {
                                    Icon(
                                        imageVector = Icons.Rounded.Home,
                                        contentDescription = stringResource(R.string.presentation_batch_g_file_explorer_cd_go_root),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .size(16.dp)
                                    )
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isLast) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Separador (Chevron)
                            if (!isLast) {
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp, end = 2.dp)
                                )
                            }
                        }
                    }

                    // Spacer final para dar aire al último elemento
                    item { Spacer(modifier = Modifier.width(12.dp)) }
                }
            }
        }
    }
}

/** True when [path] is one of [picked] or sits inside one of them. */
internal fun isPathPicked(path: String, picked: Set<String>): Boolean {
    val normalized = path.trimEnd('/')
    return picked.any { root ->
        val r = root.trimEnd('/')
        normalized.equals(r, ignoreCase = true) || normalized.startsWith("$r/", ignoreCase = true)
    }
}
