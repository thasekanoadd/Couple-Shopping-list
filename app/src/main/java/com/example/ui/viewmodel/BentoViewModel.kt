package com.example.ui.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.ShoppingItem
import com.example.data.model.ShoppingList
import com.example.data.repository.BentoRepository
import com.example.data.sync.ConnectionStatus
import com.example.data.sync.GeminiRetrofitClient
import com.example.data.sync.GenerateContentRequest
import com.example.data.sync.Content
import com.example.data.sync.Part
import com.example.data.sync.InlineData
import com.example.data.sync.GenerationConfig
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppScreen {
    CONNECT, DASHBOARD
}

class BentoViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = BentoRepository(application, database.dao())

    // UI state flows
    private val _currentScreen = MutableStateFlow(AppScreen.CONNECT)
    val currentScreen: StateFlow<AppScreen> = _currentScreen

    private val _selectedListId = MutableStateFlow("all")
    val selectedListId: StateFlow<String> = _selectedListId

    val allLists: StateFlow<List<ShoppingList>> = repository.allLists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allItems: StateFlow<List<ShoppingItem>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionStatus: StateFlow<ConnectionStatus> = repository.connectionStatus
    val partnerTypingName: StateFlow<String?> = repository.partnerTypingName
    val lastSyncTime: StateFlow<Long> = repository.lastSyncTime
    val isSyncing: StateFlow<Boolean> = repository.isSyncing

    // Dynamic message stream for the coupled room
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages

    private val _unreadMessagesCount = MutableStateFlow(0)
    val unreadMessagesCount: StateFlow<Int> = _unreadMessagesCount

    private var isChatCurrentlyOpen = false

    fun setChatOpen(isOpen: Boolean) {
        isChatCurrentlyOpen = isOpen
        if (isOpen) {
            _unreadMessagesCount.value = 0
        }
    }

    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _calculateBasketCost = MutableStateFlow(false)
    val calculateBasketCost: StateFlow<Boolean> = _calculateBasketCost

    private val _isKeepScreenOn = MutableStateFlow(false)
    val isKeepScreenOn: StateFlow<Boolean> = _isKeepScreenOn

    private val _itemSuggestions = MutableStateFlow<List<String>>(emptyList())
    val itemSuggestions: StateFlow<List<String>> = _itemSuggestions

    // Cute Greek combination helpers
    val greekAdjectives = listOf("Γλυκό", "Νυσταγμένο", "Χαρούμενο", "Ζωηρό", "Τρυφερό", "Φουντωτό", "Λιχούδικο")
    val greekNouns = listOf("Σκιουράκι", "Αρκουδάκι", "Μελάκι", "Παπάκι", "Γατάκι", "Κουνελάκι", "Πιγκουινάκι")

    // Greek quick phrases for chat
    val quickPhrases = listOf(
        "Πήρες το γάλα; 🥛",
        "Έρχομαι στο ταμείο! 🛒",
        "Ξέχασα κάτι! 😮",
        "Σου έστειλα τη λίστα 📝",
        "Σ' αγαπώ ❤️"
    )

    // Greek food stickers
    val foodStickers = listOf("🥚", "🍕", "🍦", "🍪", "🍩", "🥑", "🍔", "🍓", "🥕", "🍍")

    var userNickname = ""
    var coupleCode = ""
    var wsServerUrl = ""

    init {
        // Hydrate configuration from local preferences
        _isDarkMode.value = repository.isDarkMode()
        _isKeepScreenOn.value = repository.isKeepScreenOn()
        _calculateBasketCost.value = repository.isCalculateBasketCost()
        _itemSuggestions.value = repository.getCustomSuggestions()
        if (repository.hasUserProfile()) {
            userNickname = repository.getNickname()
            coupleCode = repository.getCoupleCode()
            wsServerUrl = repository.getCustomServerUrl()
            
            // Connect WebSocket
            repository.wsManager.connect(userNickname, coupleCode, wsServerUrl)
            _currentScreen.value = AppScreen.DASHBOARD
            observeMessages(coupleCode)
        }
        startPeriodicSync()
    }

    private fun startPeriodicSync() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(6_000) // Frequent automatic cloud sync every 6 seconds
                if (repository.hasUserProfile()) {
                    repository.forceSync()
                }
            }
        }
    }

    fun onAppResume() {
        viewModelScope.launch {
            if (repository.hasUserProfile()) {
                repository.forceSync()
            }
        }
    }

    fun onAppPauseOrStop() {
        viewModelScope.launch {
            if (repository.hasUserProfile()) {
                repository.forceSync()
            }
        }
    }

    fun generateRandomNickname(): String {
        val adj = greekAdjectives.random()
        val noun = greekNouns.random()
        return "$adj $noun"
    }

    fun generateRandom6DigitCode(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun connectUser(name: String, code: String, customUrl: String) {
        val trimmedName = name.trim().ifBlank { generateRandomNickname() }
        val trimmedCode = code.trim().ifBlank { generateRandom6DigitCode() }
        
        userNickname = trimmedName
        coupleCode = trimmedCode
        wsServerUrl = customUrl

        repository.saveProfile(trimmedName, trimmedCode, customUrl)
        
        // Setup initial default shopping categories/lists if empty
        viewModelScope.launch {
            repository.allLists.first().let { currentLists ->
                if (currentLists.isEmpty()) {
                    repository.addShoppingList("Σούπερ Μάρκετ 🛒")
                    repository.addShoppingList("Λαϊκή Αγορά 🥦")
                    repository.addShoppingList("Pet Shop 🐾")
                }
            }
        }

        _currentScreen.value = AppScreen.DASHBOARD
        observeMessages(trimmedCode)
    }

    private fun observeMessages(code: String) {
        viewModelScope.launch {
            repository.getMessages(code).collect { messages ->
                val prevMessages = _chatMessages.value
                val prevCount = prevMessages.size
                _chatMessages.value = messages

                // If messages increased and we already had a history, flag unread messages and show Toast
                if (prevCount > 0 && messages.size > prevCount) {
                    val incomingCount = messages.size - prevCount
                    val partnerMessages = messages.takeLast(incomingCount).filter { 
                        it.sender != userNickname && it.sender != "Σύστημα"
                    }
                    if (partnerMessages.isNotEmpty()) {
                        if (!isChatCurrentlyOpen) {
                            _unreadMessagesCount.value += partnerMessages.size
                        }
                        
                        // Show fresh Toast notification for the partner message(s)
                        val lastMsg = partnerMessages.last()
                        val previewText = if (lastMsg.sticker != null) {
                            "${lastMsg.sticker} (Αυτοκόλλητο)"
                        } else {
                            lastMsg.text
                        }
                        try {
                            Toast.makeText(
                                getApplication(),
                                "💬 ${lastMsg.sender}: $previewText",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            // ignore in tests
                        }
                    }
                }
            }
        }
    }

    fun logout() {
        repository.clearProfile()
        userNickname = ""
        coupleCode = ""
        wsServerUrl = ""
        _selectedListId.value = "all"
        _currentScreen.value = AppScreen.CONNECT
    }

    fun selectList(id: String) {
        _selectedListId.value = id
    }

    fun addList(name: String) {
        viewModelScope.launch {
            repository.addShoppingList(name)
        }
    }

    fun renameList(id: String, newName: String) {
        viewModelScope.launch {
            repository.renameShoppingList(id, newName)
        }
    }

    fun deleteCurrentList() {
        val currentId = _selectedListId.value
        if (currentId != "all") {
            viewModelScope.launch {
                repository.deleteShoppingList(currentId)
                _selectedListId.value = "all"
            }
        }
    }

    fun deleteListById(id: String) {
        viewModelScope.launch {
            repository.deleteShoppingList(id)
            if (_selectedListId.value == id) {
                _selectedListId.value = "all"
            }
        }
    }

    fun addOrUpdateItem(id: String?, name: String, qty: String, price: Double?, listIds: List<String>, bought: Boolean = false, priority: String = "LOW") {
        viewModelScope.launch {
            repository.addOrUpdateItem(id, name, qty, price, listIds, bought, priority)
        }
    }

    fun toggleItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.toggleItemBought(item)
        }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    fun bulkUpdatePriority(itemIds: Set<String>, newPriority: String) {
        viewModelScope.launch {
            allItems.value.filter { it.id in itemIds }.forEach { item ->
                repository.addOrUpdateItem(item.id, item.name, item.qty, item.price, item.listIds, item.bought, newPriority)
            }
        }
    }

    fun bulkUpdateLists(itemIds: Set<String>, targetListId: String) {
        viewModelScope.launch {
            allItems.value.filter { it.id in itemIds }.forEach { item ->
                val newListIds = if (targetListId == "all") item.listIds else listOf(targetListId)
                repository.addOrUpdateItem(item.id, item.name, item.qty, item.price, newListIds, item.bought, item.priority)
            }
        }
    }

    fun bulkToggleBought(itemIds: Set<String>, bought: Boolean) {
        viewModelScope.launch {
            allItems.value.filter { it.id in itemIds }.forEach { item ->
                repository.addOrUpdateItem(item.id, item.name, item.qty, item.price, item.listIds, bought, item.priority)
            }
        }
    }

    fun bulkDelete(itemIds: Set<String>) {
        viewModelScope.launch {
            allItems.value.filter { it.id in itemIds }.forEach { item ->
                repository.deleteItem(item)
            }
        }
    }

    fun clearBoughtItems() {
        viewModelScope.launch {
            val listId = _selectedListId.value
            val itemsToDelete = allItems.value.filter { item ->
                item.bought && (listId == "all" || item.listIds.contains(listId))
            }
            itemsToDelete.forEach { item ->
                repository.deleteItem(item)
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            repository.forceSync()
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChat()
        }
    }

    fun sendChat(text: String, sticker: String? = null) {
        viewModelScope.launch {
            repository.sendChatMessage(text, sticker)
        }
    }

    fun reactToMessage(message: ChatMessage, emoji: String) {
        viewModelScope.launch {
            repository.addReactionToMessage(message, emoji)
        }
    }

    fun toggleDarkMode() {
        val next = !_isDarkMode.value
        _isDarkMode.value = next
        repository.setDarkMode(next)
    }

    fun toggleCalculateBasketCost() {
        val next = !_calculateBasketCost.value
        _calculateBasketCost.value = next
        repository.setCalculateBasketCost(next)
    }

    fun toggleKeepScreenOn() {
        val next = !_isKeepScreenOn.value
        _isKeepScreenOn.value = next
        repository.setKeepScreenOn(next)
    }

    // -------------------------------------------------------------
    // Bento Billing Realtime Calculator Math Logic
    // Parses string/text quantities to evaluate approximate totals.
    // -------------------------------------------------------------

    fun getCheckedTotal(filterListId: String, items: List<ShoppingItem>): Double {
        return items.filter { 
            (filterListId == "all" || it.listIds.contains(filterListId)) && it.bought 
        }.sumOf { (it.price ?: 0.0) * parseQuantity(it.qty) }
    }

    fun getPendingTotal(filterListId: String, items: List<ShoppingItem>): Double {
        return items.filter { 
            (filterListId == "all" || it.listIds.contains(filterListId)) && !it.bought
        }.sumOf { (it.price ?: 0.0) * parseQuantity(it.qty) }
    }

    fun getOverallTotal(filterListId: String, items: List<ShoppingItem>): Double {
        return items.filter { 
            filterListId == "all" || it.listIds.contains(filterListId)
        }.sumOf { (it.price ?: 0.0) * parseQuantity(it.qty) }
    }

    fun getCompletionPercentage(filterListId: String, items: List<ShoppingItem>): Float {
        val filtered = items.filter { filterListId == "all" || it.listIds.contains(filterListId) }
        if (filtered.isEmpty()) return 0f
        val boughtCount = filtered.count { it.bought }
        return boughtCount.toFloat() / filtered.size.toFloat()
    }

    /**
     * Parses the leading numeric component of a quantity string (e.g., "1.5 kg" -> 1.5).
         * Fallbacks to 1.0 if not parsed or purely alphabetical.
     */
    private fun parseQuantity(qtyText: String): Double {
        val trimmed = qtyText.trim()
        if (trimmed.isEmpty()) return 1.0
        val regex = """^([0-9]+(?:\.[0-9]+)?)\b""".toRegex()
        val match = regex.find(trimmed)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
    }

    fun addSuggestion(suggestion: String) {
        val trimmed = suggestion.trim()
        if (trimmed.isNotBlank()) {
            val current = _itemSuggestions.value.toMutableList()
            if (!current.contains(trimmed)) {
                current.add(trimmed)
                _itemSuggestions.value = current.sorted()
                repository.saveCustomSuggestions(current)
            }
        }
    }

    fun deleteSuggestion(suggestion: String) {
        val current = _itemSuggestions.value.toMutableList()
        if (current.remove(suggestion)) {
            _itemSuggestions.value = current.sorted()
            repository.saveCustomSuggestions(current)
        }
    }

    fun resetSuggestions() {
        val defaults = listOf(
            "Γάλα Φρέσκο 🥛",
            "Ψωμί Φραντζόλα 🍞",
            "Αυγά Εξάδα 🥚",
            "Ντομάτες 🍅",
            "Καφές ☕",
            "Μπανάνες 🍌",
            "Γιαούρτι 🥛",
            "Κοτόπουλο 🍗",
            "Τυρί Φέτα 🧀",
            "Μακαρόνια 🍝"
        )
        _itemSuggestions.value = defaults.sorted()
        repository.saveCustomSuggestions(defaults)
    }

    // AI scanner states
    private val _scannedItems = MutableStateFlow<List<ParsedShoppingItem>>(emptyList())
    val scannedItems: StateFlow<List<ParsedShoppingItem>> = _scannedItems

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError

    fun clearScannedItems() {
        _scannedItems.value = emptyList()
        _scanError.value = null
    }

    fun updateScannedItemName(id: String, newName: String) {
        _scannedItems.value = _scannedItems.value.map {
            if (it.id == id) it.copy(name = newName) else it
        }
    }

    fun updateScannedItemQty(id: String, newQty: String) {
        _scannedItems.value = _scannedItems.value.map {
            if (it.id == id) it.copy(qty = newQty) else it
        }
    }

    fun deleteScannedItem(id: String) {
        _scannedItems.value = _scannedItems.value.filter { it.id != id }
    }

    fun addScannedItemManual(name: String, qty: String) {
        _scannedItems.value = _scannedItems.value + ParsedShoppingItem(name = name, qty = qty)
    }

    fun scanHandwrittenList(bitmap: Bitmap) {
        viewModelScope.launch {
            _isScanning.value = true
            _scanError.value = null
            _scannedItems.value = emptyList()

            try {
                // Resize bitmap to max 1024x1024 to save memory, tokens and prevent rate limit (429) errors
                val scaledBitmap = if (bitmap.width > 1024 || bitmap.height > 1024) {
                    val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                    val (w, h) = if (ratio > 1f) {
                        1024 to (1024 / ratio).toInt()
                    } else {
                        (1024 * ratio).toInt() to 1024
                    }
                    Bitmap.createScaledBitmap(bitmap, w, h, true)
                } else {
                    bitmap
                }

                val outputStream = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                val prompt = """
                    You are an expert shopping list scanner. 
                    Your task is to scan the image of the handwritten shopping list and extract all the items listed in it.
                    For each item, extract the name and the quantity or weight (if specified).
                    Make sure to translate the names into Greek if they are in another language, or keep them in Greek if they are already in Greek.
                    Respond ONLY with a JSON array where each object has exactly two fields:
                    - "name" (string, the name of the item)
                    - "qty" (string, the quantity or weight of the item, e.g. "1", "2 κιλά", or an empty string if not specified)

                    Do NOT wrap the JSON in markdown formatting. Respond with the raw JSON array string only.
                """.trimIndent()

                val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                    _scanError.value = "Το API Key του Gemini δεν έχει ρυθμιστεί. Παρακαλώ ρυθμίστε το στα Secrets."
                    _isScanning.value = false
                    return@launch
                }

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = prompt),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Data))
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.2f
                    )
                )

                // Try modern models with fallback
                val candidateModels = listOf("gemini-3.5-flash", "gemini-3.1-flash-lite-preview", "gemini-flash-latest")
                var parsedList: List<ParsedShoppingItem>? = null
                var lastError: Exception? = null

                for (modelName in candidateModels) {
                    try {
                        val response = GeminiRetrofitClient.service.generateContent(
                            model = modelName,
                            apiKey = apiKey,
                            request = request
                        )
                        val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        if (!textResponse.isNullOrBlank()) {
                            val items = mutableListOf<ParsedShoppingItem>()
                            val jsonArray = org.json.JSONArray(textResponse)
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val name = obj.optString("name", "").trim()
                                val qty = obj.optString("qty", "").trim()
                                if (name.isNotBlank()) {
                                    items.add(ParsedShoppingItem(name = name, qty = if (qty.isEmpty()) "1" else qty))
                                }
                            }
                            if (items.isNotEmpty()) {
                                parsedList = items
                                break
                            }
                        }
                    } catch (e: Exception) {
                        lastError = e
                        Log.w("BentoAI", "Model $modelName failed: ${e.message}")
                    }
                }

                if (parsedList != null && parsedList.isNotEmpty()) {
                    _scannedItems.value = parsedList
                    _scanError.value = null
                } else {
                    val errMsg = lastError?.message ?: ""
                    if (errMsg.contains("429") || (lastError is retrofit2.HttpException && lastError.code() == 429)) {
                        _scanError.value = "Σφάλμα AI (HTTP 429 - Rate Limit): Εξαντλήθηκε προσωρινά το όριο του AI. Χρησιμοποιήστε την καρτέλα «Επικόλληση Κειμένου» για άμεση εισαγωγή!"
                    } else if (lastError != null) {
                        _scanError.value = "Σφάλμα AI: ${lastError.localizedMessage ?: lastError.message}. Μπορείτε να εισάγετε τα προϊόντα και με «Επικόλληση Κειμένου»."
                    } else {
                        _scanError.value = "Δεν βρέθηκαν προϊόντα στην εικόνα. Δοκιμάστε ξανά ή χρησιμοποιήστε την «Επικόλληση Κειμένου»."
                    }
                }
            } catch (e: Exception) {
                Log.e("BentoAI", "Scan api error", e)
                if (e.message?.contains("429") == true || (e is retrofit2.HttpException && e.code() == 429)) {
                    _scanError.value = "Σφάλμα AI (HTTP 429 - Rate Limit): Εξαντλήθηκε προσωρινά το όριο του AI. Χρησιμοποιήστε την καρτέλα «Επικόλληση Κειμένου» για άμεση εισαγωγή!"
                } else {
                    _scanError.value = "Σφάλμα επικοινωνίας με το AI: ${e.localizedMessage ?: e.message}"
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    /**
     * Converts common Greek number words into numeric strings or standardizes them.
     */
    private val greekNumberWords = mapOf(
        "ένα" to "1", "ένας" to "1", "μία" to "1", "μια" to "1",
        "δύο" to "2", "δυο" to "2",
        "τρία" to "3", "τρεις" to "3",
        "τέσσερα" to "4", "τέσσερις" to "4",
        "πέντε" to "5",
        "έξι" to "6",
        "επτά" to "7", "εφτά" to "7",
        "οκτώ" to "8", "οχτώ" to "8",
        "εννέα" to "9", "εννιά" to "9",
        "δέκα" to "10",
        "έντεκα" to "11",
        "δώδεκα" to "12",
        "δεκαπέντε" to "15",
        "είκοσι" to "20",
        "μισό" to "0.5", "μισή" to "0.5"
    )

    /**
     * Splits continuous spoken Greek text (from Android SpeechRecognizer which does NOT insert punctuation)
     * into separate shopping items using:
     * - Spoken punctuation words ("κόμμα", "τελεία", "παύλα", "enter", "newline")
     * - Spoken conjunctions ("και", "κι", "επίσης", "ακόμα", "ακόμη", "μετά")
     * - Transition boundaries before quantities/numbers (e.g. "ψωμί 2 γάλατα" -> "ψωμί", "2 γάλατα")
     * - Known units and packaging (κιλά, κιλό, kg, γρ, γραμμάρια, λίτρα, λίτρο, κουτιά, πακέτα, κτλ.)
     */
    fun splitSpokenGreekList(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()

        var text = rawText.trim()

        // 1. Convert spoken punctuation words to real delimiters
        // People often say "κόμμα" or "τελεία" or the engine might output commas
        text = text.replace(Regex("\\s*\\b(?:κόμμα|κομμα|τελεία|τελεια|παύλα|παυλα|άνω τελεία)\\b\\s*", RegexOption.IGNORE_CASE), " , ")

        // 2. Convert spoken conjunctions to commas
        text = text.replace(Regex("\\s*\\b(?:και|κι|επίσης|επισης|ακόμα|ακομα|ακόμη|ακομη|μετά|μετα)\\b\\s*", RegexOption.IGNORE_CASE), " , ")

        // 3. Pre-process numbers written as Greek words before nouns or units
        // e.g. "δύο γάλατα" -> "2 γάλατα", "τρία κιλά μήλα" -> "3 κιλά μήλα"
        for ((word, digit) in greekNumberWords) {
            text = text.replace(Regex("\\b$word\\b", RegexOption.IGNORE_CASE), digit)
        }

        // 4. Handle speech engine missing punctuation between items where a new number starts:
        // e.g. "γάλα 2 ψωμιά 1 κιλό μήλα 500 γρ κιμά φέτα"
        // Insert a delimiter before numbers that follow a non-number word (e.g. "γάλα | 2 ψωμιά | 1 κιλό...")
        // But NOT inside quantities like "1.5" or "1 , 5"
        val numberFollowsWordRegex = Regex("(?<=[^\\d\\s,;\\n])\\s+(?=\\d+(?:[.,]\\d+)?\\s*(?:κιλά|κιλό|kg|g|γρ|γραμμάρια|τεμ|τεμάχια|κουτί|κουτιά|πακέτο|πακέτα|λίτρα|λίτρο|l|ml)?\\s+[a-zA-Z\\u0370-\\u03FF])", RegexOption.IGNORE_CASE)
        text = text.replace(numberFollowsWordRegex, " , ")

        // 5. Split by all delimiters (commas, semicolons, newlines, etc.)
        val parts = text.split(Regex("[,;\\n]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return parts
    }

    /**
     * Parses shopping list text locally without needing any API or internet connection.
     * Extracts item names and quantities accurately (e.g. "2 γάλα", "ψωμί 1", "1.5 κιλό πατάτες", "φέτα 500γρ").
     */
    fun parseShoppingListTextLocally(rawText: String): List<ParsedShoppingItem> {
        if (rawText.isBlank()) return emptyList()

        val rawLines = splitSpokenGreekList(rawText)

        val results = mutableListOf<ParsedShoppingItem>()
        for (entry in rawLines) {
            var clean = entry.trim()
            if (clean.isBlank()) continue

            // Remove leading bullets, numbers, checkmarks, dashes, brackets: e.g. "- γάλα", "* 2 ψωμιά", "1. αυγά", "[ ] μπανάνες"
            clean = clean.replace(Regex("^([\\s\\-•*–—\\[\\]\\(xX\\)]|\\d+[.)])+\\s*"), "").trim()
            if (clean.isBlank()) continue

            // 1. Leading quantity pattern: e.g. "2 γάλα", "2x γάλα", "1.5 κιλό πατάτες", "500 γρ κιμάς", "6 αυγά"
            val leadingRegex = Regex("^(\\d+(?:[.,]\\d+)?\\s*(?:κιλά|κιλό|kg|g|γρ|γραμμάρια|τεμ|τεμάχια|τεμ\\.|κουτί|κουτιά|πακέτο|πακέτα|λίτρα|λίτρο|l|ml)?(?:\\s*[xX*])?)\\s+(.+)$", RegexOption.IGNORE_CASE)
            val leadMatch = leadingRegex.find(clean)
            if (leadMatch != null) {
                val qty = leadMatch.groupValues[1].trim()
                val name = leadMatch.groupValues[2].trim()
                if (name.isNotBlank()) {
                    results.add(ParsedShoppingItem(name = name, qty = qty))
                    continue
                }
            }

            // 2. Trailing quantity pattern: e.g. "γάλα 2", "πατάτες 2 κιλά", "φέτα 500γρ", "μπύρες 6αδα"
            val trailRegex = Regex("^(.+?)\\s+([xX*]?\\s*\\d+(?:[.,]\\d+)?\\s*(?:κιλά|κιλό|kg|g|γρ|γραμμάρια|τεμ|τεμάχια|τεμ\\.|κουτί|κουτιά|πακέτο|πακέτα|λίτρα|λίτρο|l|ml|αδα)?)$", RegexOption.IGNORE_CASE)
            val trailMatch = trailRegex.find(clean)
            if (trailMatch != null) {
                val name = trailMatch.groupValues[1].trim()
                val qty = trailMatch.groupValues[2].trim()
                if (name.isNotBlank()) {
                    results.add(ParsedShoppingItem(name = name, qty = qty))
                    continue
                }
            }

            // 3. Fallback: single item without specific quantity
            results.add(ParsedShoppingItem(name = clean, qty = "1"))
        }
        return results
    }

    /**
     * Imports shopping items from pasted raw text.
     * If useAi is false, performs instant, offline local parsing (zero 429 errors).
     * If useAi is true, attempts Gemini parsing and falls back automatically to local parsing on any error/429.
     */
    fun importFromPastedText(text: String, useAi: Boolean = false) {
        if (text.isBlank()) return

        val localItems = parseShoppingListTextLocally(text)
        if (localItems.isEmpty()) {
            _scanError.value = "Δεν εντοπίστηκαν προϊόντα στο κείμενο."
            return
        }

        if (!useAi) {
            _scannedItems.value = localItems
            _scanError.value = null
            return
        }

        // Use AI with automatic local fallback
        viewModelScope.launch {
            _isScanning.value = true
            _scanError.value = null

            val apiKey = com.example.BuildConfig.GEMINI_API_KEY
            if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
                _scannedItems.value = localItems
                _isScanning.value = false
                return@launch
            }

            try {
                val prompt = """
                    You are an expert shopping list organizer.
                    Extract each shopping item from this user text:
                    $text

                    For each item extract:
                    - "name": clean product name in Greek (capitalize nicely)
                    - "qty": quantity or weight (e.g. "1", "2 κιλά", "500γρ")

                    Respond ONLY with a JSON array where each item has "name" and "qty". No markdown formatting.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt)))),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.2f
                    )
                )

                val candidateModels = listOf("gemini-3.5-flash", "gemini-3.1-flash-lite-preview", "gemini-flash-latest")
                var parsedList: List<ParsedShoppingItem>? = null
                var lastEx: Exception? = null

                for (modelName in candidateModels) {
                    try {
                        val response = GeminiRetrofitClient.service.generateContent(
                            model = modelName,
                            apiKey = apiKey,
                            request = request
                        )
                        val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        if (!textResponse.isNullOrBlank()) {
                            val items = mutableListOf<ParsedShoppingItem>()
                            val jsonArray = org.json.JSONArray(textResponse)
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val name = obj.optString("name", "").trim()
                                val qty = obj.optString("qty", "1").trim()
                                if (name.isNotBlank()) {
                                    items.add(ParsedShoppingItem(name = name, qty = if (qty.isEmpty()) "1" else qty))
                                }
                            }
                            if (items.isNotEmpty()) {
                                parsedList = items
                                break
                            }
                        }
                    } catch (e: Exception) {
                        lastEx = e
                        Log.w("BentoAI", "Model $modelName text import failed: ${e.message}")
                    }
                }

                if (parsedList != null && parsedList.isNotEmpty()) {
                    _scannedItems.value = parsedList
                    _scanError.value = null
                } else {
                    // Fallback to local parsing gracefully
                    _scannedItems.value = localItems
                    if (lastEx?.message?.contains("429") == true || (lastEx is retrofit2.HttpException && lastEx.code() == 429)) {
                        _scanError.value = "Σημείωση AI (429 Rate Limit): Έγινε αυτόματη τοπική ανάλυση κειμένου ✓"
                    }
                }
            } catch (e: Exception) {
                Log.e("BentoAI", "AI text parsing error", e)
                _scannedItems.value = localItems
                if (e.message?.contains("429") == true || (e is retrofit2.HttpException && e.code() == 429)) {
                    _scanError.value = "Σημείωση AI (429 Rate Limit): Έγινε αυτόματη τοπική ανάλυση κειμένου ✓"
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun addAllScannedItemsToList(listId: String) {
        viewModelScope.launch {
            val items = _scannedItems.value
            if (items.isEmpty()) return@launch

            items.forEach { scanned ->
                repository.addOrUpdateItem(
                    id = null,
                    name = scanned.name,
                    qty = scanned.qty,
                    price = null,
                    listIds = listOf(listId),
                    bought = false,
                    priority = "LOW"
                )
            }
            clearScannedItems()
        }
    }

    /**
     * Adds items spoken by the user via microphone speech recognition.
     * Uses local parser - NO AI, 100% offline and instant.
     * e.g., "ψωμί ολικής" -> name="ψωμί ολικής", qty="1"
     * e.g., "2 γάλατα και 1 κιλό μήλα" -> 2 items parsed and added.
     */
    fun addVoiceSpokenItems(spokenText: String, targetListId: String? = null): List<String> {
        val targetId = if (targetListId != null && targetListId != "all") {
            targetListId
        } else if (_selectedListId.value != "all") {
            _selectedListId.value
        } else {
            allLists.value.firstOrNull()?.id ?: "default"
        }

        // Clean and parse text locally
        var text = spokenText.trim()
        // Support speaking multiple items connected by "και" or commas
        text = text.replace(Regex("\\s+και\\s+", RegexOption.IGNORE_CASE), "\n")
        val parsed = parseShoppingListTextLocally(text)
        val addedNames = mutableListOf<String>()

        if (parsed.isNotEmpty()) {
            viewModelScope.launch {
                parsed.forEach { item ->
                    // Capitalize first letter of item name nicely
                    val formattedName = item.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    repository.addOrUpdateItem(
                        id = null,
                        name = formattedName,
                        qty = item.qty,
                        price = null,
                        listIds = listOf(targetId),
                        bought = false,
                        priority = "LOW"
                    )
                    addedNames.add("$formattedName (${item.qty})")
                }
            }
        } else if (text.isNotBlank()) {
            val formattedName = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            viewModelScope.launch {
                repository.addOrUpdateItem(
                    id = null,
                    name = formattedName,
                    qty = "1",
                    price = null,
                    listIds = listOf(targetId),
                    bought = false,
                    priority = "LOW"
                )
            }
            addedNames.add(formattedName)
        }
        return addedNames
    }
}

data class ParsedShoppingItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val qty: String
)

class BentoViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BentoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BentoViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
