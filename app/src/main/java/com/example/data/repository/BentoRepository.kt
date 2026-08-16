package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.db.AppDao
import com.example.data.model.ChatMessage
import com.example.data.model.ShoppingItem
import com.example.data.model.ShoppingList
import com.example.data.sync.BentoWebSocketManager
import com.example.data.sync.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class BentoRepository(
    private val context: Context,
    private val dao: AppDao,
    val wsManager: BentoWebSocketManager = BentoWebSocketManager()
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("bento_prefs", Context.MODE_PRIVATE)
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    // Sync Flows & Status
    val allLists: Flow<List<ShoppingList>> = dao.getAllLists()
    val allItems: Flow<List<ShoppingItem>> = dao.getAllItems()
    
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _partnerTypingName = MutableStateFlow<String?>(null)
    val partnerTypingName: StateFlow<String?> = _partnerTypingName

    private val _lastSyncTime = MutableStateFlow<Long>(System.currentTimeMillis())
    val lastSyncTime: StateFlow<Long> = _lastSyncTime

    private val _isSyncing = MutableStateFlow<Boolean>(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    init {
        // Wire up the WebSocket Manager listeners
        wsManager.setListeners(
            onStatusChange = { status ->
                _connectionStatus.value = status
            },
            onSync = { incomingLists, incomingItems, sender ->
                repositoryScope.launch {
                    _isSyncing.value = true
                    try {
                        var needsReturnSync = false

                        // 1. Process incoming lists with timestamp LWW
                        if (incomingLists.isNotEmpty()) {
                            val localListsMap = dao.getAllListsDirect().associateBy { it.id }
                            val listsToInsert = mutableListOf<ShoppingList>()

                            for (incomingList in incomingLists) {
                                val localList = localListsMap[incomingList.id]
                                if (localList == null) {
                                    listsToInsert.add(incomingList)
                                } else {
                                    val incomingTime = incomingList.updatedAt.toLongOrNull() ?: 0L
                                    val localTime = localList.updatedAt.toLongOrNull() ?: 0L
                                    if (incomingTime >= localTime) {
                                        listsToInsert.add(incomingList)
                                    } else {
                                        needsReturnSync = true
                                    }
                                }
                            }

                            if (listsToInsert.isNotEmpty()) {
                                dao.insertLists(listsToInsert)
                            }
                        }

                        // 2. Process incoming items with timestamp LWW
                        if (incomingItems.isNotEmpty()) {
                            val localItemsMap = dao.getAllItemsDirect().associateBy { it.id }
                            val itemsToInsert = mutableListOf<ShoppingItem>()

                            for (incomingItem in incomingItems) {
                                val localItem = localItemsMap[incomingItem.id]
                                if (localItem == null) {
                                    itemsToInsert.add(incomingItem)
                                } else {
                                    val incomingTime = incomingItem.updatedAt.toLongOrNull() ?: 0L
                                    val localTime = localItem.updatedAt.toLongOrNull() ?: 0L
                                    if (incomingTime >= localTime) {
                                        itemsToInsert.add(incomingItem)
                                    } else {
                                        needsReturnSync = true
                                    }
                                }
                            }

                            // Check if local has active items that remote didn't know about
                            val incomingIds = incomingItems.map { it.id }.toSet()
                            for ((localId, localItem) in localItemsMap) {
                                if (!incomingIds.contains(localId) && !localItem.isDeleted) {
                                    needsReturnSync = true
                                }
                            }

                            if (itemsToInsert.isNotEmpty()) {
                                dao.insertItems(itemsToInsert)
                            }
                        }

                        _lastSyncTime.value = System.currentTimeMillis()

                        if (needsReturnSync) {
                            triggerWSSync()
                        }
                    } finally {
                        _isSyncing.value = false
                    }
                }
            },
            onSyncRequested = {
                repositoryScope.launch {
                    triggerWSSync()
                }
            },
            onChat = { message ->
                repositoryScope.launch {
                    dao.insertMessage(message)
                }
            },
            onTyping = { partner, isTyping ->
                _partnerTypingName.value = if (isTyping) partner else null
            }
        )
    }

    // Settings Profile retention
    fun getNickname(): String = prefs.getString("nickname", "") ?: ""
    fun getCoupleCode(): String = prefs.getString("couple_code", "") ?: ""
    fun getCustomServerUrl(): String = prefs.getString("custom_server_url", "") ?: ""
    fun isDarkMode(): Boolean = prefs.getBoolean("dark_mode", false)

    fun isKeepScreenOn(): Boolean = prefs.getBoolean("keep_screen_on", false)

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean("keep_screen_on", enabled).apply()
    }

    fun hasUserProfile(): Boolean {
        return getNickname().isNotBlank() && getCoupleCode().isNotBlank()
    }

    fun saveProfile(nickname: String, coupleCode: String, customUrl: String) {
        prefs.edit()
            .putString("nickname", nickname)
            .putString("couple_code", coupleCode)
            .putString("custom_server_url", customUrl)
            .apply()

        // reconnect socket
        wsManager.connect(nickname, coupleCode, customUrl)
    }

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun isCalculateBasketCost(): Boolean = prefs.getBoolean("calculate_basket_cost", false)

    fun setCalculateBasketCost(enabled: Boolean) {
        prefs.edit().putBoolean("calculate_basket_cost", enabled).apply()
    }

    fun getCustomSuggestions(): List<String> {
        val s = prefs.getStringSet("custom_item_suggestions", null)
        if (s == null) {
            val defaults = setOf(
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
            prefs.edit().putStringSet("custom_item_suggestions", defaults).apply()
            return defaults.sorted()
        }
        return s.toList().sorted()
    }

    fun saveCustomSuggestions(suggestions: List<String>) {
        prefs.edit().putStringSet("custom_item_suggestions", suggestions.toSet()).apply()
    }

    fun clearProfile() {
        prefs.edit().clear().apply()
        wsManager.disconnect()
        repositoryScope.launch {
            dao.clearAllLists()
            dao.clearAllItems()
        }
    }

    fun getMessages(coupleCode: String): Flow<List<ChatMessage>> = dao.getMessages(coupleCode)

    // Shopping List management
    suspend fun addShoppingList(name: String) {
        val list = ShoppingList(
            id = "list-${UUID.randomUUID()}",
            name = name,
            updatedAt = System.currentTimeMillis().toString(),
            isDeleted = false
        )
        dao.insertList(list)
        triggerWSSync()
    }

    suspend fun renameShoppingList(id: String, name: String) {
        val existing = dao.getListByIdDirect(id)
        val list = ShoppingList(
            id = id,
            name = name,
            updatedAt = System.currentTimeMillis().toString(),
            isDeleted = false
        )
        dao.insertList(list)
        triggerWSSync()
    }

    suspend fun deleteShoppingList(id: String) {
        val existing = dao.getListByIdDirect(id) ?: ShoppingList(id = id, name = "")
        val updatedList = existing.copy(
            isDeleted = true,
            updatedAt = System.currentTimeMillis().toString()
        )
        dao.insertList(updatedList)
        triggerWSSync()
    }

    // Shopping Items management
    suspend fun addOrUpdateItem(id: String?, name: String, qty: String, price: Double?, listIds: List<String>, bought: Boolean = false, priority: String = "LOW") {
        val itemId = id ?: "item-${UUID.randomUUID()}"
        val item = ShoppingItem(
            id = itemId,
            name = name,
            qty = qty,
            price = price,
            bought = bought,
            listIds = listIds.ifEmpty { listOf("all") },
            updatedBy = getNickname(),
            updatedAt = System.currentTimeMillis().toString(),
            priority = priority,
            isDeleted = false
        )
        dao.insertItem(item)
        triggerWSSync()
    }

    suspend fun toggleItemBought(item: ShoppingItem) {
        val updated = item.copy(
            bought = !item.bought,
            updatedBy = getNickname(),
            updatedAt = System.currentTimeMillis().toString(),
            isDeleted = false
        )
        dao.insertItem(updated)
        // Auto system message in chat
        val chatMsg = ChatMessage(
            id = "msg-system-${UUID.randomUUID()}",
            coupleCode = getCoupleCode(),
            sender = "Σύστημα",
            text = "Το προϊόν '${item.name}' μεταφέρθηκε ${if (updated.bought) "στο καλάθι 🛒" else "στα προς αγορά 📦"} από το χρήστη $senderLabel",
            timestamp = System.currentTimeMillis().toString()
        )
        dao.insertMessage(chatMsg)
        triggerWSSync()
    }

    suspend fun deleteItem(item: ShoppingItem) {
        val updatedItem = item.copy(
            isDeleted = true,
            updatedAt = System.currentTimeMillis().toString()
        )
        dao.insertItem(updatedItem)
        triggerWSSync()
    }

    // Chat
    suspend fun sendChatMessage(text: String, sticker: String? = null) {
        val message = ChatMessage(
            id = "msg-${UUID.randomUUID()}",
            coupleCode = getCoupleCode(),
            sender = getNickname(),
            text = text,
            timestamp = System.currentTimeMillis().toString(),
            sticker = sticker
        )
        dao.insertMessage(message)
        wsManager.broadcastChat(text, sticker)
    }

    suspend fun addReactionToMessage(message: ChatMessage, emoji: String) {
        val currentReactions = message.reactions.toMutableList()
        if (currentReactions.contains(emoji)) {
            currentReactions.remove(emoji)
        } else {
            currentReactions.add(emoji)
        }
        val updatedMessage = message.copy(reactions = currentReactions)
        dao.insertMessage(updatedMessage)
        
        // Also notify via custom chat reaction text
        sendChatMessage("Αντέδρασε με $emoji στο μήνυμα: \"${message.text}\"")
    }

    suspend fun forceSync() {
        _isSyncing.value = true
        try {
            wsManager.fetchCloudSnapshot { cloudLists, cloudItems, cloudChats ->
                repositoryScope.launch {
                    try {
                        if (cloudLists.isNotEmpty()) {
                            val localListsMap = dao.getAllListsDirect().associateBy { it.id }
                            val listsToInsert = mutableListOf<ShoppingList>()
                            for (incomingList in cloudLists) {
                                val localList = localListsMap[incomingList.id]
                                if (localList == null) {
                                    listsToInsert.add(incomingList)
                                } else {
                                    val incomingTime = incomingList.updatedAt.toLongOrNull() ?: 0L
                                    val localTime = localList.updatedAt.toLongOrNull() ?: 0L
                                    if (incomingTime >= localTime) {
                                        listsToInsert.add(incomingList)
                                    }
                                }
                            }
                            if (listsToInsert.isNotEmpty()) {
                                dao.insertLists(listsToInsert)
                            }
                        }

                        if (cloudItems.isNotEmpty()) {
                            val localItemsMap = dao.getAllItemsDirect().associateBy { it.id }
                            val itemsToInsert = mutableListOf<ShoppingItem>()
                            for (incomingItem in cloudItems) {
                                val localItem = localItemsMap[incomingItem.id]
                                if (localItem == null) {
                                    itemsToInsert.add(incomingItem)
                                } else {
                                    val incomingTime = incomingItem.updatedAt.toLongOrNull() ?: 0L
                                    val localTime = localItem.updatedAt.toLongOrNull() ?: 0L
                                    if (incomingTime >= localTime) {
                                        itemsToInsert.add(incomingItem)
                                    }
                                }
                            }
                            if (itemsToInsert.isNotEmpty()) {
                                dao.insertItems(itemsToInsert)
                            }
                        }

                        if (cloudChats.isNotEmpty()) {
                            dao.insertMessages(cloudChats)
                        }

                        triggerWSSync()
                    } catch (e: Exception) {
                        Log.e("BentoRepo", "Error applying cloud snapshot", e)
                    }
                }
            }
            triggerWSSync()
        } finally {
            _isSyncing.value = false
        }
    }

    suspend fun clearChat() {
        dao.clearMessagesForRoom(getCoupleCode())
        val msg = ChatMessage(
            id = "msg-system-${UUID.randomUUID()}",
            coupleCode = getCoupleCode(),
            sender = "Σύστημα",
            text = "Η συνομιλία καθαρίστηκε από το χρήστη $senderLabel 🧹",
            timestamp = System.currentTimeMillis().toString()
        )
        dao.insertMessage(msg)
        wsManager.broadcastChat("Η συνομιλία καθαρίστηκε 🧹")
    }

    // Helper to send all lists, items, and chats to persistent Cloud Store & Real-time stream
    private suspend fun triggerWSSync() {
        try {
            _isSyncing.value = true
            val lists = dao.getAllListsDirect()
            val items = dao.getAllItemsDirect()
            val chats = dao.getMessagesDirect(getCoupleCode())
            wsManager.broadcastSync(lists, items, chats)
            _lastSyncTime.value = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e("BentoRepo", "Error fetching from db to sync", e)
        } finally {
            _isSyncing.value = false
        }
    }

    private val senderLabel: String
        get() = getNickname().ifBlank { "Συνεργάτης" }
}
