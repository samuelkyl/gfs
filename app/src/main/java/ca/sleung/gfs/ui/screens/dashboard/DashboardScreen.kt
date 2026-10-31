package ca.sleung.gfs.ui.screens.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.sleung.gfs.R
import ca.sleung.gfs.data.local.db.entity.TransactionEntity
import ca.sleung.gfs.data.repository.BudgetRepository
import ca.sleung.gfs.domain.model.BudgetInfo
import ca.sleung.gfs.ui.screens.components.AddExpenseBottomSheet
import ca.sleung.gfs.ui.theme.GFSTheme
import ca.sleung.gfs.ui.theme.GFSThemeDefaults
import ca.sleung.gfs.ui.theme.TABULAR_FIGURES_FEATURE
import ca.sleung.gfs.ui.theme.parseHexColor
import ca.sleung.gfs.ui.util.CurrencyFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DashboardContent(
        uiState = uiState,
        onNavigateToHistory = onNavigateToHistory,
        onNavigateToSettings = onNavigateToSettings,
        onOpenAddSheet = viewModel::openAddExpenseSheet,
        onCloseAddSheet = viewModel::closeAddExpenseSheet,
        onAddExpense = viewModel::addExpense,
        onLoadMore = viewModel::loadMoreTransactions,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    uiState: DashboardUiState,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenAddSheet: () -> Unit,
    onCloseAddSheet: () -> Unit,
    onAddExpense: (amount: Double, description: String, tag: String?, tagColor: String?) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val budget = uiState.budgetInfo
    val extendedColors = GFSThemeDefaults.extendedColors

    // Smooth continuous color interpolation: Red (0%) -> Amber (50%) -> Emerald Green (100%)
    val pct = budget.remainingPercentage.coerceIn(0f, 1f)
    val targetTint = if (pct >= 0.5f) {
        lerp(extendedColors.warningAccent, extendedColors.positiveAccent, (pct - 0.5f) * 2f)
    } else {
        lerp(extendedColors.negativeAccent, extendedColors.warningAccent, pct * 2f)
    }
    val animatedAccentColor by animateColorAsState(
        targetValue = targetTint,
        animationSpec = tween(450),
        label = "heroAccentColor"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_pear_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.app_full_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAddSheet,
                containerColor = extendedColors.positiveAccent,
                contentColor = extendedColors.onPositiveAccent,
                shape = CircleShape,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.dashboard_log_expense_cd),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        val listState = rememberLazyListState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 3 && lastVisibleIndex >= totalItems - 3
            }
        }

        LaunchedEffect(shouldLoadMore, uiState.recentTransactions.size) {
            if (shouldLoadMore) {
                onLoadMore()
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero Section: Dynamic ambient background & pacing bar
            item(key = "hero_section") {
                HeroSection(
                    cycleName = budget.cycleName,
                    daysRemaining = budget.daysRemaining,
                    remainingBalance = budget.remainingBalance,
                    totalCycleBudget = budget.totalCycleBudget,
                    percentage = budget.remainingPercentage,
                    accentColor = animatedAccentColor
                )
            }

            // Recent Expenses Section Header
            item(key = "recent_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_recent_activity),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    TextButton(onClick = onNavigateToHistory) {
                        Text(
                            text = stringResource(R.string.dashboard_view_all),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = extendedColors.positiveAccent
                        )
                    }
                }
            }

            // Recent Expenses List (Clean, borderless, read-only)
            if (uiState.recentTransactions.isEmpty()) {
                item(key = "empty_recent") {
                    Text(
                        text = stringResource(R.string.dashboard_empty_activity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(uiState.recentTransactions, key = { it.id }) { tx ->
                    RecentTransactionRow(transaction = tx)
                }
            }

            item(key = "fab_bottom_spacer") {
                Spacer(modifier = Modifier.height(76.dp))
            }
        }

        // Add Expense Modal Bottom Sheet
        if (uiState.showAddExpenseSheet) {
            AddExpenseBottomSheet(
                remainingBalance = budget.remainingBalance,
                tags = uiState.tags,
                onDismiss = onCloseAddSheet,
                onSaveExpense = onAddExpense
            )
        }
    }
}

@Composable
private fun HeroSection(
    cycleName: String,
    daysRemaining: Int,
    remainingBalance: Double,
    totalCycleBudget: Double,
    percentage: Float,
    accentColor: Color
) {
    val animatedProgress by animateFloatAsState(
        targetValue = percentage.coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = "heroProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "cyclePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val formattedRemaining = remember(remainingBalance) {
        CurrencyFormatter.formatCurrency(remainingBalance)
    }
    val formattedBudget = remember(totalCycleBudget) {
        CurrencyFormatter.formatCurrency(totalCycleBudget)
    }
    val pctInt = (percentage * 100).roundToInt()
    val cycleStatusText = pluralStringResource(
        R.plurals.days_left,
        daysRemaining,
        cycleName.ifBlank { "—" },
        daysRemaining
    )
    val heroA11yDescription = stringResource(
        R.string.dashboard_hero_a11y,
        formattedRemaining,
        pctInt,
        formattedBudget,
        cycleStatusText
    )

    // Scale font down gracefully for larger currency strings so single-line display never clips
    val heroFontSize = when {
        formattedRemaining.length <= 6 -> 62.sp
        formattedRemaining.length <= 9 -> 48.sp
        else -> 38.sp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.18f),
                        accentColor.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    )
                )
            )
            .semantics(mergeDescendants = true) {
                contentDescription = heroA11yDescription
            }
            .padding(vertical = 28.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cycle Pill Badge with subtle pulsing status dot
            if (cycleName.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(accentColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cycleStatusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // High-Impact Remaining Balance Typography
            Text(
                text = formattedRemaining,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = heroFontSize,
                    lineHeight = heroFontSize * 1.05f,
                    fontFeatureSettings = TABULAR_FIGURES_FEATURE
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = stringResource(R.string.dashboard_available_to_spend),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Gradient Progress Bar
            Column(
                modifier = Modifier.fillMaxWidth(0.88f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                ) {
                    if (animatedProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress)
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            accentColor.copy(alpha = 0.78f),
                                            accentColor
                                        )
                                    )
                                )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_pct_remaining, pctInt),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = TABULAR_FIGURES_FEATURE
                        ),
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = stringResource(R.string.dashboard_of_budget, formattedBudget),
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

@Composable
private fun RecentTransactionRow(transaction: TransactionEntity) {
    val extendedColors = GFSThemeDefaults.extendedColors
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())
    }
    val dateText = remember(transaction.timestamp) {
        dateFormatter.format(Instant.ofEpochMilli(transaction.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!transaction.tag.isNullOrBlank()) {
                    val tagColor = parseHexColor(transaction.tagColor ?: BudgetRepository.DEFAULT_TAG_COLOR)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tagColor.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = transaction.tag,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tagColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Text(
            text = CurrencyFormatter.formatExpenseItem(transaction.amount),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFeatureSettings = TABULAR_FIGURES_FEATURE
            ),
            fontWeight = FontWeight.Bold,
            color = extendedColors.negativeAccent
        )
    }
}

@PreviewLightDark
@Composable
private fun DashboardScreenPreview() {
    GFSTheme(dynamicColor = false) {
        DashboardContent(
            uiState = DashboardUiState(
                budgetInfo = BudgetInfo(
                    cycleKey = "2026-10",
                    cycleName = "October",
                    daysRemaining = 14,
                    monthlyBudget = 500.0,
                    rolloverAmount = 150.0,
                    totalCycleBudget = 650.0,
                    totalSpent = 211.50,
                    remainingBalance = 438.50,
                    remainingPercentage = 0.67f
                ),
                recentTransactions = listOf(
                    TransactionEntity(
                        id = 1,
                        amount = 35.0,
                        description = "Dinner with friends",
                        timestamp = 1_760_500_000_000L,
                        cycleKey = "2026-10",
                        tag = "Eating Out",
                        tagColor = "#FF7043"
                    ),
                    TransactionEntity(
                        id = 2,
                        amount = 6.50,
                        description = "Morning Latte",
                        timestamp = 1_760_410_000_000L,
                        cycleKey = "2026-10",
                        tag = "Coffee & Drinks",
                        tagColor = "#26A69A"
                    )
                )
            ),
            onNavigateToHistory = {},
            onNavigateToSettings = {},
            onOpenAddSheet = {},
            onCloseAddSheet = {},
            onAddExpense = { _, _, _, _ -> },
            onLoadMore = {}
        )
    }
}
