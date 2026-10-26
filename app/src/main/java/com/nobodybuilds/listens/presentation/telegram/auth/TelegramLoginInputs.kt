package com.nobodybuilds.listens.presentation.telegram.auth

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveCodeInput(
    code: String,
    isLoading: Boolean,
    onCodeChanged: (String) -> Unit,
    onCheck: () -> Unit,
    onEditPhone: () -> Unit,
    onResendCode: () -> Unit
) {
    val inputShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 16.dp,
        cornerRadiusTL = 16.dp,
        cornerRadiusBR = 16.dp,
        cornerRadiusBL = 16.dp,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60,
        smoothnessAsPercentBL = 60
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthStepHeader(
            icon = Icons.Rounded.Sms,
            title = stringResource(R.string.presentation_batch_f_verification_code_title),
            subtitle = stringResource(R.string.presentation_batch_f_verification_code_subtitle)
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { onCodeChanged(it.filter(Char::isDigit).take(8)) },
            label = { Text(stringResource(R.string.presentation_batch_f_code_field_label), fontFamily = GoogleSansRounded) },
            placeholder = { Text(stringResource(R.string.presentation_batch_f_code_placeholder), fontFamily = GoogleSansRounded) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Sms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (!isLoading) onCheck()
            }),
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = inputShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onEditPhone, enabled = !isLoading) {
                Text(text = stringResource(R.string.presentation_batch_f_edit_phone), fontFamily = GoogleSansRounded)
            }
            TextButton(onClick = onResendCode, enabled = !isLoading) {
                Text(text = stringResource(R.string.presentation_batch_f_resend_code), fontFamily = GoogleSansRounded)
            }
        }

        Spacer(Modifier.height(18.dp))

        ExpressiveButton(
            text = stringResource(R.string.presentation_batch_f_verify_code),
            onClick = onCheck,
            enabled = code.length >= 3 && !isLoading,
            loading = isLoading
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePasswordInput(
    password: String,
    isLoading: Boolean,
    onPasswordChanged: (String) -> Unit,
    onCheck: () -> Unit,
    onEditPhone: () -> Unit
) {
    val inputShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 16.dp,
        cornerRadiusTL = 16.dp,
        cornerRadiusBR = 16.dp,
        cornerRadiusBL = 16.dp,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60,
        smoothnessAsPercentBL = 60
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthStepHeader(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.presentation_batch_f_two_step_password_title),
            subtitle = stringResource(R.string.presentation_batch_f_two_step_password_subtitle)
        )

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChanged,
            label = { Text(stringResource(R.string.presentation_batch_f_password_label), fontFamily = GoogleSansRounded) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (!isLoading) onCheck()
            }),
            singleLine = true,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            shape = inputShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            )
        )

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onEditPhone, enabled = !isLoading) {
                Text(text = stringResource(R.string.presentation_batch_f_edit_phone), fontFamily = GoogleSansRounded)
            }
        }

        Spacer(Modifier.height(18.dp))

        ExpressiveButton(
            text = stringResource(R.string.presentation_batch_f_verify_password),
            onClick = onCheck,
            enabled = password.isNotBlank() && !isLoading,
            loading = isLoading
        )
    }
}

@Composable
internal fun AuthStepHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ExpressiveButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "telegramButtonScale"
    )

    MediumExtendedFloatingActionButton(
        text = {
            Text(
                text = if (loading) stringResource(R.string.presentation_batch_f_please_wait) else text,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold
            )
        },
        icon = {
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.width(28.dp))
            } else {
                Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
            }
        },
        onClick = {
            if (enabled && !loading) onClick()
        },
        expanded = true,
        shape = CircleShape,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        containerColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        interactionSource = interactionSource
    )
}
