package ca.sleung.gfs.ui.screens.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.sleung.gfs.BuildConfig
import ca.sleung.gfs.R
import ca.sleung.gfs.data.repository.BudgetRepository
import ca.sleung.gfs.domain.model.Tag
import ca.sleung.gfs.ui.theme.GFSTheme
import ca.sleung.gfs.ui.theme.GFSThemeDefaults
import ca.sleung.gfs.ui.theme.TABULAR_FIGURES_FEATURE
import ca.sleung.gfs.ui.theme.parseHexColor
import ca.sleung.gfs.ui.util.CurrencyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import kotlin.math.abs

private const val MAX_BACKUP_FILE_BYTES = 5 * 1024 * 1024 // 5 MB safety limit

private data class SwatchOption(val hex: String, val label: String)

private val COLOR_PALETTE = listOf(
    SwatchOption("#FF7043", "Coral"),
    SwatchOption("#FFA726", "Amber"),
    SwatchOption("#26A69A", "Teal"),
    SwatchOption("#42A5F5", "Sky Blue"),
    SwatchOption("#5C6BC0", "Indigo"),
    SwatchOption("#AB47BC", "Purple"),
    SwatchOption("#EC407A", "Rose"),
    SwatchOption("#2E7D32", "Forest Green")
)

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val tooLargeMsg = stringResource(R.string.snackbar_backup_too_large)
    val unreadableMsg = stringResource(R.string.snackbar_backup_unreadable)
    val exportFailedMsg = stringResource(R.string.snackbar_backup_export_failed)
    val chooserTitle = stringResource(R.string.settings_share_backup_chooser)

    // SAF OpenDocument file picker reading off the Main thread
    val importFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val readResult = withContext(Dispatchers.IO) {
                    runCatching {
                        val stream = context.contentResolver.openInputStream(uri)
                            ?: error("Unable to open input stream")
                        stream.use { input ->
                            val buffer = ByteArray(8192)
                            val out = ByteArrayOutputStream()
                            var totalRead = 0
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                totalRead += read
                                if (totalRead > MAX_BACKUP_FILE_BYTES) {
                                    return@use null // Exceeded 5 MB cap
                                }
                                out.write(buffer, 0, read)
                            }
                            out.toString(Charsets.UTF_8.name())
                        }
                    }
                }
                readResult.fold(
                    onSuccess = { content ->
                        if (content == null) {
                            snackbarHostState.showSnackbar(tooLargeMsg)
                        } else {
                            viewModel.stageImportBackup(content)
                        }
                    },
                    onFailure = {
                        snackbarHostState.showSnackbar(unreadableMsg)
                    }
                )
            }
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    SettingsContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onResetExtraSaved = viewModel::resetExtraSaved,
        onAddTag = viewModel::addTag,
        onDeleteTag = viewModel::deleteTag,
        onToggleHideDecimals = viewModel::setHideDecimalPlaces,
        onExportBackup = {
            coroutineScope.launch {
                runCatching {
                    val json = viewModel.getExportJson()
                    val shareIntent = createBackupShareIntent(context, json, chooserTitle)
                    context.startActivity(shareIntent)
                }.onFailure {
                    snackbarHostState.showSnackbar(exportFailedMsg)
                }
            }
        },
        onRequestImportBackup = {
            importFilePicker.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
        },
        onSaveSettings = viewModel::saveSettings,
        modifier = modifier
    )

    if (uiState.hasPendingImport) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelImportBackup() },
            title = { Text(stringResource(R.string.dialog_import_confirm_title)) },
            text = { Text(stringResource(R.string.dialog_import_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmImportBackup() }
                ) {
                    Text(
                        text = stringResource(R.string.dialog_import_confirm_action),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelImportBackup() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private suspend fun createBackupShareIntent(
    context: Context,
    json: String,
    chooserTitle: String
): Intent = withContext(Dispatchers.IO) {
    val backupsDir = File(context.cacheDir, "backups").apply { mkdirs() }
    // Clean up any stale temporary export files before writing a fresh backup
    backupsDir.listFiles()?.forEach { it.delete() }

    val fileName = "gfs_backup_${LocalDate.now()}.json"
    val backupFile = File(backupsDir, fileName)
    backupFile.writeText(json, Charsets.UTF_8)

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        backupFile
    )

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    Intent.createChooser(sendIntent, chooserTitle)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onResetExtraSaved: () -> Unit,
    onAddTag: (name: String, colorHex: String) -> Unit,
    onDeleteTag: (id: Long) -> Unit,
    onToggleHideDecimals: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onRequestImportBackup: () -> Unit,
    onSaveSettings: (monthlyBudget: Double, refillDay: Int, maxCap: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = GFSThemeDefaults.extendedColors
    val focusManager = LocalFocusManager.current

    var monthlyInput by rememberSaveable { mutableStateOf("") }
    var refillDayInput by rememberSaveable { mutableStateOf("") }
    var maxCapInput by rememberSaveable { mutableStateOf("") }
    var syncedRestoreGen by rememberSaveable { mutableIntStateOf(-1) }

    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var tagPendingDelete by remember { mutableStateOf<Tag?>(null) }
    var newTagName by rememberSaveable { mutableStateOf("") }
    var selectedColorHex by rememberSaveable { mutableStateOf(COLOR_PALETTE[0].hex) }

    // Initialize inputs once loaded AND re-sync whenever a backup is restored
    LaunchedEffect(uiState.isLoading, uiState.restoreGeneration) {
        if (!uiState.isLoading && syncedRestoreGen != uiState.restoreGeneration) {
            monthlyInput = CurrencyFormatter.formatEditableAmount(uiState.monthlyBudget)
            refillDayInput = uiState.refillDayOfMonth.toString()
            maxCapInput = if (uiState.maxRolloverCap == 0.0) {
                "0"
            } else {
                CurrencyFormatter.formatEditableAmount(uiState.maxRolloverCap)
            }
            syncedRestoreGen = uiState.restoreGeneration
        }
    }

    val parsedMonthly = CurrencyFormatter.parseAmount(monthlyInput)
    val parsedDay = refillDayInput.toIntOrNull()
    val parsedCap = CurrencyFormatter.parseAmount(maxCapInput)

    val isMonthlyError = syncedRestoreGen >= 0 && (parsedMonthly == null || parsedMonthly <= 0.0)
    val isDayError = syncedRestoreGen >= 0 && (parsedDay == null || parsedDay !in 1..31)
    val isCapError = syncedRestoreGen >= 0 && (parsedCap == null || parsedCap < 0.0)
    val isFormValid = !isMonthlyError && !isDayError && !isCapError &&
        parsedMonthly != null && parsedDay != null && parsedCap != null

    fun submitNewTag() {
        val trimmed = newTagName.trim()
        if (trimmed.isNotBlank()) {
            onAddTag(trimmed, selectedColorHex)
            newTagName = ""
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
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
                .imePadding()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Cycle Breakdown with Reset Extra Saved
            item(key = "cycle_breakdown") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_cycle_breakdown),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline,
                            letterSpacing = 1.sp
                        )
                        TextButton(onClick = { showResetDialog = true }) {
                            Text(
                                text = stringResource(R.string.settings_reset_extra_saved),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatTile(
                            label = stringResource(R.string.settings_tile_monthly),
                            value = CurrencyFormatter.formatCurrency(
                                uiState.monthlyBudget,
                                hideDecimals = uiState.hideDecimalPlaces
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        val rolloverColor = if (uiState.currentRollover < 0.0) {
                            extendedColors.negativeAccent
                        } else {
                            extendedColors.positiveAccent
                        }

                        StatTile(
                            label = stringResource(R.string.settings_tile_rollover),
                            value = CurrencyFormatter.formatCurrency(
                                uiState.currentRollover,
                                showPlusSign = true,
                                hideDecimals = uiState.hideDecimalPlaces
                            ),
                            valueColor = rolloverColor,
                            modifier = Modifier.weight(1f)
                        )
                        StatTile(
                            label = stringResource(R.string.settings_tile_extra_saved),
                            value = CurrencyFormatter.formatCurrency(
                                uiState.totalExtraSaved,
                                hideDecimals = uiState.hideDecimalPlaces
                            ),
                            valueColor = extendedColors.savedAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // CUSTOMIZE TAGS SECTION
            item(key = "customize_tags") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_customize_tags),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.tags_count,
                                uiState.tags.size,
                                uiState.tags.size
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    // Active tags flow row using accessible InputChip
                    if (uiState.tags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.tags.forEach { tagItem ->
                                val tagColor = parseHexColor(tagItem.colorHex)
                                InputChip(
                                    selected = false,
                                    onClick = { tagPendingDelete = tagItem },
                                    label = {
                                        Text(
                                            text = tagItem.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(tagColor)
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(
                                                R.string.settings_delete_tag_cd,
                                                tagItem.name
                                            ),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = InputChipDefaults.inputChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurface,
                                        trailingIconColor = MaterialTheme.colorScheme.outline
                                    ),
                                    border = InputChipDefaults.inputChipBorder(
                                        enabled = true,
                                        selected = false,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }

                    // Add Tag Input & Color Picker
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newTagName,
                                onValueChange = {
                                    if (it.length <= BudgetRepository.MAX_TAG_NAME_LENGTH) {
                                        newTagName = it
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.settings_new_tag_placeholder),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { submitNewTag() }
                                ),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = extendedColors.positiveAccent,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                            Button(
                                onClick = { submitNewTag() },
                                enabled = newTagName.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = extendedColors.positiveAccent,
                                    contentColor = extendedColors.onPositiveAccent
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_add_tag_button),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Accessible Color swatches row (RadioButton semantics + checkmark indicator)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            itemVerticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_color_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            COLOR_PALETTE.forEach { swatch ->
                                val color = parseHexColor(swatch.hex)
                                val isSelected = selectedColorHex == swatch.hex
                                val swatchCd = stringResource(R.string.settings_color_swatch_cd, swatch.label)

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .selectable(
                                            selected = isSelected,
                                            onClick = { selectedColorHex = swatch.hex },
                                            role = Role.RadioButton
                                        )
                                        .semantics { contentDescription = swatchCd },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 2.dp else 0.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // DATA MANAGEMENT & BACKUP SECTION
            item(key = "backup_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_backup_header),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = pluralStringResource(
                            R.plurals.settings_db_summary,
                            uiState.transactionCount,
                            uiState.transactionCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onExportBackup,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.IosShare,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.settings_export_json),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = onRequestImportBackup,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.settings_import_json),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Configuration Form with inline validation
            item(key = "configuration_form") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.settings_config_header),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = monthlyInput,
                        onValueChange = { next ->
                            val sanitized = CurrencyFormatter.sanitizeAmountInput(next) ?: return@OutlinedTextField
                            val prevMonthly = CurrencyFormatter.parseAmount(monthlyInput)
                            val prevCap = CurrencyFormatter.parseAmount(maxCapInput)
                            val wasTrackingDefault2x = prevMonthly != null &&
                                prevCap != null &&
                                abs(prevCap - prevMonthly * 2.0) < 0.01

                            monthlyInput = sanitized
                            val nextNum = CurrencyFormatter.parseAmount(sanitized)
                            if (wasTrackingDefault2x && nextNum != null && nextNum > 0.0 && nextNum * 2.0 <= CurrencyFormatter.MAX_INPUT_AMOUNT) {
                                maxCapInput = CurrencyFormatter.formatEditableAmount(nextNum * 2.0)
                            }
                        },
                        label = { Text(stringResource(R.string.settings_monthly_refill_label)) },
                        isError = isMonthlyError,
                        supportingText = if (isMonthlyError) {
                            { Text(stringResource(R.string.settings_error_monthly_budget)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
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

                    OutlinedTextField(
                        value = refillDayInput,
                        onValueChange = { next ->
                            CurrencyFormatter.sanitizeDayOfMonthInput(next)?.let { refillDayInput = it }
                        },
                        label = { Text(stringResource(R.string.settings_refill_day_label)) },
                        isError = isDayError,
                        supportingText = if (isDayError) {
                            { Text(stringResource(R.string.settings_error_refill_day)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
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

                    OutlinedTextField(
                        value = maxCapInput,
                        onValueChange = { next ->
                            CurrencyFormatter.sanitizeAmountInput(next)?.let { maxCapInput = it }
                        },
                        label = { Text(stringResource(R.string.settings_max_cap_label)) },
                        isError = isCapError,
                        supportingText = if (isCapError) {
                            { Text(stringResource(R.string.settings_error_max_cap)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (parsedMonthly != null && parsedDay != null && parsedCap != null && isFormValid) {
                                    focusManager.clearFocus()
                                    onSaveSettings(parsedMonthly, parsedDay, parsedCap)
                                }
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = extendedColors.positiveAccent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    )

                    // Hide decimal places toggle (on by default)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                            .toggleable(
                                value = uiState.hideDecimalPlaces,
                                role = Role.Switch,
                                onValueChange = onToggleHideDecimals
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_hide_decimals_label),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.settings_hide_decimals_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = uiState.hideDecimalPlaces,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = extendedColors.positiveAccent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            if (parsedMonthly != null && parsedDay != null && parsedCap != null && isFormValid) {
                                focusManager.clearFocus()
                                onSaveSettings(parsedMonthly, parsedDay, parsedCap)
                            }
                        },
                        enabled = isFormValid,
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
                            text = stringResource(R.string.settings_save_changes),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            item(key = "settings_bottom_spacer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.settings_version_footer, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = TABULAR_FIGURES_FEATURE
                        ),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Confirmation Dialog for Reset Extra Saved
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text(stringResource(R.string.dialog_reset_extra_title)) },
                text = { Text(stringResource(R.string.dialog_reset_extra_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onResetExtraSaved()
                            showResetDialog = false
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_reset_extra_confirm),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        // Confirmation Dialog for Tag Deletion
        tagPendingDelete?.let { tag ->
            AlertDialog(
                onDismissRequest = { tagPendingDelete = null },
                title = { Text(stringResource(R.string.dialog_delete_tag_title)) },
                text = { Text(stringResource(R.string.dialog_delete_tag_body, tag.name)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeleteTag(tag.id)
                            tagPendingDelete = null
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_delete_tag_action),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { tagPendingDelete = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFeatureSettings = TABULAR_FIGURES_FEATURE
            ),
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    GFSTheme(dynamicColor = false) {
        SettingsContent(
            uiState = SettingsUiState(
                monthlyBudget = 600.0,
                refillDayOfMonth = 1,
                maxRolloverCap = 1200.0,
                currentRollover = 180.0,
                totalExtraSaved = 340.0,
                tags = listOf(
                    Tag(1, "Eating Out", "#FF7043", 0),
                    Tag(2, "Shopping", "#AB47BC", 1),
                    Tag(3, "Coffee & Drinks", "#26A69A", 2)
                ),
                transactionCount = 24
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateBack = {},
            onResetExtraSaved = {},
            onAddTag = { _, _ -> },
            onDeleteTag = {},
            onToggleHideDecimals = {},
            onExportBackup = {},
            onRequestImportBackup = {},
            onSaveSettings = { _, _, _ -> }
        )
    }
}
