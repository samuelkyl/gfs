package ca.sleung.gfs.ui.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.sleung.gfs.R
import ca.sleung.gfs.data.local.db.entity.TagEntity
import ca.sleung.gfs.data.local.db.entity.TransactionEntity
import ca.sleung.gfs.data.repository.BudgetRepository
import ca.sleung.gfs.ui.theme.GFSThemeDefaults
import ca.sleung.gfs.ui.theme.TABULAR_FIGURES_FEATURE
import ca.sleung.gfs.ui.theme.parseHexColor
import ca.sleung.gfs.ui.util.CurrencyFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditExpenseBottomSheet(
    transaction: TransactionEntity,
    tags: List<TagEntity> = emptyList(),
    remainingBalance: Double? = null,
    isInActiveCycle: Boolean = false,
    onDismiss: () -> Unit,
    onUpdateExpense: (id: Long, amount: Double, description: String, timestamp: Long, tag: String?, tagColor: String?) -> Unit,
    onDeleteExpense: (id: Long) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val extendedColors = GFSThemeDefaults.extendedColors
    val defaultDescription = stringResource(R.string.default_expense_description)

    var amountText by rememberSaveable(transaction.id) {
        mutableStateOf(CurrencyFormatter.formatEditableAmount(transaction.amount))
    }
    var descriptionText by rememberSaveable(transaction.id) {
        mutableStateOf(transaction.description)
    }
    var selectedTagName by rememberSaveable(transaction.id) {
        mutableStateOf(transaction.tag?.takeIf { it.isNotBlank() })
    }
    var selectedTimestamp by rememberSaveable(transaction.id) {
        mutableLongStateOf(transaction.timestamp)
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    // Include transaction's original tag even if deleted from active tags list so edits preserve it
    val displayTags = remember(tags, transaction.tag, transaction.tagColor) {
        val originalTag = transaction.tag?.takeIf { it.isNotBlank() }
        if (originalTag != null && tags.none { it.name.equals(originalTag, ignoreCase = true) }) {
            listOf(
                TagEntity(
                    id = -1L,
                    name = originalTag,
                    colorHex = transaction.tagColor ?: BudgetRepository.DEFAULT_TAG_COLOR
                )
            ) + tags
        } else {
            tags
        }
    }

    val selectedTag = remember(displayTags, selectedTagName) {
        displayTags.find { it.name == selectedTagName }
    }

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a").withZone(ZoneId.systemDefault())
    }
    val dateString = remember(selectedTimestamp) {
        dateFormatter.format(Instant.ofEpochMilli(selectedTimestamp))
    }

    val amountValue = amountText.toDoubleOrNull() ?: 0.0
    val deltaOverBudget = remember(amountValue, remainingBalance, isInActiveCycle, transaction.amount) {
        if (isInActiveCycle && remainingBalance != null && amountValue > 0.0) {
            val projectedRemaining = remainingBalance + transaction.amount - amountValue
            projectedRemaining < 0.0
        } else {
            false
        }
    }

    fun handleUpdate() {
        if (amountValue > 0.0) {
            onUpdateExpense(
                transaction.id,
                amountValue,
                descriptionText.trim().ifBlank { selectedTag?.name ?: defaultDescription },
                selectedTimestamp,
                selectedTag?.name,
                selectedTag?.colorHex
            )
            onDismiss()
        }
    }

    val dateFieldInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(dateFieldInteractionSource) {
        dateFieldInteractionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                showDatePicker = true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with Delete (protected by confirmation) and Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sheet_edit_expense_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    CurrencyFormatter.sanitizeAmountInput(input)?.let { amountText = it }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.sheet_amount_label)) },
                leadingIcon = {
                    Text(
                        text = stringResource(R.string.currency_symbol),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TABULAR_FIGURES_FEATURE
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = extendedColors.positiveAccent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            )

            // Description Input
            OutlinedTextField(
                value = descriptionText,
                onValueChange = {
                    if (it.length <= BudgetRepository.MAX_DESCRIPTION_LENGTH) {
                        descriptionText = it
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.sheet_description_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { handleUpdate() }
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = extendedColors.positiveAccent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            )

            // Date Display / Picker Trigger (tapping anywhere on field opens DatePickerDialog)
            OutlinedTextField(
                value = dateString,
                onValueChange = {},
                readOnly = true,
                interactionSource = dateFieldInteractionSource,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.sheet_date_label)) },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = stringResource(R.string.sheet_select_date_cd),
                            tint = extendedColors.positiveAccent
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = extendedColors.positiveAccent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            )

            // Tags Selector
            if (displayTags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.sheet_assigned_tag_header),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "untagged_option") {
                            val isNoTagSelected = selectedTag == null
                            FilterChip(
                                selected = isNoTagSelected,
                                onClick = { selectedTagName = null },
                                label = {
                                    Text(
                                        text = stringResource(R.string.tag_untagged),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isNoTagSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isNoTagSelected,
                                    borderColor = Color.Transparent,
                                    selectedBorderColor = MaterialTheme.colorScheme.outline,
                                    selectedBorderWidth = 1.dp
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    labelColor = MaterialTheme.colorScheme.outline,
                                    selectedContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }

                        items(displayTags, key = { "${it.id}_${it.name}" }) { tagItem ->
                            val isSelected = selectedTag?.name == tagItem.name
                            val tagColor = parseHexColor(tagItem.colorHex)

                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedTagName = tagItem.name },
                                label = {
                                    Text(
                                        text = tagItem.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = tagColor,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(tagColor)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = Color.Transparent,
                                    selectedBorderColor = tagColor,
                                    selectedBorderWidth = 1.5.dp
                                ),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedContainerColor = tagColor.copy(alpha = 0.20f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }

            if (deltaOverBudget) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.sheet_overspending_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Update Button
            Button(
                onClick = { handleUpdate() },
                enabled = amountValue > 0.0,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = extendedColors.positiveAccent,
                    contentColor = extendedColors.onPositiveAccent
                )
            ) {
                Text(
                    text = stringResource(R.string.sheet_update_expense),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.dialog_delete_expense_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_delete_expense_body,
                        transaction.description,
                        CurrencyFormatter.formatCurrency(transaction.amount, alwaysShowCents = true)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteExpense(transaction.id)
                        onDismiss()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showDatePicker) {
        val initialLocalDate = remember(selectedTimestamp) {
            Instant.ofEpochMilli(selectedTimestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        val initialUtcMillis = remember(initialLocalDate) {
            initialLocalDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val pickedDate = Instant.ofEpochMilli(utcMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            val existingTime = Instant.ofEpochMilli(selectedTimestamp)
                                .atZone(ZoneId.systemDefault())
                                .toLocalTime()
                            selectedTimestamp = pickedDate
                                .atTime(existingTime)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_ok),
                        color = extendedColors.positiveAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
