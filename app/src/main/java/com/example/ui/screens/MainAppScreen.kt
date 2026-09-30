package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import com.example.R
import com.example.data.model.ChatMessage
import com.example.data.model.ShoppingItem
import com.example.data.model.ShoppingList
import com.example.data.sync.ConnectionStatus
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.BentoViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import coil.compose.AsyncImage
import android.util.Log
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainAppScreen(
    viewModel: BentoViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    // Render screen transitions gracefully
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            AppScreen.CONNECT -> ConnectScreen(viewModel = viewModel)
            AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
        }
    }
}

// ---------------------------------------------------------------------------
// 🔑 Α. ΟΘΟΝΗ ΣΥΝΔΕΣΗΣ (Connect Screen)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(viewModel: BentoViewModel) {
    val context = LocalContext.current
    var inputName by remember { mutableStateOf(viewModel.userNickname) }
    var inputCode by remember { mutableStateOf(viewModel.coupleCode) }
    var inputServerUrl by remember { mutableStateOf(viewModel.wsServerUrl) }
    var isAdvancedExpanded by remember { mutableStateOf(false) }

    val logoPainter = painterResource(id = R.drawable.ic_launcher_custom_fg)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = if (isSystemInDarkTheme()) {
                        listOf(Color(0xFF141218), Color(0xFF1F1B24))
                    } else {
                        listOf(Color(0xFFFDF8FD), Color(0xFFF3EDF7))
                    }
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Elegant Bento Styled Logo Card
            Card(
                modifier = Modifier
                    .size(130.dp)
                    .shadow(10.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFCFAED))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = logoPainter,
                        contentDescription = "Couple Shopping List Logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Couple Shopping List",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "Real-time συνεργατική λίστα & live υπολογιστής κόστους για ζευγάρια",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Nickname Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Το Όνομα / Ψευδώνυμό σου:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nickname_input"),
                        placeholder = { Text("π.χ. Γιάννης, Μαρία...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Room / Couple Code Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Κωδικός Ζευγαριού (Couple Code):",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { inputCode = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("couple_code_input"),
                        placeholder = { Text("Συνδετικός κωδικός (π.χ. love123)") },
                        trailingIcon = {
                            IconButton(onClick = { inputCode = viewModel.generateRandom6DigitCode() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Auto Generate")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = "Μοιραστείτε αυτόν τον κωδικό με το σύντροφό σας για να βλέπετε τις λίστες σας σε πραγματικό χρόνο!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Advanced Connection Settings (Accordion URL Setup)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedExpanded = !isAdvancedExpanded }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.SettingsInputComponent, contentDescription = "Advanced Server Setup", tint = MaterialTheme.colorScheme.primary)
                            Text("Ρυθμίσεις Διακομιστή (Advanced)", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                        Icon(
                            imageVector = if (isAdvancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand Accordion"
                        )
                    }

                    AnimatedVisibility(visible = isAdvancedExpanded) {
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Custom WebSocket Server URL:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedTextField(
                                value = inputServerUrl,
                                onValueChange = { inputServerUrl = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("wss://couple-shopping.onrender.com") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            Text(
                                text = "Αφήστε κενό για αυτόματη σύνδεση στον προεπιλεγμένο server.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // BIG ACTION CONNECT BUTTON
            Button(
                onClick = {
                    if (inputName.isBlank()) inputName = viewModel.generateRandomNickname()
                    if (inputCode.isBlank()) inputCode = viewModel.generateRandom6DigitCode()
                    viewModel.connectUser(inputName, inputCode, inputServerUrl)
                    Toast.makeText(context, "Καλωσόρισες, $inputName! ✨", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("connect_submit_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "Connect Room", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Είσοδος & Σύνδεση Ζεύγους", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 🛒 Β. ΚΕΝΤΡΙΚΗ ΔΙΑΧΕΙΡΙΣΗ ΛΙΣΤΩΝ & PRODUCTS (Dashboard Screen)
// ---------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: BentoViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val lists by viewModel.allLists.collectAsState()
    val items by viewModel.allItems.collectAsState()
    val status by viewModel.connectionStatus.collectAsState()
    val selectedListId by viewModel.selectedListId.collectAsState()
    val partnerTyping by viewModel.partnerTypingName.collectAsState()
    val calculateCost by viewModel.calculateBasketCost.collectAsState()
    val isAlwaysOnEnabled by viewModel.isKeepScreenOn.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewModel.onAppResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE, androidx.lifecycle.Lifecycle.Event.ON_STOP -> viewModel.onAppPauseOrStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var fabOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    // Dialog state controllers
    var isListDropdownExpanded by remember { mutableStateOf(false) }
    var showAddListDialog by remember { mutableStateOf(false) }
    var showRenameListDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showClearBoughtConfirmDialog by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }
    var showManageSuggestionsDialog by remember { mutableStateOf(false) }
    var showCategoriesDialog by remember { mutableStateOf(false) }
    var showSuggestionsDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAiScanDialog by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var importDialogTab by remember { mutableStateOf(0) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            try {
                val bitmap = if (android.os.Build.VERSION.SDK_INT < 28) {
                    android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                }
                // Downscale to max 1024px for efficient scanning
                val width = bitmap.width
                val height = bitmap.height
                val maxDim = 1024
                selectedImageBitmap = if (width > maxDim || height > maxDim) {
                    val ratio = width.toFloat() / height.toFloat()
                    val newW = if (width > height) maxDim else (maxDim * ratio).toInt()
                    val newH = if (width > height) (maxDim / ratio).toInt() else maxDim
                    android.graphics.Bitmap.createScaledBitmap(bitmap, newW, newH, true)
                } else {
                    bitmap
                }
            } catch (e: Exception) {
                Log.e("BentoAI", "Error loading bitmap", e)
                Toast.makeText(context, "Σφάλμα κατά τη φόρτωση της εικόνας", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var editingItem by remember { mutableStateOf<ShoppingItem?>(null) }
    var itemToDelete by remember { mutableStateOf<ShoppingItem?>(null) }
    var listToRename by remember { mutableStateOf<ShoppingList?>(null) }
    var listToDelete by remember { mutableStateOf<ShoppingList?>(null) }
    var checkingItemForCost by remember { mutableStateOf<ShoppingItem?>(null) }

    // -------------------------------------------------------------
    // 🎙️ VOICE INPUT / SPEECH RECOGNITION (NO AI - 100% OFFLINE / LOCAL)
    // -------------------------------------------------------------
    var isVoiceRecording by remember { mutableStateOf(false) }
    var voiceRecognizedText by remember { mutableStateOf("") }
    var isSpeechRecognizerAvailable by remember {
        mutableStateOf(SpeechRecognizer.isRecognitionAvailable(context))
    }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var voiceRmsLevel by remember { mutableStateOf(0f) }

    // Initialize or release SpeechRecognizer with Lifecycle
    DisposableEffect(Unit) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer
        }
        onDispose {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    val speechRecognizerIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                voiceRecognizedText = spoken
                val added = viewModel.addVoiceSpokenItems(spoken, selectedListId)
                if (added.isNotEmpty()) {
                    Toast.makeText(context, "Προστέθηκε: ${added.joinToString(", ")} ✨", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Απαιτείται άδεια μικροφώνου για φωνητική προσθήκη", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Άδεια μικροφώνου δόθηκε! Κρατήστε πατημένο το μικρόφωνο για να μιλήσετε 🎙️", Toast.LENGTH_SHORT).show()
        }
    }

    // Function to start speech recognition
    fun startVoiceListening() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        val recognizer = speechRecognizer ?: run {
            val newRec = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = newRec
            newRec
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "el-GR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "el-GR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, "el-GR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Allow user to speak a continuous list without cutting off too fast
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Πείτε τη λίστα σας (π.χ. γάλα, ψωμί, 2 μήλα και τυρί)...")
        }

        voiceRecognizedText = ""
        voiceRmsLevel = 0f
        isVoiceRecording = true

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isVoiceRecording = true
            }

            override fun onBeginningOfSpeech() {
                isVoiceRecording = true
            }

            override fun onRmsChanged(rmsdB: Float) {
                voiceRmsLevel = rmsdB
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // Speech ended
            }

            override fun onError(error: Int) {
                isVoiceRecording = false
                voiceRmsLevel = 0f
                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Σφάλμα ήχου"
                    SpeechRecognizer.ERROR_CLIENT -> "Σφάλμα εφαρμογής"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Απαιτείται άδεια μικροφώνου"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Σφάλμα σύνδεσης"
                    SpeechRecognizer.ERROR_NO_MATCH -> "Δεν αναγνωρίστηκε ομιλία. Δοκιμάστε ξανά."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Ο αναγνωριστής είναι απασχολημένος"
                    SpeechRecognizer.ERROR_SERVER -> "Σφάλμα διακομιστή ομιλίας"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Δεν ακούστηκε ομιλία"
                    else -> "Σφάλμα φωνής ($error)"
                }
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResults(results: Bundle?) {
                isVoiceRecording = false
                voiceRmsLevel = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognized = matches?.firstOrNull() ?: voiceRecognizedText
                if (!recognized.isNullOrBlank()) {
                    voiceRecognizedText = recognized
                    val added = viewModel.addVoiceSpokenItems(recognized, selectedListId)
                    if (added.isNotEmpty()) {
                        Toast.makeText(
                            context,
                            "Προστέθηκε: ${added.joinToString(", ")} ✨",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    voiceRecognizedText = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        try {
            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e("BentoVoice", "Failed to start speech listening via SpeechRecognizer, trying intent", e)
            isVoiceRecording = false
            try {
                speechRecognizerIntentLauncher.launch(intent)
            } catch (ex: Exception) {
                Log.e("BentoVoice", "Failed to launch speech intent", ex)
                Toast.makeText(context, "Δεν βρέθηκε υπηρεσία αναγνώρισης φωνής στη συσκευή", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to stop speech recognition and process results
    fun stopVoiceListening() {
        if (isVoiceRecording) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e("BentoVoice", "Error stopping speech listening", e)
            }
        }
    }

    // Multi-selection state
    var selectedItemIds by remember { mutableStateOf(setOf<String>()) }
    val isMultiSelectMode = selectedItemIds.isNotEmpty()
    var showBulkListDialog by remember { mutableStateOf(false) }
    var showBulkPriorityDialog by remember { mutableStateOf(false) }

    // Collapsible states
    var isOpenActiveSection by remember { mutableStateOf(true) }
    var isOpenBoughtSection by remember { mutableStateOf(true) }
    var isChatOpen by remember { mutableStateOf(false) }

    // Pre-select current filter for list items
    val activeList = lists.find { it.id == selectedListId }
    val currentListName = activeList?.name ?: "Όλα τα Προϊόντα"

    // Animation details for status indicator
    val glowAnim = rememberInfiniteTransition(label = "glow")
    val alphaAnim by glowAnim.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                // Header Area with Info Row & Partner Typing Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val syncTimeFormatted = remember(lastSyncTime) {
                            if (lastSyncTime > 0) {
                                val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                sdf.format(java.util.Date(lastSyncTime))
                            } else ""
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Online Status Dot / Spinner
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (status) {
                                                ConnectionStatus.CONNECTED -> Color(0xFF10B981)
                                                ConnectionStatus.CONNECTING -> Color(0xFFF59E0B)
                                                ConnectionStatus.DISCONNECTED -> Color(0xFF6B7280)
                                            }
                                        )
                                )
                            }
                            Text(
                                text = when {
                                    isSyncing -> "Συγχρονισμός... 🔄"
                                    status == ConnectionStatus.CONNECTED -> "Συνδεδεμένο & Συγχρονισμένο ✓ ${if (syncTimeFormatted.isNotEmpty()) "($syncTimeFormatted)" else ""}"
                                    status == ConnectionStatus.CONNECTING -> "Σύνδεση στο Cloud... ⏳"
                                    else -> "Τοπική λειτουργία (Offline)"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSyncing) MaterialTheme.colorScheme.primary else when (status) {
                                    ConnectionStatus.CONNECTED -> Color(0xFF059669)
                                    ConnectionStatus.CONNECTING -> Color(0xFFD97706)
                                    ConnectionStatus.DISCONNECTED -> Color(0xFF6B7280)
                                }
                            )
                        }

                        // Small row of action options: Refresh, DarkTheme, AlwaysOn
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            // 1. Refresh Button (Small)
                            IconButton(
                                onClick = {
                                    viewModel.refreshData()
                                    Toast.makeText(context, "Όλα τα δεδομένα είναι συγχρονισμένα! ✓", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("refresh_sync_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = if (isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // 2. Day/Night Theme Button (Small)
                            val isDark = viewModel.isDarkMode.value
                            IconButton(
                                onClick = { viewModel.toggleDarkMode() },
                                modifier = Modifier
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Night Mode Toggle",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // 3. Always On Screen Button (Small)
                            IconButton(
                                onClick = {
                                    viewModel.toggleKeepScreenOn()
                                    val msg = if (!isAlwaysOnEnabled) "Αναμμένη Οθόνη: Ενεργοποιήθηκε 💡" else "Αναμμένη Οθόνη: Απενεργοποιήθηκε 🔌"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("always_on_header_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = "Always On Screen",
                                    tint = if (isAlwaysOnEnabled) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // 4. Basket Cost Calculation Toggle Button (Small)
                            IconButton(
                                onClick = {
                                    viewModel.toggleCalculateBasketCost()
                                    val msg = if (!calculateCost) "Υπολογισμός Κόστους: Ενεργοποιήθηκε 💶" else "Υπολογισμός Κόστους: Απενεργοποιήθηκε 🔌"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("basket_cost_header_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = "Calculate Basket Cost",
                                    tint = if (calculateCost) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Top Action Column (Right Side)
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quick BabbleChat Icon Button with pulse badge
                            val unreadCount by viewModel.unreadMessagesCount.collectAsState()
                            IconButton(
                                onClick = { 
                                    isChatOpen = !isChatOpen 
                                    viewModel.setChatOpen(isChatOpen)
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("chat_toggle_bubble")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (partnerTyping != null) {
                                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                                Text("Typing...")
                                            }
                                        } else if (unreadCount > 0) {
                                            Badge(containerColor = MaterialTheme.colorScheme.error) {
                                                Text("$unreadCount")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubble,
                                        contentDescription = "Open Chat",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Quick Import / Text Paste Button
                            IconButton(
                                onClick = {
                                    importDialogTab = 1
                                    showAiScanDialog = true
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("top_bar_import_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Εισαγωγή Προϊόντων",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Settings Icon
                            IconButton(
                                onClick = { showSettingsDialog = true },
                                modifier = Modifier
                                    .size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Total Cost displayed prominently right under the settings button ONLY if calculateCost is enabled
                        if (calculateCost) {
                            val checkedSum = viewModel.getCheckedTotal(selectedListId, items)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = String.format("%.2f €", checkedSum),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Space-saving Dropdown Category Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        // Current selection button
                        OutlinedCard(
                            onClick = { isListDropdownExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.08f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth().testTag("list_category_dropdown_trigger")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingBag,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (selectedListId == "all") "Όλες οι Λίστες" else currentListName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Expand lists list",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isListDropdownExpanded,
                            onDismissRequest = { isListDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.72f)
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingBag,
                                        contentDescription = null,
                                        tint = if (selectedListId == "all") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = { 
                                    Text(
                                        text = "Όλες οι Λίστες",
                                        fontWeight = if (selectedListId == "all") FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedListId == "all") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    ) 
                                },
                                onClick = {
                                    viewModel.selectList("all")
                                    isListDropdownExpanded = false
                                }
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            lists.forEach { list ->
                                val isSelected = selectedListId == list.id
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.List,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    text = { 
                                        Text(
                                            text = list.name,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        ) 
                                    },
                                    onClick = {
                                        viewModel.selectList(list.id)
                                        isListDropdownExpanded = false
                                    }
                                )
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Δημιουργία Νέας Λίστας",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "+ Δημιουργία Νέας Λίστας...",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    isListDropdownExpanded = false
                                    showAddListDialog = true
                                }
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .offset { IntOffset(fabOffset.x.roundToInt(), fabOffset.y.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress { change, dragAmount ->
                            change.consume()
                            fabOffset = androidx.compose.ui.geometry.Offset(
                                x = fabOffset.x + dragAmount.x,
                                y = fabOffset.y + dragAmount.y
                            )
                        }
                    }
            ) {
                // 1. 🎙️ Floating Microphone Button (Click to start/stop listening, auto-adds whole list without AI)
                val micScale by animateFloatAsState(
                    targetValue = if (isVoiceRecording) 1.25f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "mic_scale"
                )
                val micPulseInfinite = rememberInfiniteTransition(label = "mic_pulse")
                val micPulseAlpha by micPulseInfinite.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_alpha"
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(68.dp)
                ) {
                    // Pulsing wave halo when actively listening
                    if (isVoiceRecording) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .scale(micScale)
                                .background(
                                    Color(0xFFEF4444).copy(alpha = micPulseAlpha * 0.35f),
                                    CircleShape
                                )
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            if (!isVoiceRecording) {
                                startVoiceListening()
                            } else {
                                stopVoiceListening()
                            }
                        },
                        shape = CircleShape,
                        containerColor = if (isVoiceRecording) Color(0xFFEF4444) else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isVoiceRecording) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = if (isVoiceRecording) 10.dp else 4.dp,
                            pressedElevation = 12.dp
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .scale(micScale)
                            .testTag("voice_mic_fab")
                    ) {
                        Icon(
                            imageVector = if (isVoiceRecording) Icons.Default.Mic else Icons.Default.MicNone,
                            contentDescription = "Φωνητική Προσθήκη Λίστας (Πατήστε για έναρξη/διακοπή)",
                            tint = if (isVoiceRecording) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // 2. Standard Add Item FAB (+)
                FloatingActionButton(
                    onClick = { showAddItemDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("add_item_fab")
                ) {
                    Icon(Icons.Default.Add, "Add Item", modifier = Modifier.size(24.dp))
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // -------------------------------------------------------------
                // 📦 Γ. ΔΙΑΧΕΙΡΙΣΗ ΠΡΟΪΟΝΤΩΝ (Active Items & Bought Items Lists)
                // -------------------------------------------------------------

                // 1. «Προς Αγορά» Header Row
                item {
                    val activeListItemsCount = items.count { (selectedListId == "all" || it.listIds.contains(selectedListId)) && !it.bought }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isOpenActiveSection = !isOpenActiveSection }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Προς Αγορά ($activeListItemsCount)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (isOpenActiveSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle active section"
                        )
                    }
                }

                if (isOpenActiveSection) {
                    val activeListItems = items.filter { (selectedListId == "all" || it.listIds.contains(selectedListId)) && !it.bought }
                        .sortedWith(
                            compareBy<ShoppingItem> {
                                when (it.priority) {
                                    "HIGH" -> 0
                                    "MEDIUM" -> 1
                                    else -> 2
                                }
                            }.thenByDescending { it.updatedAt }
                        )

                    if (activeListItems.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, "No items remaining", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(36.dp))
                                    Text(
                                        text = "Δεν υπάρχουν εκκρεμή προϊόντα! Προσθέστε μερικά χρησιμοποιώντας το κουμπί + κάτω δεξιά. 🎉",
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(activeListItems, key = { it.id }) { item ->
                            ShoppingItemRow(
                                item = item,
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = selectedItemIds.contains(item.id),
                                modifier = Modifier.animateItem(),
                                onToggle = {
                                    if (calculateCost) {
                                        checkingItemForCost = item
                                    } else {
                                        viewModel.toggleItem(item)
                                    }
                                },
                                onEdit = {
                                    editingItem = item
                                    showAddItemDialog = true
                                },
                                onDelete = { itemToDelete = item },
                                onLongClick = {
                                    selectedItemIds = selectedItemIds + item.id
                                },
                                onSelectToggle = {
                                    selectedItemIds = if (selectedItemIds.contains(item.id)) {
                                        selectedItemIds - item.id
                                    } else {
                                        selectedItemIds + item.id
                                    }
                                }
                            )
                        }
                    }
                }

                // 2. «Στο Καλάθι» Header Row
                item {
                    val boughtListItemsCount = items.count { (selectedListId == "all" || it.listIds.contains(selectedListId)) && it.bought }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isOpenBoughtSection = !isOpenBoughtSection }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Στο Καλάθι ($boughtListItemsCount)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (boughtListItemsCount > 0) {
                                TextButton(
                                    onClick = { showClearBoughtConfirmDialog = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Clear Basket",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Καθαρισμός", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Icon(
                                imageVector = if (isOpenBoughtSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle bought items"
                            )
                        }
                    }
                }

                if (isOpenBoughtSection) {
                    val boughtListItems = items.filter { (selectedListId == "all" || it.listIds.contains(selectedListId)) && it.bought }
                        .sortedByDescending { it.updatedAt }

                    if (boughtListItems.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "Το καλάθι σας είναι άδειο. Κάντε tick σε κάποιο προϊόν για να το μεταφέρετε εδώ!",
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(24.dp),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(boughtListItems, key = { it.id }) { item ->
                            ShoppingItemRow(
                                item = item,
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = selectedItemIds.contains(item.id),
                                modifier = Modifier.animateItem(),
                                onToggle = { viewModel.toggleItem(item) },
                                onEdit = {
                                    editingItem = item
                                    showAddItemDialog = true
                                },
                                onDelete = { itemToDelete = item },
                                onLongClick = {
                                    selectedItemIds = selectedItemIds + item.id
                                },
                                onSelectToggle = {
                                    selectedItemIds = if (selectedItemIds.contains(item.id)) {
                                        selectedItemIds - item.id
                                    } else {
                                        selectedItemIds + item.id
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Bar overlay when multi-selection mode is active
            if (isMultiSelectMode) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedItemIds = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                            }
                            Text(
                                text = "${selectedItemIds.size} επιλεγμένα",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Bulk List assignment button
                            IconButton(onClick = { showBulkListDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Bulk List",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Bulk Priority button
                            IconButton(onClick = { showBulkPriorityDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = "Bulk Priority",
                                    tint = Color(0xFFD97706)
                                )
                            }

                            // Bulk Toggle Bought button
                            IconButton(
                                onClick = {
                                    viewModel.bulkToggleBought(selectedItemIds, true)
                                    Toast.makeText(context, "Μεταφέρθηκαν στο καλάθι", Toast.LENGTH_SHORT).show()
                                    selectedItemIds = emptySet()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Bulk Bought",
                                    tint = Color(0xFF10B981)
                                )
                            }

                            // Bulk Delete button
                            IconButton(
                                onClick = {
                                    viewModel.bulkDelete(selectedItemIds)
                                    Toast.makeText(context, "Διαγράφηκαν επιλεγμένα προϊόντα", Toast.LENGTH_SHORT).show()
                                    selectedItemIds = emptySet()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Bulk Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Realtime partner typing banner anchored above chat indicator
            if (partnerTyping != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .shadow(4.dp, RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Το $partnerTyping πληκτρολογεί...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 🎙️ Voice Recording Active Visual Banner
            AnimatedVisibility(
                visible = isVoiceRecording,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 20.dp, end = 20.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .testTag("voice_listening_overlay")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Ακρόαση",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Σας ακούω... (χωρίς AI)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                                Text(
                                    text = if (voiceRecognizedText.isNotBlank())
                                        "\"$voiceRecognizedText\""
                                    else
                                        "Πείτε όλη τη λίστα, π.χ. «γάλα, 2 ψωμιά, μήλα και φέτα»",
                                    fontSize = 12.sp,
                                    fontWeight = if (voiceRecognizedText.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Stop button
                            IconButton(
                                onClick = { stopVoiceListening() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Τέλος",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------
    // 💬 Ε. ΕΝΣΩΜΑΤΩΜΕΝΟ BABBLECHAT (Overlay Sliding Panel inside Modal Sheet)
    // -------------------------------------------------------------
    if (isChatOpen) {
        ModalBottomSheet(
            onDismissRequest = { 
                isChatOpen = false 
                viewModel.setChatOpen(false)
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.testTag("babble_chat_panel"),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            BabbleChatView(viewModel = viewModel, onClose = { 
                isChatOpen = false 
                viewModel.setChatOpen(false)
            })
        }
    }

    // -------------------------------------------------------------
    // MODALS: Dialog box triggers
    // -------------------------------------------------------------

    // 1. New List Dialog
    if (showAddListDialog) {
        var newListName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddListDialog = false },
            title = { Text("Δημιουργία Νέας Λίστας") },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    placeholder = { Text("π.χ. Μανάβικο, Σκλαβενίτης 🥦") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_list_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newListName.isNotBlank()) {
                            viewModel.addList(newListName.trim())
                            showAddListDialog = false
                        }
                    },
                    modifier = Modifier.testTag("new_list_confirm_btn")
                ) {
                    Text("Προσθήκη")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddListDialog = false }) { Text("Ακύρωση") }
            }
        )
    }

    // 1.5 Rename List Dialog
    if (showRenameListDialog) {
        var editedListName by remember { mutableStateOf(currentListName) }
        AlertDialog(
            onDismissRequest = { showRenameListDialog = false },
            title = { Text("Μετονομασία Λίστας") },
            text = {
                OutlinedTextField(
                    value = editedListName,
                    onValueChange = { editedListName = it },
                    placeholder = { Text("π.χ. Μανάβικο, Σκλαβενίτης 🥦") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("rename_list_name_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editedListName.isNotBlank()) {
                            viewModel.renameList(selectedListId, editedListName.trim())
                            showRenameListDialog = false
                        }
                    },
                    modifier = Modifier.testTag("rename_list_confirm_btn")
                ) {
                    Text("Αποθήκευση")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameListDialog = false }) { Text("Ακύρωση") }
            }
        )
    }

    // 2. Confirm current active List deletion Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Διαγραφή Λίστας") },
            text = { Text("Είστε βέβαιοι ότι θέλετε να διαγράψετε οριστικά τη λίστα κατηγορίας \"$currentListName\" και όλα τα περιεχόμενά της;") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.deleteCurrentList()
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("Διαγραφή", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Ακύρωση") }
            }
        )
    }

    // 2.2 Confirm Clear Basket Items Dialog
    if (showClearBoughtConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearBoughtConfirmDialog = false },
            title = { Text("Καθαρισμός Καλαθιού") },
            text = { Text("Είστε βέβαιοι ότι θέλετε να διαγράψετε όλα τα προϊόντα που βρίσκονται \"Στο Καλάθι\";") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.clearBoughtItems()
                        showClearBoughtConfirmDialog = false
                    }
                ) {
                    Text("Καθαρισμός", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearBoughtConfirmDialog = false }) { Text("Ακύρωση") }
            }
        )
    }

    // 2.5 Confirm specific item deletion Dialog
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Διαγραφή Προϊόντος")
                }
            },
            text = { Text("Είστε βέβαιοι ότι θέλετε να διαγράψετε το προϊόν \"${itemToDelete?.name}\";") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        itemToDelete?.let { viewModel.deleteItem(it) }
                        itemToDelete = null
                    },
                    modifier = Modifier.testTag("delete_item_confirm_btn")
                ) {
                    Text("Διαγραφή", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Ακύρωση") }
            }
        )
    }

    // 3. Add/Edit Shopping Item Dialog Modal
    if (showAddItemDialog) {
        var itemName by remember { mutableStateOf(editingItem?.name ?: "") }
        var itemQty by remember { mutableStateOf(editingItem?.qty ?: "1") }
        var itemPriceStr by remember { mutableStateOf(editingItem?.price?.toString() ?: "") }
        var itemPriority by remember { mutableStateOf(editingItem?.priority ?: "LOW") }
        val itemSuggestions by viewModel.itemSuggestions.collectAsState()
        var itemSelectedLists by remember {
            mutableStateOf(editingItem?.listIds?.toSet() ?: setOf(selectedListId))
        }

        AlertDialog(
            onDismissRequest = {
                // Do nothing to prevent dismissal on back press
            },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            title = { Text(if (editingItem == null) "Προσθήκη Προϊόντος" else "Επεξεργασία Προϊόντος") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                        // Option for Bulk Import via Text Paste / AI if adding new item
                        if (editingItem == null) {
                            OutlinedButton(
                                onClick = {
                                    showAddItemDialog = false
                                    importDialogTab = 1
                                    showAiScanDialog = true
                                },
                                modifier = Modifier.fillMaxWidth().testTag("bulk_paste_from_add_dialog_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("📋 Μαζική Εισαγωγή με Επικόλληση / AI", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Item Name
                        OutlinedTextField(
                            value = itemName,
                            onValueChange = { itemName = it },
                            label = { Text("Όνομα Προϊόντος") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        startVoiceListening()
                                        Toast.makeText(context, "Πείτε το προϊόν (π.χ. ψωμί ολικής)... 🎙️", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("dialog_voice_input_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isVoiceRecording) Icons.Default.Mic else Icons.Default.MicNone,
                                        contentDescription = "Φωνητική υπαγόρευση",
                                        tint = if (isVoiceRecording) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("item_name_input"),
                            placeholder = { Text("π.χ. Γιαούρτι Στραγγιστό") }
                        )

                        // Quick name suggestion dropdown
                        if (itemSuggestions.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Γρήγορες επιλογές:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(
                                    onClick = { showManageSuggestionsDialog = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(24.dp).testTag("manage_quick_options_btn")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Manage suggestions", modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Επεξεργασία", fontSize = 11.sp)
                                }
                            }

                            var isSuggestionsDropdownExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedCard(
                                    onClick = { isSuggestionsDropdownExpanded = true },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("suggestions_dropdown_trigger")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (itemName.isBlank()) "Επιλογή από γρήγορες προτάσεις..." else "Επιλεγμένο: $itemName",
                                            fontSize = 13.sp,
                                            color = if (itemName.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                            fontWeight = if (itemName.isBlank()) FontWeight.Normal else FontWeight.SemiBold
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Expand suggestions list",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = isSuggestionsDropdownExpanded,
                                    onDismissRequest = { isSuggestionsDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    itemSuggestions.forEach { suggestion ->
                                        DropdownMenuItem(
                                            text = { Text(suggestion) },
                                            onClick = {
                                                itemName = suggestion
                                                isSuggestionsDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Quantity and Price side-by-side
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = itemQty,
                                onValueChange = { itemQty = it },
                                label = { Text("Ποσότητα") },
                                singleLine = true,
                                modifier = Modifier.weight(1.2f).testTag("item_qty_input"),
                                placeholder = { Text("π.χ. 2, 1.5 κιλό") }
                            )
                            OutlinedTextField(
                                value = itemPriceStr,
                                onValueChange = { itemPriceStr = it },
                                label = { Text("Τιμή (€)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("item_price_input"),
                                placeholder = { Text("π.χ. 1.45") }
                            )
                        }

                        // Priority Selector Selection
                        Text("Προτεραιότητα αγοράς:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val priorities = listOf(
                                Triple("LOW", "Όταν μπορέσω ☕", Color(0xFFDCFCE7) to Color(0xFF15803D)),
                                Triple("MEDIUM", "Επείγον ⚠️", Color(0xFFFEF3C7) to Color(0xFFB45309)),
                                Triple("HIGH", "Πολύ Επείγον 🚨", Color(0xFFFFE4E6) to Color(0xFFBE123C))
                            )
                            priorities.forEach { (pCode, pLabel, pColors) ->
                                val isSel = itemPriority == pCode
                                Card(
                                    onClick = { itemPriority = pCode },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSel) pColors.first else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    border = if (isSel) BorderStroke(1.5.dp, pColors.second) else null,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = pLabel,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSel) pColors.second else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // List category selection picker dropdown replacement
                        Text("Κατηγορίες / Λίστες:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        var isCategorySelectorBtnExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedCard(
                                onClick = { isCategorySelectorBtnExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth().testTag("add_item_category_dropdown_trigger")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val selectedNames = lists.filter { itemSelectedLists.contains(it.id) }.map { it.name }
                                    val textToShow = if (selectedNames.isEmpty()) "Επιλογή Λιστών..." else selectedNames.joinToString(", ")
                                    Text(
                                        text = textToShow,
                                        fontSize = 13.sp,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (selectedNames.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Expand Lists",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = isCategorySelectorBtnExpanded,
                                onDismissRequest = { isCategorySelectorBtnExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                lists.forEach { list ->
                                    val isSelected = itemSelectedLists.contains(list.id)
                                    DropdownMenuItem(
                                        leadingIcon = {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    itemSelectedLists = if (checked == true) {
                                                        itemSelectedLists + list.id
                                                    } else {
                                                        itemSelectedLists - list.id
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                        },
                                        text = { Text(list.name) },
                                        onClick = {
                                            itemSelectedLists = if (isSelected) {
                                                itemSelectedLists - list.id
                                            } else {
                                                itemSelectedLists + list.id
                                            }
                                        }
                                    )
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                DropdownMenuItem(
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Δημιουργία Νέας Λίστας",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = "+ Δημιουργία Νέας Λίστας...",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    onClick = {
                                        isCategorySelectorBtnExpanded = false
                                        showAddListDialog = true
                                    }
                                )
                            }
                        }
                    }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (itemName.isNotBlank()) {
                            val priceVal = itemPriceStr.toDoubleOrNull()
                            viewModel.addOrUpdateItem(
                                id = editingItem?.id,
                                name = itemName.trim(),
                                qty = itemQty.trim().ifBlank { "1" },
                                price = priceVal,
                                listIds = itemSelectedLists.toList(),
                                bought = editingItem?.bought ?: false,
                                priority = itemPriority
                            )
                            showAddItemDialog = false
                            editingItem = null
                        }
                    },
                    modifier = Modifier.testTag("item_confirm_save_btn")
                ) {
                    Text("Αποθήκευση")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddItemDialog = false
                        editingItem = null
                    }
                ) {
                    Text("Ακύρωση")
                }
            }
        )
    }

    // 3.1 Compact Qty & Price dialog when checking an item with cost calculation enabled
    if (checkingItemForCost != null) {
        val itemToCheck = checkingItemForCost!!
        var checkQty by remember(itemToCheck) { mutableStateOf(itemToCheck.qty.ifEmpty { "1" }) }
        var checkPriceStr by remember(itemToCheck) { mutableStateOf(itemToCheck.price?.toString() ?: "") }

        AlertDialog(
            onDismissRequest = { checkingItemForCost = null },
            title = { Text("Ποσότητα & Τιμή") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = itemToCheck.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = checkQty,
                            onValueChange = { checkQty = it },
                            label = { Text("Ποσότητα") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("check_qty_input"),
                            placeholder = { Text("1") }
                        )
                        OutlinedTextField(
                            value = checkPriceStr,
                            onValueChange = { checkPriceStr = it },
                            label = { Text("Τιμή (€)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("check_price_input"),
                            placeholder = { Text("0.00") }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newPrice = checkPriceStr.toDoubleOrNull()
                        viewModel.addOrUpdateItem(
                            id = itemToCheck.id,
                            name = itemToCheck.name,
                            qty = checkQty.trim().ifBlank { "1" },
                            price = newPrice,
                            listIds = itemToCheck.listIds,
                            bought = true,
                            priority = itemToCheck.priority
                        )
                        checkingItemForCost = null
                    },
                    modifier = Modifier.testTag("confirm_check_item_btn")
                ) {
                    Text("Προσθήκη στο Καλάθι")
                }
            },
            dismissButton = {
                TextButton(onClick = { checkingItemForCost = null }) {
                    Text("Ακύρωση")
                }
            }
        )
    }

    // 3.2 Bulk List Selection Dialog
    if (showBulkListDialog) {
        AlertDialog(
            onDismissRequest = { showBulkListDialog = false },
            title = { Text("Μαζική Μεταφορά σε Λίστα") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    lists.forEach { list ->
                        Surface(
                            onClick = {
                                viewModel.bulkUpdateLists(selectedItemIds, list.id)
                                Toast.makeText(context, "Μεταφέρθηκαν ${selectedItemIds.size} προϊόντα στη λίστα '${list.name}'", Toast.LENGTH_SHORT).show()
                                selectedItemIds = emptySet()
                                showBulkListDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(text = list.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBulkListDialog = false }) { Text("Ακύρωση") }
            }
        )
    }

    // 3.3 Bulk Priority Dialog
    if (showBulkPriorityDialog) {
        AlertDialog(
            onDismissRequest = { showBulkPriorityDialog = false },
            title = { Text("Μαζική Αλλαγή Προτεραιότητας") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val priorities = listOf(
                        Triple("HIGH", "🔥 Υψηλή", Color(0xFFE11D48)),
                        Triple("MEDIUM", "⚡ Μεσαία", Color(0xFFD97706)),
                        Triple("LOW", "🟢 Χαμηλή", Color(0xFF059669))
                    )
                    priorities.forEach { (pKey, pLabel, pColor) ->
                        Surface(
                            onClick = {
                                viewModel.bulkUpdatePriority(selectedItemIds, pKey)
                                Toast.makeText(context, "Ενημερώθηκε η προτεραιότητα σε $pLabel", Toast.LENGTH_SHORT).show()
                                selectedItemIds = emptySet()
                                showBulkPriorityDialog = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = pColor.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = pLabel, fontWeight = FontWeight.Bold, color = pColor, fontSize = 15.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBulkPriorityDialog = false }) { Text("Ακύρωση") }
            }
        )
    }

    // 3.5 Manage Suggestions Dialog
    if (showAddItemDialog && showManageSuggestionsDialog) {
        var newCustSuggestionText by remember { mutableStateOf("") }
        val allSuggestions by viewModel.itemSuggestions.collectAsState()
        
        AlertDialog(
            onDismissRequest = { showManageSuggestionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Διαχείριση Επιλογών")
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Προσθήκη νέας γρήγορης επιλογής (π.χ. με emoji):", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newCustSuggestionText,
                            onValueChange = { newCustSuggestionText = it },
                            placeholder = { Text("π.χ. Κεράσια 🍒") },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                if (newCustSuggestionText.isNotBlank()) {
                                    viewModel.addSuggestion(newCustSuggestionText.trim())
                                    newCustSuggestionText = ""
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Προσθήκη", fontSize = 11.sp)
                        }
                    }
                    
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 4.dp))
                    
                    Text("Υπάρχουσες επιλογές (πατήστε ✕ για διαγραφή):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(allSuggestions) { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                                    IconButton(
                                        onClick = { viewModel.deleteSuggestion(suggestion) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Διαγραφή",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    TextButton(
                        onClick = { viewModel.resetSuggestions() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Επαναφορά Προεπιλεγμένων", fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showManageSuggestionsDialog = false }) {
                    Text("Κλείσιμο")
                }
            }
        )
    }

    // Dialog for renaming a custom list from settings
    if (listToRename != null) {
        var renameListName by remember(listToRename) { mutableStateOf(listToRename?.name ?: "") }
        AlertDialog(
            onDismissRequest = { listToRename = null },
            title = { Text("Μετονομασία Λίστας") },
            text = {
                OutlinedTextField(
                    value = renameListName,
                    onValueChange = { renameListName = it },
                    label = { Text("Όνομα Λίστας") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentList = listToRename
                        if (currentList != null && renameListName.isNotBlank()) {
                            viewModel.renameList(currentList.id, renameListName.trim())
                            listToRename = null
                        }
                    }
                ) {
                    Text("Αποθήκευση")
                }
            },
            dismissButton = {
                TextButton(onClick = { listToRename = null }) {
                    Text("Ακύρωση")
                }
            }
        )
    }

    // Dialog for deleting custom list from settings
    if (listToDelete != null) {
        AlertDialog(
            onDismissRequest = { listToDelete = null },
            title = { Text("Διαγραφή Λίστας") },
            text = {
                Text("Είστε σίγουροι ότι θέλετε να διαγράψετε τη λίστα \"${listToDelete?.name}\" και όλα τα προϊόντα που περιέχονται σε αυτήν;")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentList = listToDelete
                        if (currentList != null) {
                            viewModel.deleteListById(currentList.id)
                            listToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Διαγραφή")
                }
            },
            dismissButton = {
                TextButton(onClick = { listToDelete = null }) {
                    Text("Ακύρωση")
                }
            }
        )
    }

    // 4. Settings Modal Dialogue Pane
    if (showSettingsDialog) {
        Dialog(onDismissRequest = { showSettingsDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ρυθμίσεις",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Divider()

                    // Nickname label description info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Το Ψευδώνυμό σου", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(viewModel.userNickname, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.Face, "Profile user", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Couple Room Code representation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Συνδετικό Δωμάτιο", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(viewModel.coupleCode, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(viewModel.coupleCode))
                                Toast.makeText(context, "Κωδικός αντιγράφηκε! 📋", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy code", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Ανάγνωση με AI & Επικόλληση Κειμένου
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showSettingsDialog = false
                                showAiScanDialog = true
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Εισαγωγή με AI & Επικόλληση 🪄📋", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Ανίχνευση από φωτογραφία ή άμεση επικόλληση κειμένου", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = {
                                showSettingsDialog = false
                                showAiScanDialog = true
                            },
                            modifier = Modifier.testTag("ai_scan_settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = "AI Scanner",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Divider()

                    // 🛠️ BUTTON TO OPEN CATEGORIES DIALOG
                    OutlinedButton(
                        onClick = {
                            showSettingsDialog = false
                            showCategoriesDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.List, contentDescription = "Categories", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Επεξεργασία Κατηγοριών", fontWeight = FontWeight.SemiBold)
                    }

                    // ⚡ BUTTON TO OPEN SUGGESTIONS DIALOG
                    OutlinedButton(
                        onClick = {
                            showSettingsDialog = false
                            showSuggestionsDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = "Suggestions", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Επεξεργασία Γρήγορων Επιλογών", fontWeight = FontWeight.SemiBold)
                    }

                    Divider()

                    // Logout/Disconnect platform button
                    Button(
                        onClick = {
                            viewModel.logout()
                            showSettingsDialog = false
                            Toast.makeText(context, "Αποσυνδέθηκες επιτυχώς.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Αποσύνδεση & Καθαρισμός", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Έξοδος")
                    }
                }
            }
        }
    }

    // 4b. Categories Management Dialog
    if (showCategoriesDialog) {
        Dialog(onDismissRequest = { showCategoriesDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Κατηγορίες / Λίστες Αγορών",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    var newListNameInSettings by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newListNameInSettings,
                            onValueChange = { newListNameInSettings = it },
                            placeholder = { Text("Νέα λίστα... (π.χ. Μανάβικο 🥦)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                if (newListNameInSettings.isNotBlank()) {
                                    viewModel.addList(newListNameInSettings.trim())
                                    newListNameInSettings = ""
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f).testTag("settings_add_list_btn")
                        ) {
                            Text("Προσθήκη", fontSize = 11.sp)
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        lists.forEach { list ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = list.name,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { listToRename = list },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Μετονομασία",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { listToDelete = list },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Διαγραφή",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TextButton(onClick = { 
                        showCategoriesDialog = false 
                        showSettingsDialog = true // Go back to main settings
                    }) {
                        Text("Πίσω στις Ρυθμίσεις")
                    }
                }
            }
        }
    }

    // 4c. Quick Suggestions Management Dialog
    if (showSuggestionsDialog) {
        Dialog(onDismissRequest = { showSuggestionsDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Γρήγορες Επιλογές Προϊόντων",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    var newSuggestionStrSettings by remember { mutableStateOf("") }
                    val allSuggestionsSettings by viewModel.itemSuggestions.collectAsState()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newSuggestionStrSettings,
                            onValueChange = { newSuggestionStrSettings = it },
                            placeholder = { Text("π.χ. Κεράσια 🍒", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f),
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                if (newSuggestionStrSettings.isNotBlank()) {
                                    viewModel.addSuggestion(newSuggestionStrSettings.trim())
                                    newSuggestionStrSettings = ""
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f).testTag("settings_add_suggestion_btn")
                        ) {
                            Text("Προσθήκη", fontSize = 11.sp)
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        allSuggestionsSettings.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = suggestion,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                IconButton(
                                    onClick = { viewModel.deleteSuggestion(suggestion) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Διαγραφή",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    TextButton(
                        onClick = { viewModel.resetSuggestions() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Επαναφορά Προεπιλογών", fontSize = 11.sp)
                    }

                    TextButton(onClick = { 
                        showSuggestionsDialog = false 
                        showSettingsDialog = true // Go back to main settings
                    }) {
                        Text("Πίσω στις Ρυθμίσεις")
                    }
                }
            }
        }
    }

    // 5. AI Handwritten List Scanner Modal Dialog
    if (showAiScanDialog) {
        val scannedItems by viewModel.scannedItems.collectAsState()
        val isScanning by viewModel.isScanning.collectAsState()
        val scanError by viewModel.scanError.collectAsState()

        val scanTargetListId = remember {
            mutableStateOf(if (selectedListId != "all") selectedListId else lists.firstOrNull()?.id ?: "")
        }
        var showTargetListDropdown by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { 
            if (!isScanning) {
                showAiScanDialog = false
                viewModel.clearScannedItems()
                selectedImageUri = null
                selectedImageBitmap = null
            }
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(12.dp, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Εισαγωγή Προϊόντων 🪄📋",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                if (!isScanning) {
                                    showAiScanDialog = false
                                    viewModel.clearScannedItems()
                                    selectedImageUri = null
                                    selectedImageBitmap = null
                                }
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Κλείσιμο")
                        }
                    }

                    // Mode Tab Selector
                    var importModeTab by remember(importDialogTab) { mutableStateOf(importDialogTab) }
                    var pastedTextInput by remember { mutableStateOf("") }

                    TabRow(
                        selectedTabIndex = importModeTab,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = importModeTab == 0,
                            onClick = { if (!isScanning) importModeTab = 0 },
                            text = { Text("📸 Φωτογραφία (AI)", fontSize = 12.sp, fontWeight = if (importModeTab == 0) FontWeight.Bold else FontWeight.Normal) }
                        )
                        Tab(
                            selected = importModeTab == 1,
                            onClick = { if (!isScanning) importModeTab = 1 },
                            text = { Text("📋 Επικόλληση Κειμένου", fontSize = 12.sp, fontWeight = if (importModeTab == 1) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }

                    // Target List Selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Προσθήκη στη λίστα:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box {
                            val targetList = lists.find { it.id == scanTargetListId.value }
                            val targetListName = targetList?.name ?: "Επιλέξτε..."
                            TextButton(
                                onClick = { showTargetListDropdown = true },
                                modifier = Modifier.testTag("scan_target_list_selector"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(targetListName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(
                                expanded = showTargetListDropdown,
                                onDismissRequest = { showTargetListDropdown = false }
                            ) {
                                lists.forEach { list ->
                                    DropdownMenuItem(
                                        text = { Text(list.name) },
                                        onClick = {
                                            scanTargetListId.value = list.id
                                            showTargetListDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Tab 0: Image Picker / AI Scan
                    if (importModeTab == 0) {
                        Text(
                            text = "Φωτογραφίστε τη χειρόγραφη λίστα σας ή επιλέξτε εικόνα. Το AI θα αναγνωρίσει τα προϊόντα αυτόματα!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        // Image Picker / Preview Section
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clickable {
                                    if (!isScanning) {
                                        imagePickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            if (selectedImageUri != null) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = selectedImageUri,
                                        contentDescription = "Selected Handwritten List Image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.4f))
                                    )
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Αλλαγή Εικόνας",
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Αλλαγή Εικόνας",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Επιλογή Εικόνας",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Επιλογή Εικόνας Λίστας",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "Κάντε κλικ για λήψη ή επιλογή",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Analysis Action Button
                        Button(
                            onClick = {
                                val bmp = selectedImageBitmap
                                if (bmp != null) {
                                    viewModel.scanHandwrittenList(bmp)
                                }
                            },
                            enabled = selectedImageBitmap != null && !isScanning,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("start_ai_analysis_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ανάλυση σε εξέλιξη... 🪄")
                            } else {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = "Analyze")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ανάλυση με AI ✨", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Tab 1: Text Paste Import
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Επικολλήστε κείμενο λίστας:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            val clip = clipboardManager.getText()?.text
                                            if (!clip.isNullOrBlank()) {
                                                pastedTextInput = clip
                                            } else {
                                                Toast.makeText(context, "Το πρόχειρο είναι άδειο", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp).testTag("paste_clipboard_btn")
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Επικόλληση", fontSize = 11.sp)
                                    }
                                    if (pastedTextInput.isNotBlank()) {
                                        TextButton(
                                            onClick = { pastedTextInput = "" },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Καθαρισμός", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = pastedTextInput,
                                onValueChange = { pastedTextInput = it },
                                placeholder = {
                                    Text(
                                        "π.χ.\n2 γάλα\nψωμί τοστ\n1.5 κιλό πατάτες\nφέτα 500γρ\nαυγά 6αδα\nμήλα",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 110.dp, max = 180.dp)
                                    .testTag("pasted_text_field"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Action buttons for Text Import
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. Direct local parsing (instant, offline, 100% immune to 429)
                                Button(
                                    onClick = {
                                        viewModel.importFromPastedText(pastedTextInput, useAi = false)
                                    },
                                    enabled = pastedTextInput.isNotBlank() && !isScanning,
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("direct_text_import_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Άμεση Εισαγωγή ⚡", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                // 2. AI-assisted parsing
                                OutlinedButton(
                                    onClick = {
                                        viewModel.importFromPastedText(pastedTextInput, useAi = true)
                                    },
                                    enabled = pastedTextInput.isNotBlank() && !isScanning,
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("ai_text_import_btn"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isScanning) {
                                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                    } else {
                                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ανάλυση AI ✨", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Error Box if any
                    if (scanError != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                                    Text(
                                        text = scanError ?: "",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (importModeTab == 0 && (scanError?.contains("429") == true || scanError?.contains("Rate Limit") == true)) {
                                    TextButton(
                                        onClick = { importModeTab = 1 },
                                        modifier = Modifier.align(Alignment.End).height(30.dp)
                                    ) {
                                        Text("👉 Μετάβαση στην Επικόλληση Κειμένου", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Result List of Scanned Items
                    if (scannedItems.isNotEmpty()) {
                        Divider()
                        Text(
                            text = "Αναγνωρισμένα Προϊόντα (${scannedItems.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Text(
                            text = "Μπορείτε να διορθώσετε τυχόν λάθη ή να διαγράψετε προϊόντα πριν την προσθήκη.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            scannedItems.forEach { scannedItem ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Item Name Input
                                    OutlinedTextField(
                                        value = scannedItem.name,
                                        onValueChange = { viewModel.updateScannedItemName(scannedItem.id, it) },
                                        placeholder = { Text("Όνομα") },
                                        singleLine = true,
                                        modifier = Modifier.weight(2f),
                                        textStyle = MaterialTheme.typography.bodyMedium
                                    )
                                    // Item Quantity Input
                                    OutlinedTextField(
                                        value = scannedItem.qty,
                                        onValueChange = { viewModel.updateScannedItemQty(scannedItem.id, it) },
                                        placeholder = { Text("Ποσότητα") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        textStyle = MaterialTheme.typography.bodyMedium
                                    )
                                    // Delete Scanned Item Button
                                    IconButton(
                                        onClick = { viewModel.deleteScannedItem(scannedItem.id) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Διαγραφή",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // Manual item addition inside results list
                            var newScannedItemName by remember { mutableStateOf("") }
                            var newScannedItemQty by remember { mutableStateOf("") }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newScannedItemName,
                                    onValueChange = { newScannedItemName = it },
                                    placeholder = { Text("Προσθήκη νέου...", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(2f),
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )
                                OutlinedTextField(
                                    value = newScannedItemQty,
                                    onValueChange = { newScannedItemQty = it },
                                    placeholder = { Text("Ποσότητα", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(
                                    onClick = {
                                        if (newScannedItemName.isNotBlank()) {
                                            viewModel.addScannedItemManual(newScannedItemName.trim(), newScannedItemQty.trim())
                                            newScannedItemName = ""
                                            newScannedItemQty = ""
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Προσθήκη",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Final Confirmation CTA button to insert all scanned items
                        Button(
                            onClick = {
                                if (scanTargetListId.value.isNotBlank()) {
                                    viewModel.addAllScannedItemsToList(scanTargetListId.value)
                                    showAiScanDialog = false
                                    selectedImageUri = null
                                    selectedImageBitmap = null
                                    Toast.makeText(context, "Τα προϊόντα προστέθηκαν στη λίστα! 🎉", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Παρακαλώ επιλέξτε λίστα προορισμού.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_all_scanned_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Προσθήκη όλων στη λίστα ✅", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 📦 SHOPPING PRODUCT ITEM REPRESENTATION CARD COMPONENT
// Supports Check toggling, editing details, and deleting.
// ---------------------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingItemRow(
    item: ShoppingItem,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit = {},
    onSelectToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    val cardBgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        item.bought -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surface
    }

    val cardBorderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        item.bought -> Color.Transparent
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) {
                        onSelectToggle()
                    }
                },
                onLongClick = onLongClick
            )
            .testTag("item_row_${item.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBgColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isMultiSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() },
                        modifier = Modifier.testTag("item_select_checkbox_${item.id}")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                } else {
                    // Interactive Checkbox Toggle
                    Checkbox(
                        checked = item.bought,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.testTag("item_bought_checkbox_${item.id}")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                // Item description & quantity bubble details
                Column {
                    val priceText = if (item.price != null && item.price > 0.0) {
                        val factor = item.qty.trim().substringBefore(" ").toDoubleOrNull() ?: 1.0
                        val totalItemCost = item.price * factor
                        String.format(" (€%.2f)", totalItemCost)
                    } else {
                        ""
                    }
                    val cleanQty = item.qty.trim()
                    val qtyFormatted = if (cleanQty.isNotEmpty()) {
                        if (cleanQty.all { it.isDigit() || it == '.' || it == ',' }) {
                            "${cleanQty}Χ "
                        } else {
                            "$cleanQty "
                        }
                    } else {
                        ""
                    }
                    val displayText = "$qtyFormatted${item.name}$priceText"
                    
                    val priorityColor = when (item.priority) {
                        "HIGH" -> if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
                        "MEDIUM" -> if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
                        else -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                    }

                    Text(
                        text = displayText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.bought) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else priorityColor,
                        textDecoration = if (item.bought) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick actions buttons array (Edit pen, trash removal)
            if (!isMultiSelectMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Item Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).testTag("delete_item_btn")) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Item",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 💬 Ε. ΕΝΣΩΜΑΤΩΜΕΝΟ BABBLECHAT (The Conversation Messaging Pane)
// ---------------------------------------------------------------------------
@Composable
fun BabbleChatView(
    viewModel: BentoViewModel,
    onClose: () -> Unit
) {
    val messages by viewModel.chatMessages.collectAsState()
    val partnerTyping by viewModel.partnerTypingName.collectAsState()
    
    val filteredMessages = remember(messages) {
        messages.filter { msg ->
            !(msg.sender == "Σύστημα" && (msg.text.contains("καλάθι") || msg.text.contains("καλάθι 🛒") || msg.text.contains("καλαθιού") || msg.text.contains("μεταφέρθηκε")))
        }
    }
    
    var chatText by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
    var showClearChatConfirm by remember { mutableStateOf(false) }

    // Slide-down keyboard adjust list scroll focus to bottom on new messages
    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty()) {
            lazyListState.animateScrollToItem(filteredMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        // Chat Header Title Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ChatBubble, contentDescription = "Couple Chat", tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Couple Chat", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Κοινό δωμάτιο συνομιλιών", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { showClearChatConfirm = true },
                    modifier = Modifier.testTag("clear_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Καθαρισμός Συνομιλίας",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close Chat")
                }
            }
        }

        if (showClearChatConfirm) {
            AlertDialog(
                onDismissRequest = { showClearChatConfirm = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Καθαρισμός Συνομιλίας")
                    }
                },
                text = { Text("Είστε βέβαιοι ότι θέλετε να διαγράψετε όλα τα μηνύματα συνομιλίας;") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            viewModel.clearChat()
                            showClearChatConfirm = false
                        },
                        modifier = Modifier.testTag("clear_chat_confirm_btn")
                    ) {
                        Text("Καθαρισμός", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearChatConfirm = false }) { Text("Ακύρωση") }
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Divider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(modifier = Modifier.height(10.dp))

        // Chats messaging bubble scrolling tray
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredMessages) { msg ->
                val isMe = msg.sender == viewModel.userNickname
                val isSystem = msg.sender == "Σύστημα"

                if (isSystem) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = msg.text,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Column(
                            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
                            modifier = Modifier.fillMaxWidth(0.82f)
                        ) {
                            // Sender nickname tag description label
                            Text(
                                text = msg.sender,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )

                            // Main Text Bubble
                            Card(
                                shape = RoundedCornerShape(
                                    topStart = 16.dp,
                                    topEnd = 16.dp,
                                    bottomStart = if (isMe) 16.dp else 0.dp,
                                    bottomEnd = if (isMe) 0.dp else 16.dp
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Render sticker icon if provided
                                    if (msg.sticker != null) {
                                        Text(
                                            text = msg.sticker,
                                            fontSize = 44.sp,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }

                                    Text(
                                        text = msg.text,
                                        fontSize = 14.sp,
                                        color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Reaction emoji chips
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Existing reactions
                                msg.reactions.forEach { emoji ->
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                            .clickable { viewModel.reactToMessage(msg, emoji) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(emoji, fontSize = 10.sp)
                                    }
                                }

                                // Quick react triggering icon button opens miniature action reactions
                                if (msg.reactions.isEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.reactToMessage(msg, "❤️") },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Favorite, "React Heart", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.reactToMessage(msg, "👍") },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ThumbUp, "React ThumbsUp", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Rendering partner typing animation dot directly inside bubble list
            if (partnerTyping != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                                Text("Το $partnerTyping γράφει...", fontSize = 12.sp, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Aτάκες (1-Tap Greeks Quick Phrases Tray)
        Text(
            text = "Γρήγορες Ατάκες Ζευγαριού:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(viewModel.quickPhrases) { phrase ->
                InputChip(
                    selected = false,
                    onClick = { viewModel.sendChat(phrase) },
                    label = { Text(phrase, fontSize = 11.sp) }
                )
            }
        }

        // Cute Food stickers array selection tray
        Text(
            text = "Αυτοκόλλητα Φαγητών & Emojis:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(viewModel.foodStickers) { stickerEmoji ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                        .clickable { viewModel.sendChat("Έστειλε αυτοκόλλητο: $stickerEmoji", stickerEmoji) }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stickerEmoji, fontSize = 20.sp)
                }
            }
        }

        // Messaging Input Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = chatText,
                onValueChange = { chatText = it },
                placeholder = { Text("Γράψτε μήνυμα...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_text_input_field"),
                shape = RoundedCornerShape(24.dp),
                maxLines = 2,
                trailingIcon = {
                    IconButton(onClick = { chatText = "" }) {
                        Icon(Icons.Default.Clear, "Clear Draft")
                    }
                }
            )

            FloatingActionButton(
                onClick = {
                    if (chatText.isNotBlank()) {
                        viewModel.sendChat(chatText.trim())
                        chatText = ""
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .testTag("send_chat_text_btn"),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Send, "Send Chat text", modifier = Modifier.size(18.dp))
            }
        }
    }
}
