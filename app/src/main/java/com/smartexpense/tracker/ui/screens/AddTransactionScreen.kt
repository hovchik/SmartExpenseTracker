package com.smartexpense.tracker.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.smartexpense.tracker.R
import com.smartexpense.tracker.data.model.Category
import com.smartexpense.tracker.data.model.TransactionSource
import com.smartexpense.tracker.data.model.TransactionType
import com.smartexpense.tracker.ui.theme.*
import com.smartexpense.tracker.util.CurrencyUtils
import com.smartexpense.tracker.util.VoiceTransactionParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    categories: List<Category>,
    onAdd: (Double, String, String, TransactionType, TransactionSource, String, String, Long) -> Unit,
    onScanReceipt: () -> Unit,
    onNavigateBack: () -> Unit,
    currencyCode: String = "USD"
) {
    val context = LocalContext.current
    val currencySymbol = CurrencyUtils.symbolFor(currencyCode)
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var merchantName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    // Date/time selection
    var selectedDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateDisplayFormatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val displayDate = dateDisplayFormatter.format(Date(selectedDateMillis))

    // ── Voice input state ───────────────────────────────────────────
    var isListening by remember { mutableStateOf(false) }
    var showVoiceSheet by remember { mutableStateOf(false) }
    var voiceText by remember { mutableStateOf("") }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var selectedVoiceLanguage by remember { mutableStateOf("en-US") }
    // Track whether we used voice for the source field
    var usedVoice by remember { mutableStateOf(false) }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    // Clean up SpeechRecognizer on dispose
    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    // Set up recognition listener
    LaunchedEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                voiceError = null
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                voiceError = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.voice_error_no_match)
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> context.getString(R.string.voice_error_network)
                    SpeechRecognizer.ERROR_AUDIO -> context.getString(R.string.voice_error_audio)
                    else -> context.getString(R.string.voice_error_generic)
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull() ?: ""
                if (spokenText.isNotBlank()) {
                    voiceText = spokenText
                    // Parse the spoken text and fill form fields
                    val parsed = VoiceTransactionParser.parse(spokenText)
                    parsed.amount?.let { amount = it.toBigDecimal().stripTrailingZeros().toPlainString() }
                    parsed.description?.let { description = it }
                    parsed.type?.let { isExpense = it == TransactionType.EXPENSE }
                    parsed.category?.let { selectedCategory = it }
                    parsed.merchantName?.let { if (it.isNotBlank()) merchantName = it }
                    usedVoice = true
                    showError = false
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.firstOrNull()?.let { voiceText = it }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    fun startListening() {
        // Check permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            (context as? Activity)?.let {
                ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
            }
            return
        }
        if (speechRecognizer == null) {
            voiceError = context.getString(R.string.voice_not_available)
            return
        }
        voiceText = ""
        voiceError = null
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedVoiceLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                stringResource(R.string.add_transaction),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Voice Input Section ─────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.voice_input_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Language selector chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    VoiceLanguageChip("EN", "en-US", selectedVoiceLanguage) { selectedVoiceLanguage = it }
                    VoiceLanguageChip("РУ", "ru-RU", selectedVoiceLanguage) { selectedVoiceLanguage = it }
                    VoiceLanguageChip("ՀY", "hy-AM", selectedVoiceLanguage) { selectedVoiceLanguage = it }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mic button with animation
                val pulseAnim = rememberInfiniteTransition(label = "pulse")
                val scale by pulseAnim.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = EaseInOut),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "micScale"
                )
                val micColor by animateColorAsState(
                    targetValue = if (isListening) RedExpense else MaterialTheme.colorScheme.primary,
                    label = "micColor"
                )

                FilledIconButton(
                    onClick = {
                        if (isListening) stopListening() else startListening()
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .then(if (isListening) Modifier.scale(scale) else Modifier),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = micColor)
                ) {
                    Icon(
                        if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isListening)
                            stringResource(R.string.voice_stop)
                        else
                            stringResource(R.string.voice_start),
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = when {
                        isListening -> stringResource(R.string.voice_listening)
                        voiceText.isNotBlank() -> "\"$voiceText\""
                        else -> stringResource(R.string.voice_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = if (isListening)
                        RedExpense
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (voiceError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        voiceError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Type Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = isExpense,
                onClick = { isExpense = true },
                label = { Text(stringResource(R.string.expense)) },
                leadingIcon = {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = RedExpense.copy(alpha = 0.15f),
                    selectedLabelColor = RedExpense
                )
            )
            FilterChip(
                selected = !isExpense,
                onClick = { isExpense = false },
                label = { Text(stringResource(R.string.income)) },
                leadingIcon = {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = GreenIncome.copy(alpha = 0.15f),
                    selectedLabelColor = GreenIncome
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Amount
        OutlinedTextField(
            value = amount,
            onValueChange = {
                if (it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                    amount = it
                    showError = false
                }
            },
            label = { Text(stringResource(R.string.amount)) },
            leadingIcon = { Text(currencySymbol, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            isError = showError && amount.isEmpty(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        OutlinedTextField(
            value = description,
            onValueChange = { description = it; showError = false },
            label = { Text(stringResource(R.string.description)) },
            leadingIcon = { Icon(Icons.Filled.Description, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            isError = showError && description.isEmpty(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Date field (tap to open picker)
        OutlinedTextField(
            value = displayDate,
            onValueChange = {},
            label = { Text(stringResource(R.string.voice_date_label)) },
            leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Filled.EditCalendar, contentDescription = stringResource(R.string.voice_pick_date))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            readOnly = true,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Merchant Name
        OutlinedTextField(
            value = merchantName,
            onValueChange = { merchantName = it },
            label = { Text(stringResource(R.string.merchant_optional)) },
            leadingIcon = { Icon(Icons.Filled.Store, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Category Selection
        Text(
            stringResource(R.string.category),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories.filter {
                if (isExpense) !listOf("Salary", "Freelance", "Investment").contains(it.name)
                else listOf("Salary", "Freelance", "Investment", "Other").contains(it.name)
            }) { category ->
                FilterChip(
                    selected = selectedCategory == category.name,
                    onClick = { selectedCategory = category.name },
                    label = { Text(category.name, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notes
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.notes_optional)) },
            leadingIcon = { Icon(Icons.Filled.Notes, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            minLines = 2,
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Scan Receipt Button
        OutlinedButton(
            onClick = onScanReceipt,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.scan_receipt_instead))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Add Button
        Button(
            onClick = {
                val amountVal = amount.toDoubleOrNull()
                if (amountVal != null && amountVal > 0 && description.isNotEmpty()) {
                    onAdd(
                        amountVal,
                        description,
                        selectedCategory.ifEmpty { "Other" },
                        if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME,
                        if (usedVoice) TransactionSource.VOICE else TransactionSource.MANUAL,
                        merchantName,
                        notes,
                        selectedDateMillis
                    )
                    onNavigateBack()
                } else {
                    showError = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isExpense) RedExpense else GreenIncome
            )
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (isExpense) stringResource(R.string.add_expense) else stringResource(R.string.add_income),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }

        if (showError) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.error_valid_amount_description),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun VoiceLanguageChip(
    label: String,
    languageCode: String,
    selectedLanguage: String,
    onSelect: (String) -> Unit
) {
    FilterChip(
        selected = selectedLanguage == languageCode,
        onClick = { onSelect(languageCode) },
        label = { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium) },
        shape = RoundedCornerShape(20.dp)
    )
}
