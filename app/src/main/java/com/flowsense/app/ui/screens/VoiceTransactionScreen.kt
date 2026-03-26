package com.flowsense.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.flowsense.app.data.model.TransactionType
import com.flowsense.app.ui.theme.*
import com.flowsense.app.util.CurrencyUtils
import com.flowsense.app.util.NaturalLanguageParser

/**
 * Supported voice input languages.
 */
enum class VoiceLanguage(val code: String, val displayName: String, val flag: String) {
    ENGLISH("en-US", "English", "EN"),
    ARMENIAN("hy-AM", "Armenian", "HY"),
    RUSSIAN("ru-RU", "Russian", "RU")
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VoiceTransactionScreen(
    categories: List<String>,
    currencyCode: String,
    onAddTransaction: (String) -> String?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val currencySymbol = CurrencyUtils.symbolFor(currencyCode)

    // Permission state
    val audioPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    // Voice recognition state
    var isListening by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var partialText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedLanguage by remember { mutableStateOf(VoiceLanguage.ENGLISH) }
    var showLanguageMenu by remember { mutableStateOf(false) }

    // Parsed transaction result
    var parsedResult by remember { mutableStateOf<NaturalLanguageParser.ParsedInput?>(null) }
    var addedMessage by remember { mutableStateOf<String?>(null) }

    // Speech recognizer
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    // Pulsing animation for mic button while listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Set up recognition listener
    DisposableEffect(speechRecognizer) {
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                errorMessage = null
                partialText = ""
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                errorMessage = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Please try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap the mic and speak."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
                    SpeechRecognizer.ERROR_NETWORK -> "Network error. Voice recognition may need internet."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout. Check your connection."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                    else -> "Recognition error (code $error). Please try again."
                }
            }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedText = matches[0]
                    partialText = ""
                    // Parse the recognized text
                    parsedResult = NaturalLanguageParser.parse(recognizedText, categories)
                    if (parsedResult == null) {
                        errorMessage = "Could not extract a transaction. Try saying an amount, e.g. \"Coffee 500\""
                    }
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty()) {
                    partialText = partial[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        speechRecognizer.setRecognitionListener(listener)
        onDispose {
            speechRecognizer.destroy()
        }
    }

    fun startListening() {
        if (!audioPermission.status.isGranted) {
            audioPermission.launchPermissionRequest()
            return
        }
        recognizedText = ""
        parsedResult = null
        addedMessage = null
        errorMessage = null

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage.code)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        isListening = true
        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer.stopListening()
        isListening = false
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
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Voice Input",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Language selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Language",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoiceLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = {
                                selectedLanguage = lang
                                // Reset state when switching language
                                recognizedText = ""
                                parsedResult = null
                                addedMessage = null
                                errorMessage = null
                            },
                            label = {
                                Text(
                                    "${lang.flag} ${lang.displayName}",
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GreenPrimary.copy(alpha = 0.15f),
                                selectedLabelColor = GreenPrimary
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Permission request
        if (!audioPermission.status.isGranted) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = OrangeWarning.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.MicOff, null,
                        tint = OrangeWarning,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Microphone permission is required for voice input",
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { audioPermission.launchPermissionRequest() },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangeWarning)
                    ) {
                        Text("Grant Permission")
                    }
                    if (audioPermission.status.shouldShowRationale) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Voice input needs microphone access to convert your speech into transactions.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Mic button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer pulsing ring when listening
            if (isListening) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(GreenPrimary.copy(alpha = 0.15f))
                )
            }
            // Mic button
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (isListening) stopListening() else startListening()
                    },
                color = if (isListening) RedExpense else GreenPrimary,
                shape = CircleShape,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (isListening) "Stop listening" else "Start listening",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // Status text
        Text(
            when {
                isListening -> "Listening... Speak now"
                recognizedText.isNotEmpty() -> "Tap mic to try again"
                else -> "Tap the microphone and say your transaction"
            },
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = if (isListening) GreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Hint examples
        if (recognizedText.isEmpty() && !isListening) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = BluePrimary.copy(alpha = 0.05f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        "Examples:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = BluePrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val examples = when (selectedLanguage) {
                        VoiceLanguage.ENGLISH -> listOf(
                            "\"Coffee five dollars\"",
                            "\"Salary three thousand income\"",
                            "\"Taxi twelve fifty yesterday\""
                        )
                        VoiceLanguage.ARMENIAN -> listOf(
                            "\"\u054D\u0578\u0582\u0580\u0573 \u0570\u056B\u0576\u0563 \u0570\u0561\u0580\u0575\u0578\u0582\u0580 \u0564\u0580\u0561\u0574\"",
                            "\"\u0531\u0577\u056D\u0561\u057F\u0561\u057E\u0561\u0580\u0571 \u0565\u0580\u0565\u0584 \u0570\u0561\u0566\u0561\u0580 \u0564\u0580\u0561\u0574\"",
                            "\"\u054F\u0561\u0584\u057D\u056B \u0570\u0561\u0566\u0561\u0580 \u0564\u0580\u0561\u0574\""
                        )
                        VoiceLanguage.RUSSIAN -> listOf(
                            "\"\u041A\u043E\u0444\u0435 \u043F\u044F\u0442\u044C\u0441\u043E\u0442 \u0440\u0443\u0431\u043B\u0435\u0439\"",
                            "\"\u0417\u0430\u0440\u043F\u043B\u0430\u0442\u0430 \u043F\u044F\u0442\u044C\u0434\u0435\u0441\u044F\u0442 \u0442\u044B\u0441\u044F\u0447\"",
                            "\"\u0422\u0430\u043A\u0441\u0438 \u0442\u0440\u0438\u0441\u0442\u0430 \u0440\u0443\u0431\u043B\u0435\u0439 \u0432\u0447\u0435\u0440\u0430\""
                        )
                    }
                    examples.forEach { example ->
                        Text(
                            example,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Partial text while listening
        AnimatedVisibility(
            visible = isListening && partialText.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = GreenPrimary.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.GraphicEq, null,
                        tint = GreenPrimary, modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        partialText,
                        fontSize = 14.sp,
                        color = GreenPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Recognized text display
        if (recognizedText.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.RecordVoiceOver, null,
                            tint = BluePrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Recognized Text",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = BluePrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "\"$recognizedText\"",
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Parsed result
        if (parsedResult != null) {
            val result = parsedResult!!
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.type == TransactionType.EXPENSE)
                        RedExpense.copy(alpha = 0.08f)
                    else
                        GreenIncome.copy(alpha = 0.08f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (result.type == TransactionType.EXPENSE)
                                Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            null,
                            tint = if (result.type == TransactionType.EXPENSE) RedExpense else GreenIncome,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (result.type == TransactionType.EXPENSE) "Expense" else "Income",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (result.type == TransactionType.EXPENSE) RedExpense else GreenIncome
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Amount
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Amount", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$currencySymbol${String.format("%.2f", result.amount)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Description
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Description", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(result.description, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }

                    // Category
                    if (result.category != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Category", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = PurpleAccent.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    result.category,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PurpleAccent,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Tags
                    if (result.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tags", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(result.tags.joinToString(", ") { "#$it" }, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Add transaction button
            Button(
                onClick = {
                    val msg = onAddTransaction(recognizedText)
                    if (msg != null) {
                        addedMessage = msg
                        parsedResult = null
                        recognizedText = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (result.type == TransactionType.EXPENSE) RedExpense else GreenIncome
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Add ${if (result.type == TransactionType.EXPENSE) "Expense" else "Income"}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }

        // Error message
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = RedExpense.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.ErrorOutline, null,
                        tint = RedExpense, modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        errorMessage!!,
                        fontSize = 13.sp,
                        color = RedExpense
                    )
                }
            }
        }

        // Success message
        if (addedMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = GreenPrimary.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.CheckCircle, null,
                        tint = GreenPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Transaction Added!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = GreenPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        addedMessage!!,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            addedMessage = null
                            recognizedText = ""
                            parsedResult = null
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Mic, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Another")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
