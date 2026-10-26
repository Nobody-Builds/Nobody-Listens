package com.nobodybuilds.listens.presentation.telegram.auth

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.util.Locale
import androidx.compose.ui.res.stringResource

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressivePhoneNumberInput(
    phoneNumber: String,
    isLoading: Boolean,
    onPhoneNumberChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    var countryCode by remember { mutableStateOf("") }
    var localNumber by remember { mutableStateOf("") }
    var codeFieldFocused by remember { mutableStateOf(false) }
    var numFieldFocused by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    val numFocusRequester = remember { FocusRequester() }
    val codeFocusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val isActive = codeFieldFocused || numFieldFocused

    LaunchedEffect(Unit) {
        val (parsedCode, parsedLocal) = splitPhoneNumberForInput(phoneNumber)
        if (parsedCode.isNotEmpty() || parsedLocal.isNotEmpty()) {
            countryCode = parsedCode
            localNumber = parsedLocal
            isExpanded = true
            return@LaunchedEffect
        }

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        val iso = tm?.networkCountryIso?.uppercase()?.takeIf { it.isNotEmpty() }
            ?: tm?.simCountryIso?.uppercase()?.takeIf { it.isNotEmpty() }
            ?: Locale.getDefault().country
        getDialCodeForCountry(iso).takeIf { it.isNotEmpty() }?.let { countryCode = it }
    }

    LaunchedEffect(countryCode, localNumber) {
        onPhoneNumberChanged(
            if (countryCode.isNotEmpty()) "+$countryCode$localNumber" else localNumber
        )
    }

    LaunchedEffect(isExpanded) {
        if (!isExpanded || isLoading) return@LaunchedEffect
        if (countryCode.isEmpty()) codeFocusRequester.requestFocus() else numFocusRequester.requestFocus()
    }

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

    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        label = "phoneBorderColor"
    )

    val borderWidth = if (isActive) 2.dp else 1.dp
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthStepHeader(
            icon = Icons.Rounded.Phone,
            title = stringResource(R.string.presentation_batch_f_phone_number_title),
            subtitle = stringResource(R.string.presentation_batch_f_phone_number_subtitle)
        )

        Spacer(Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(inputShape)
                    .background(containerColor)
                    .border(BorderStroke(borderWidth, borderColor), inputShape)
                    .clickable(enabled = !isExpanded && !isLoading) { isExpanded = true }
            ) {
                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = {
                        (fadeIn() + slideInHorizontally { it / 4 })
                            .togetherWith(fadeOut())
                    },
                    label = "phoneExpandedTransition"
                ) { expanded ->
                    if (!expanded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.presentation_batch_f_phone_number_hint),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                )
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.auth_dial_plus),
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            BasicTextField(
                                value = countryCode,
                                onValueChange = { raw ->
                                    if (isLoading) return@BasicTextField
                                    val digits = raw.filter(Char::isDigit).take(4)
                                    countryCode = digits
                                    val shouldJump = raw.endsWith(" ") ||
                                        digits.length >= 3 ||
                                        (digits.isNotEmpty() && isoForDialCode(digits).isNotEmpty())
                                    if (shouldJump) numFocusRequester.requestFocus()
                                },
                                modifier = Modifier
                                    .width(42.dp)
                                    .focusRequester(codeFocusRequester)
                                    .onFocusChanged { codeFieldFocused = it.isFocused },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { numFocusRequester.requestFocus() }
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (countryCode.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.presentation_batch_f_phone_country_placeholder),
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontFamily = GoogleSansRounded,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                )
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )

                            Spacer(Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(24.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )

                            Spacer(Modifier.width(12.dp))

                            BasicTextField(
                                value = localNumber,
                                onValueChange = { raw ->
                                    if (isLoading) return@BasicTextField
                                    localNumber = raw.filter(Char::isDigit).take(15)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(numFocusRequester)
                                    .onFocusChanged { numFieldFocused = it.isFocused },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = 1.sp
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (!isLoading) onSend()
                                }),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { inner ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (localNumber.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.presentation_batch_f_phone_local_placeholder),
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontFamily = GoogleSansRounded,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                                    letterSpacing = 1.sp
                                                )
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (isExpanded) {
                Text(
                    text = stringResource(R.string.presentation_batch_f_phone_number_hint),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = GoogleSansRounded,
                        color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp)
                        .offset(y = (-8).dp)
                        .background(containerColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        ExpressiveButton(
            text = stringResource(R.string.presentation_batch_f_send_code),
            onClick = onSend,
            enabled = countryCode.isNotEmpty() && localNumber.isNotBlank() && !isLoading,
            loading = isLoading
        )
    }
}
