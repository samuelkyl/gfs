package ca.sleung.gfs.ui.screens.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.sleung.gfs.R
import ca.sleung.gfs.domain.model.CategoryBreakdown
import ca.sleung.gfs.ui.theme.GFSTheme
import ca.sleung.gfs.ui.theme.TABULAR_FIGURES_FEATURE
import ca.sleung.gfs.ui.theme.parseHexColor
import ca.sleung.gfs.ui.util.CurrencyFormatter
import kotlin.math.roundToInt

@Composable
fun CategoryPieChart(
    breakdown: List<CategoryBreakdown>,
    totalSpent: Double,
    modifier: Modifier = Modifier,
    hideDecimals: Boolean = false
) {
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(breakdown, totalSpent) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 550)
        )
    }

    val formattedTotal = remember(totalSpent, hideDecimals) {
        CurrencyFormatter.formatCurrency(totalSpent, hideDecimals = hideDecimals)
    }
    val chartA11y = pluralStringResource(
        R.plurals.breakdown_chart_a11y,
        breakdown.size,
        formattedTotal,
        breakdown.size
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.breakdown_header),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                letterSpacing = 1.sp
            )
            Text(
                text = formattedTotal,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFeatureSettings = TABULAR_FIGURES_FEATURE
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (totalSpent <= 0.0 || breakdown.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.breakdown_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Donut Chart Canvas with screen-reader summary
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = chartA11y
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(100.dp)) {
                        val strokeWidth = 20.dp.toPx()
                        var currentAngle = -90f

                        breakdown.forEach { item ->
                            val sliceSweep = (item.percentage / 100f) * 360f * animatedProgress.value
                            val sliceColor = parseHexColor(item.colorHex)

                            drawArc(
                                color = sliceColor,
                                startAngle = currentAngle,
                                sweepAngle = sliceSweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            currentAngle += sliceSweep
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.breakdown_spent_center),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = formattedTotal,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFeatureSettings = TABULAR_FIGURES_FEATURE
                            ),
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Legend List: Both Amount AND Percentage
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    breakdown.forEach { item ->
                        val itemColor = parseHexColor(item.colorHex)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(itemColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.tag,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = CurrencyFormatter.formatCurrency(item.totalAmount, hideDecimals = hideDecimals),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFeatureSettings = TABULAR_FIGURES_FEATURE
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "(${item.percentage.roundToInt()}%)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFeatureSettings = TABULAR_FIGURES_FEATURE
                                    ),
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun CategoryPieChartPreview() {
    GFSTheme(dynamicColor = false) {
        Surface {
            CategoryPieChart(
                breakdown = listOf(
                    CategoryBreakdown("Eating Out", "#FF7043", 210.50, 52.6f),
                    CategoryBreakdown("Shopping", "#AB47BC", 120.00, 30.0f),
                    CategoryBreakdown("Coffee & Drinks", "#26A69A", 69.50, 17.4f)
                ),
                totalSpent = 400.0,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
