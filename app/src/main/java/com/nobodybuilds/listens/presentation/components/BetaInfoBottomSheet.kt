package com.nobodybuilds.listens.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobodybuilds.listens.R
import com.nobodybuilds.listens.presentation.components.subcomps.SineWaveLine
import com.nobodybuilds.listens.ui.theme.ExpTitleTypography
import com.nobodybuilds.listens.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BetaInfoBottomSheet(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val issuesUrl = "https://github.com/Nobody-Builds/Nobody-Listens/issues"
    val reportUrl = "https://github.com/Nobody-Builds/Nobody-Listens/issues/new"

    val fabCornerRadius = 18.dp

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.presentation_batch_g_beta_sheet_version),
                        fontFamily = GoogleSansRounded,
                        style = ExpTitleTypography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SineWaveLine(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .padding(horizontal = 8.dp),
                        animate = true,
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f),
                        alpha = 0.95f,
                        strokeWidth = 4.dp,
                        amplitude = 4.dp,
                        waves = 7.6f,
                        phase = 0f
                    )
                }
            }

            item(key = "welcome") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTR = fabCornerRadius,
                        cornerRadiusTL = fabCornerRadius,
                        cornerRadiusBL = fabCornerRadius,
                        cornerRadiusBR = fabCornerRadius,
                        smoothnessAsPercentTR = 60,
                        smoothnessAsPercentTL = 60,
                        smoothnessAsPercentBL = 60,
                        smoothnessAsPercentBR = 60
                    ),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                        )
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.presentation_batch_h_beta_glyph),
                                color = MaterialTheme.colorScheme.onPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.presentation_batch_g_beta_sheet_welcome_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.presentation_batch_g_beta_sheet_welcome_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item(key = "github-shortcut") {
                GitHubReportCard(
                    onOpenIssues = { launchUrl(context, issuesUrl) },
                    onReportIssue = { launchUrl(context, reportUrl) }
                )
            }

            item(key = "section-expect") {
                BetaFaqSection(
                    title = stringResource(R.string.presentation_batch_g_beta_sheet_expect_title),
                    summary = stringResource(R.string.presentation_batch_g_beta_sheet_expect_summary),
                    icon = Icons.Rounded.Whatshot,
                    iconTint = MaterialTheme.colorScheme.primary,
                    initiallyExpanded = true
                ) {
                    BetaBulletList(
                        items = listOf(
                            R.string.presentation_batch_g_beta_sheet_expect_1,
                            R.string.presentation_batch_g_beta_sheet_expect_2,
                            R.string.presentation_batch_g_beta_sheet_expect_3,
                            R.string.presentation_batch_g_beta_sheet_expect_4
                        )
                    )
                }
            }

            item(key = "section-reporting") {
                BetaFaqSection(
                    title = stringResource(R.string.presentation_batch_g_beta_sheet_report_title),
                    summary = stringResource(R.string.presentation_batch_g_beta_sheet_report_summary),
                    icon = Icons.Rounded.Search,
                    iconTint = MaterialTheme.colorScheme.secondary
                ) {
                    BetaSubsectionHeader(
                        icon = Icons.Rounded.Search,
                        title = stringResource(R.string.presentation_batch_g_beta_sheet_before_title)
                    )
                    BetaBulletList(
                        items = listOf(
                            R.string.presentation_batch_g_beta_sheet_before_1,
                            R.string.presentation_batch_g_beta_sheet_before_2,
                            R.string.presentation_batch_g_beta_sheet_before_3,
                            R.string.presentation_batch_g_beta_sheet_before_4
                        )
                    )
                    BetaSectionDivider()
                    BetaSubsectionHeader(
                        icon = Icons.Rounded.CheckCircle,
                        title = stringResource(R.string.presentation_batch_g_beta_sheet_issue_type_title)
                    )
                    BetaBulletList(
                        items = listOf(
                            R.string.presentation_batch_g_beta_sheet_issue_type_bug,
                            R.string.presentation_batch_g_beta_sheet_issue_type_feature,
                            R.string.presentation_batch_g_beta_sheet_issue_type_question
                        )
                    )
                }
            }

            item(key = "section-bug-report") {
                BetaFaqSection(
                    title = stringResource(R.string.presentation_batch_g_beta_sheet_bug_title),
                    summary = stringResource(R.string.presentation_batch_g_beta_sheet_bug_summary_text),
                    icon = Icons.Rounded.BugReport,
                    iconTint = MaterialTheme.colorScheme.error,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    BetaSubsectionHeader(
                        icon = Icons.Rounded.BugReport,
                        title = stringResource(R.string.presentation_batch_g_beta_sheet_bug_template_title)
                    )
                    BetaFieldList(
                        items = listOf(
                            R.string.presentation_batch_g_beta_sheet_bug_summary,
                            R.string.presentation_batch_g_beta_sheet_bug_expected,
                            R.string.presentation_batch_g_beta_sheet_bug_actual,
                            R.string.presentation_batch_g_beta_sheet_bug_steps,
                            R.string.presentation_batch_g_beta_sheet_bug_frequency,
                            R.string.presentation_batch_g_beta_sheet_bug_screenshot,
                            R.string.presentation_batch_g_beta_sheet_bug_logs
                        )
                    )
                    BetaSectionDivider()
                    BetaSubsectionHeader(
                        icon = Icons.Rounded.Info,
                        title = stringResource(R.string.presentation_batch_g_beta_sheet_env_title)
                    )
                    BetaFieldList(
                        items = listOf(
                            R.string.presentation_batch_g_beta_sheet_env_version,
                            R.string.presentation_batch_g_beta_sheet_env_source,
                            R.string.presentation_batch_g_beta_sheet_env_android,
                            R.string.presentation_batch_g_beta_sheet_env_device,
                            R.string.presentation_batch_g_beta_sheet_env_extra
                        )
                    )
                }
            }

            item(key = "section-feature") {
                BetaFaqSection(
                    title = stringResource(R.string.presentation_batch_g_beta_sheet_feature_title),
                    summary = stringResource(R.string.presentation_batch_g_beta_sheet_feature_summary),
                    icon = Icons.Rounded.Highlight,
                    iconTint = MaterialTheme.colorScheme.primary
                ) {
                    BetaFieldList(
                        items = listOf(
                            R.string.presentation_batch_g_beta_sheet_feature_problem,
                            R.string.presentation_batch_g_beta_sheet_feature_solution,
                            R.string.presentation_batch_g_beta_sheet_feature_alternatives,
                            R.string.presentation_batch_g_beta_sheet_feature_scope,
                            R.string.presentation_batch_g_beta_sheet_feature_mockup
                        )
                    )
                }
            }

            item(key = "section-quality") {
                BetaFaqSection(
                    title = stringResource(R.string.presentation_batch_g_beta_sheet_quality_title),
                    summary = stringResource(R.string.presentation_batch_g_beta_sheet_quality_summary),
                    icon = Icons.Rounded.Gavel,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                ) {
                    BetaSubsectionHeader(
                        icon = Icons.Rounded.Gavel,
                        title = stringResource(R.string.presentation_batch_g_beta_sheet_title_examples_title)
                    )
                    BetaBulletList(
                        items = listOf(
                            R.string.presentation_batch_g_beta_sheet_title_example_1,
                            R.string.presentation_batch_g_beta_sheet_title_example_2,
                            R.string.presentation_batch_g_beta_sheet_title_example_3
                        )
                    )
                    BetaSectionDivider()
                    BetaSubsectionHeader(
                        icon = Icons.Rounded.Warning,
                        title = stringResource(R.string.presentation_batch_g_beta_sheet_avoid_title),
                        tint = MaterialTheme.colorScheme.error
                    )
                    BetaBulletList(
                        items = listOf(
                            R.string.presentation_batch_g_beta_sheet_avoid_1,
                            R.string.presentation_batch_g_beta_sheet_avoid_2,
                            R.string.presentation_batch_g_beta_sheet_avoid_3
                        )
                    )
                    BetaSectionDivider()
                    BetaSubsectionHeader(
                        icon = Icons.Rounded.Shield,
                        title = stringResource(R.string.presentation_batch_g_beta_sheet_privacy_title)
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_g_beta_sheet_privacy_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            item(key = "section-nightly") {
                BetaFaqSection(
                    title = stringResource(R.string.presentation_batch_g_beta_sheet_nightly_title),
                    summary = stringResource(R.string.presentation_batch_g_beta_sheet_nightly_summary),
                    icon = Icons.Rounded.NightsStay,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                ) {
                    BetaSubsectionHeader(
                        icon = Icons.Rounded.NightsStay,
                        title = stringResource(R.string.presentation_batch_g_beta_sheet_nightly_title)
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_g_beta_sheet_nightly_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_g_beta_sheet_nightly_access),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    BetaSectionDivider()
                    BetaSubsectionHeader(
                        icon = Icons.Rounded.Cloud,
                        title = stringResource(R.string.presentation_batch_g_beta_sheet_nightly_report_title)
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_g_beta_sheet_nightly_report_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            item(key = "bottom-spacer") {
                Spacer(modifier = Modifier.height(104.dp))
            }
        }
        ExtendedFloatingActionButton(
            onClick = { launchUrl(context, reportUrl) },
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTR = fabCornerRadius,
                cornerRadiusTL = fabCornerRadius,
                cornerRadiusBL = fabCornerRadius,
                cornerRadiusBR = fabCornerRadius,
                smoothnessAsPercentTR = 60,
                smoothnessAsPercentTL = 60,
                smoothnessAsPercentBL = 60,
                smoothnessAsPercentBR = 60
            ),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.github),
                    contentDescription = null
                )
            },
            text = {
                Text(text = stringResource(R.string.presentation_batch_g_beta_sheet_report_bug))
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(30.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
        ) {

        }
    }
}
