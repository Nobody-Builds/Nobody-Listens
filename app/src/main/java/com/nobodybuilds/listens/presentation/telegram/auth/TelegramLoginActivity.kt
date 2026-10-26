package com.nobodybuilds.listens.presentation.telegram.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobodybuilds.listens.MainActivity
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.telegram.channel.TelegramChannelSearchSheet
import com.nobodybuilds.listens.presentation.telegram.dashboard.TelegramDashboardScreen
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import com.nobodybuilds.listens.ui.theme.NobodyListensTheme
import dagger.hilt.android.AndroidEntryPoint
import org.drinkless.tdlib.TdApi
import androidx.compose.ui.res.stringResource

@AndroidEntryPoint
class TelegramLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NobodyListensTheme {
                TelegramLoginScreen(onFinish = { finish() })
            }
        }
    }
}

private enum class TelegramVisualStep {
    Phone,
    Code,
    Password,
    Status
}

@kotlin.OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun TelegramLoginScreen(
    viewModel: TelegramLoginViewModel = hiltViewModel(),
    onFinish: () -> Unit
) {
    val authState by viewModel.authorizationState.collectAsStateWithLifecycle(initialValue = null)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSearchSheet by remember { mutableStateOf(false) }

    if (showSearchSheet) {
        TelegramChannelSearchSheet(
            onDismissRequest = { showSearchSheet = false },
            onSongSelected = { song -> viewModel.downloadAndPlay(song) }
        )
    }

    val context = LocalContext.current
    val defaultWorkingMessage = stringResource(R.string.presentation_batch_f_working)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.playbackRequest.collect { song ->
            val intent = Intent(context, MainActivity::class.java).apply {
                action = "com.nobodybuilds.listens.ACTION_PLAY_SONG"
                putExtra("song", song as android.os.Parcelable)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            onFinish()
        }
    }

    if (authState is TdApi.AuthorizationStateReady && !uiState.isLoading) {
        TelegramDashboardScreen(
            onAddChannel = { showSearchSheet = true },
            onBack = onFinish
        )
        return
    }

    val visualStep = remember(authState, uiState.phoneEditMode) {
        resolveTelegramVisualStep(authState, uiState.phoneEditMode)
    }

    BackHandler(enabled = true) {
        if (!viewModel.handleBackNavigation(authState)) {
            onFinish()
        }
    }

    val gradientColors = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        MaterialTheme.colorScheme.surface
    )

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.presentation_batch_f_telegram_login_title),
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        onClick = {
                            if (!viewModel.handleBackNavigation(authState)) {
                                onFinish()
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.auth_cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(gradientColors))
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TelegramBrandingHeader()
            Spacer(modifier = Modifier.height(20.dp))

            val currentStep = when (visualStep) {
                TelegramVisualStep.Phone -> 0
                TelegramVisualStep.Code -> 1
                TelegramVisualStep.Password -> 2
                TelegramVisualStep.Status -> -1
            }
            if (currentStep >= 0) {
                StepIndicator(currentStep = currentStep, totalSteps = 3)
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (uiState.phoneEditMode && (authState is TdApi.AuthorizationStateWaitCode || authState is TdApi.AuthorizationStateWaitPassword)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = {
                        Text(
                            text = stringResource(R.string.presentation_batch_f_telegram_edit_number_chip),
                            fontFamily = GoogleSansRounded
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (uiState.isLoading) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = uiState.loadingMessage.ifBlank { defaultWorkingMessage },
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            uiState.inlineError?.let { errorText ->
                InlineErrorCard(
                    message = errorText,
                    onDismiss = viewModel::clearInlineError
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            AnimatedContent(
                targetState = visualStep,
                transitionSpec = {
                    (slideInHorizontally { width -> width / 3 } + fadeIn())
                        .togetherWith(slideOutHorizontally { width -> -width / 3 } + fadeOut())
                },
                label = "telegramLoginStepTransition"
            ) { step ->
                when (step) {
                    TelegramVisualStep.Phone -> {
                        ExpressivePhoneNumberInput(
                            phoneNumber = uiState.phoneNumber,
                            isLoading = uiState.isLoading,
                            onPhoneNumberChanged = viewModel::onPhoneNumberChanged,
                            onSend = viewModel::sendPhoneNumber
                        )
                    }

                    TelegramVisualStep.Code -> {
                        ExpressiveCodeInput(
                            code = uiState.code,
                            isLoading = uiState.isLoading,
                            onCodeChanged = viewModel::onCodeChanged,
                            onCheck = viewModel::checkCode,
                            onEditPhone = viewModel::enablePhoneEditMode,
                            onResendCode = viewModel::sendPhoneNumber
                        )
                    }

                    TelegramVisualStep.Password -> {
                        ExpressivePasswordInput(
                            password = uiState.password,
                            isLoading = uiState.isLoading,
                            onPasswordChanged = viewModel::onPasswordChanged,
                            onCheck = viewModel::checkPassword,
                            onEditPhone = viewModel::enablePhoneEditMode
                        )
                    }

                    TelegramVisualStep.Status -> {
                        StatusMessage(message = authStateStatusMessage(authState))
                    }
                }
            }
        }
    }
}

private fun resolveTelegramVisualStep(
    authState: TdApi.AuthorizationState?,
    isPhoneEditMode: Boolean
): TelegramVisualStep {
    if (isPhoneEditMode) return TelegramVisualStep.Phone

    return when (authState) {
        is TdApi.AuthorizationStateWaitPhoneNumber -> TelegramVisualStep.Phone
        is TdApi.AuthorizationStateWaitCode -> TelegramVisualStep.Code
        is TdApi.AuthorizationStateWaitPassword -> TelegramVisualStep.Password
        else -> TelegramVisualStep.Status
    }
}

@Composable
private fun authStateStatusMessage(state: TdApi.AuthorizationState?): String =
    stringResource(
        when (state) {
            null -> R.string.presentation_batch_f_telegram_status_initializing
            is TdApi.AuthorizationStateLoggingOut -> R.string.presentation_batch_f_telegram_status_logging_out
            is TdApi.AuthorizationStateClosing -> R.string.presentation_batch_f_telegram_status_closing
            is TdApi.AuthorizationStateClosed -> R.string.presentation_batch_f_telegram_status_closed
            is TdApi.AuthorizationStateWaitTdlibParameters -> R.string.presentation_batch_f_telegram_status_preparing
            else -> R.string.presentation_batch_f_telegram_status_waiting
        }
    )

@Composable
private fun TelegramBrandingHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.telegram),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.presentation_batch_f_connect_telegram),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.presentation_batch_f_connect_telegram_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StepIndicator(
    currentStep: Int,
    totalSteps: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index <= currentStep
            val isCurrent = index == currentStep

            val scale by animateFloatAsState(
                targetValue = if (isCurrent) 1.2f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "telegramStepScale"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .size(if (isCurrent) 12.dp else 10.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

@Composable
private fun InlineErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            FilledIconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(imageVector = Icons.Rounded.Clear, contentDescription = stringResource(R.string.dismiss))
            }
        }
    }
}

@Composable
private fun StatusMessage(message: String) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
