package ca.sleung.gfs.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import ca.sleung.gfs.domain.model.CategoryBreakdown
import ca.sleung.gfs.ui.screens.components.CategoryPieChart
import ca.sleung.gfs.ui.screens.components.EditExpenseBottomSheet
import ca.sleung.gfs.ui.theme.GFSTheme
import ca.sleung.gfs.ui.theme.GFSThemeDefaults
import ca.sleung.gfs.ui.theme.TABULAR_FIGURES_FEATURE
import ca.sleung.gfs.ui.theme.parseHexColor
import ca.sleung.gfs.ui.util.CurrencyFormatter
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HistoryContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onSelectCycle = viewModel::selectCycle,
        onSelectTransaction = viewModel::selectTransaction,
        onDismissEdit = viewModel::clearSelectedTransaction,
        onUpdateExpense = viewModel::updateExpense,
        onDeleteExpense = viewModel::deleteExpense,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(
    uiState: HistoryUiState,
    onNavigateBack: () -> Unit,
    onSelectCycle: (String?) -> Unit,
    onSelectTransaction: (TransactionEntity) -> Unit,
    onDismissEdit: () -> Unit,
    onUpdateExpense: (id: Long, amount: Double, description: String, timestamp: Long, tag: String?, tagColor: String?) -> Unit,
    onDeleteExpense: (id: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = GFSThemeDefaults.extendedColors

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.history_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    Text(
                        text = stringResource(
                            R.string.history_spent_header,
                            CurrencyFormatter.formatCurrency(uiState.totalSpent)
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = TABULAR_FIGURES_FEATURE
                        ),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cycle Selector Pills
            item(key = "cycle_selector") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.history_select_cycle),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 0.5.sp
                    )
                    CycleSelectorRow(
                        availableCycles = uiState.availableCycles,
                        currentCycleKey = uiState.currentCycleKey,
                        selectedCycle = uiState.selectedCycle,
                        onSelectCycle = onSelectCycle
                    )
                }
            }

            // Monthly Breakdown Donut Chart
            item(key = "breakdown_chart") {
                CategoryPieChart(
                    breakdown = uiState.breakdown,
                    totalSpent = uiState.totalSpent
                )
            }

            // Section Header
            item(key = "tx_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.history_transactions_header),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = stringResource(R.string.history_tap_to_edit),
                        style = MaterialTheme.typography.labelSmall,
                        color = extendedColors.positiveAccent
                    )
                }
            }

            if (uiState.transactions.isEmpty()) {
                item(key = "empty_tx") {
                    Text(
                        text = stringResource(R.string.history_empty_cycle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(uiState.transactions, key = { it.id }) { tx ->
                    HistoryTransactionRow(
                        transaction = tx,
                        onClick = { onSelectTransaction(tx) }
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Edit Expense Sheet
        uiState.selectedTransaction?.let { tx ->
            EditExpenseBottomSheet(
                transaction = tx,
                tags = uiState.tags,
                remainingBalance = uiState.remainingBalance,
                isInActiveCycle = tx.cycleKey == uiState.currentCycleKey,
                onDismiss = onDismissEdit,
                onUpdateExpense = onUpdateExpense,
                onDeleteExpense = onDeleteExpense
            )
        }
    }
}

@Composable
private fun CycleSelectorRow(
    availableCycles: List<String>,
    currentCycleKey: String,
    selectedCycle: String?,
    onSelectCycle: (String?) -> Unit
) {
    val currentLabel = if (currentCycleKey.isNotBlank()) {
        "${stringResource(R.string.history_cycle_current)} (${formatCycleKey(currentCycleKey)})"
    } else {
        stringResource(R.string.history_cycle_current)
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Current Cycle Pill
        item(key = "cycle_current") {
            CycleChip(
                label = currentLabel,
                isSelected = selectedCycle == null,
                onClick = { onSelectCycle(null) }
            )
        }

        // Past Cycles (sorted newest-first, never duplicating current cycle)
        items(availableCycles, key = { it }) { cycleKey ->
            CycleChip(
                label = formatCycleKey(cycleKey),
                isSelected = selectedCycle == cycleKey,
                onClick = { onSelectCycle(cycleKey) }
            )
        }

        // All Time Pill
        item(key = "cycle_all") {
            CycleChip(
                label = stringResource(R.string.history_cycle_all_time),
                isSelected = selectedCycle == "ALL",
                onClick = { onSelectCycle("ALL") }
            )
        }
    }
}

@Composable
private fun CycleChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val extendedColors = GFSThemeDefaults.extendedColors

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        },
        shape = RoundedCornerShape(20.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = Color.Transparent,
            selectedBorderColor = extendedColors.positiveAccent,
            selectedBorderWidth = 1.5.dp
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = extendedColors.positiveAccent.copy(alpha = 0.18f),
            selectedLabelColor = extendedColors.positiveAccent
        )
    )
}

@Composable
private fun HistoryTransactionRow(
    transaction: TransactionEntity,
    onClick: () -> Unit
) {
    val extendedColors = GFSThemeDefaults.extendedColors
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a").withZone(ZoneId.systemDefault())
    }
    val dateText = remember(transaction.timestamp) {
        dateFormatter.format(Instant.ofEpochMilli(transaction.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
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

private fun formatCycleKey(cycleKey: String): String {
    return runCatching {
        val ym = YearMonth.parse(cycleKey)
        val formatter = DateTimeFormatter.ofPattern("MMM yyyy")
        ym.format(formatter)
    }.getOrDefault(cycleKey)
}

@PreviewLightDark
@Composable
private fun HistoryScreenPreview() {
    GFSTheme(dynamicColor = false) {
        HistoryContent(
            uiState = HistoryUiState(
                transactions = listOf(
                    TransactionEntity(
                        id = 1,
                        amount = 42.50,
                        description = "Ramen with friends",
                        timestamp = 1_760_500_000_000L,
                        cycleKey = "2026-10",
                        tag = "Eating Out",
                        tagColor = "#FF7043"
                    )
                ),
                totalSpent = 42.50,
                availableCycles = listOf("2026-09", "2026-08"),
                currentCycleKey = "2026-10",
                remainingBalance = 457.50,
                breakdown = listOf(
                    CategoryBreakdown("Eating Out", "#FF7043", 42.50, 100f)
                )
            ),
            onNavigateBack = {},
            onSelectCycle = {},
            onSelectTransaction = {},
            onDismissEdit = {},
            onUpdateExpense = { _, _, _, _, _, _ -> },
            onDeleteExpense = {}
        )
    }
}
