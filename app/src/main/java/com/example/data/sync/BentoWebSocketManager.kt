package com.example.data.sync

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.ShoppingItem
import com.example.data.model.ShoppingList
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

enum class ConnectionStatus {
    CONNECTED, CONNECTING, DISCONNECTED
}

class BentoWebSocketManager(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    // Dedicated client with no read timeout for continuous real-time streaming
    private val streamClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var connectionStatusListener: ((ConnectionStatus) -> Unit)? = null
    var currentStatus = ConnectionStatus.DISCONNECTED
        private set(value) {
            field = value
            if (Looper.myLooper() == Looper.getMainLooper()) {
                connectionStatusListener?.invoke(value)
            } else {
                handler.post { connectionStatusListener?.invoke(value) }
            }
        }

    private var onSyncReceived: ((lists: List<ShoppingList>, items: List<ShoppingItem>, sender: String) -> Unit)? = null
    private var onChatReceived: ((ChatMessage) -> Unit)? = null
    private var onTypingIndicator: ((partnerName: String, isTyping: Boolean) -> Unit)? = null
    private var onSyncRequestedListener: (() -> Unit)? = null

    private var nickname: String = ""
    private var coupleCode: String = ""
    private var customServerUrl: String? = null

    private val KVDB_BUCKET = "CPVrmLbCo7hWqE9NuiEv6t"
    private val KVDB_BASE_URL = "https://kvdb.io/$KVDB_BUCKET"
    private val NTFY_BASE_URL = "https://ntfy.sh"

    private var isDisconnectIntentionally = false
    private val handler = Handler(Looper.getMainLooper())

    private var activeStreamCall: Call? = null
    private var streamThread: Thread? = null

    private val reconnectRunnable = Runnable {
        if (!isDisconnectIntentionally && currentStatus != ConnectionStatus.CONNECTED) {
            Log.d("BentoRealtime", "Attempting automatic cloud reconnection...")
            connect(nickname, coupleCode, customServerUrl)
        }
    }

    private fun scheduleReconnect() {
        if (isDisconnectIntentionally) return
        handler.removeCallbacks(reconnectRunnable)
        handler.postDelayed(reconnectRunnable, 4000)
    }

    fun setListeners(
        onStatusChange: (ConnectionStatus) -> Unit,
        onSync: (List<ShoppingList>, List<ShoppingItem>, String) -> Unit,
        onChat: (ChatMessage) -> Unit,
        onTyping: (String, Boolean) -> Unit,
        onSyncRequested: (() -> Unit)? = null
    ) {
        this.connectionStatusListener = onStatusChange
        this.onSyncReceived = onSync
        this.onChatReceived = onChat
        this.onTypingIndicator = onTyping
        this.onSyncRequestedListener = onSyncRequested
    }

    private fun getRoomKey(): String {
        return coupleCode.trim().lowercase().replace(" ", "_")
    }

    private fun getStreamUrl(): String {
        if (!customServerUrl.isNullOrBlank()) {
            return customServerUrl!!
        }
        val room = getRoomKey()
        return "$NTFY_BASE_URL/bento_couple_$room/json"
    }

    private fun getHttpPublishUrl(): String {
        val room = getRoomKey()
        return "$NTFY_BASE_URL/bento_couple_$room"
    }

    private fun getKvUrl(): String {
        val room = getRoomKey()
        return "$KVDB_BASE_URL/couple_$room"
    }

    fun connect(nickname: String, coupleCode: String, customUrl: String? = null) {
        this.nickname = nickname
        this.coupleCode = coupleCode
        this.customServerUrl = customUrl
        this.isDisconnectIntentionally = false
        handler.removeCallbacks(reconnectRunnable)

        disconnect()
        isDisconnectIntentionally = false

        if (coupleCode.isBlank()) {
            currentStatus = ConnectionStatus.DISCONNECTED
            return
        }

        currentStatus = ConnectionStatus.CONNECTING
        val streamUrl = getStreamUrl()
        Log.d("BentoRealtime", "Connecting to Cloud Realtime Stream at $streamUrl")

        // 1. Fetch persistent cloud snapshot immediately
        fetchCloudSnapshot { cloudLists, cloudItems, cloudChats ->
            Log.d("BentoRealtime", "Fetched initial cloud snapshot: ${cloudLists.size} lists, ${cloudItems.size} items, ${cloudChats.size} chats")
            currentStatus = ConnectionStatus.CONNECTED
            if (cloudLists.isNotEmpty() || cloudItems.isNotEmpty()) {
                handler.post {
                    onSyncReceived?.invoke(cloudLists, cloudItems, "CloudStorage")
                }
            }
            cloudChats.forEach { msg ->
                handler.post {
                    onChatReceived?.invoke(msg)
                }
            }
        }

        // 2. Open continuous real-time HTTP stream
        streamThread = thread(start = true, name = "BentoStreamListener") {
            try {
                val request = Request.Builder()
                    .url(streamUrl)
                    .header("Accept", "application/json, text/event-stream")
                    .build()

                val call = streamClient.newCall(request)
                activeStreamCall = call
                val response = call.execute()

                if (!response.isSuccessful) {
                    Log.e("BentoRealtime", "Stream HTTP error: ${response.code}")
                    response.close()
                    if (!isDisconnectIntentionally) {
                        currentStatus = ConnectionStatus.DISCONNECTED
                        scheduleReconnect()
                    }
                    return@thread
                }

                currentStatus = ConnectionStatus.CONNECTED
                Log.d("BentoRealtime", "Realtime stream connected successfully!")
                handler.post {
                    onSyncRequestedListener?.invoke()
                }

                val source = response.body?.source()
                if (source == null) {
                    response.close()
                    return@thread
                }

                while (!isDisconnectIntentionally && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isNotBlank()) {
                        handleIncomingStreamLine(line)
                    }
                }

                response.close()
            } catch (e: Exception) {
                if (!isDisconnectIntentionally) {
                    Log.w("BentoRealtime", "Stream disconnected or closed: ${e.message}")
                    currentStatus = ConnectionStatus.DISCONNECTED
                    scheduleReconnect()
                }
            }
        }
    }

    fun disconnect() {
        isDisconnectIntentionally = true
        handler.removeCallbacks(reconnectRunnable)
        try {
            activeStreamCall?.cancel()
        } catch (_: Exception) {}
        activeStreamCall = null
        streamThread = null
        currentStatus = ConnectionStatus.DISCONNECTED
    }

    private fun handleIncomingStreamLine(text: String) {
        try {
            val root = JSONObject(text)
            val event = root.optString("event")

            if (event == "open" || event == "keepalive") {
                currentStatus = ConnectionStatus.CONNECTED
                return
            }

            if (event == "message") {
                val messageContent = root.optString("message", "")
                if (messageContent.isNotBlank()) {
                    parseAndRouteMessage(messageContent)
                }
            } else {
                // If it was direct JSON payload
                parseAndRouteMessage(text)
            }
        } catch (e: Exception) {
            Log.e("BentoRealtime", "Error handling incoming stream line", e)
        }
    }

    private fun parseAndRouteMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")
            val roomCode = json.optString("coupleCode")

            // Check if matching room
            if (roomCode.isNotBlank() && !roomCode.equals(coupleCode, ignoreCase = true)) {
                return
            }

            val sender = json.optString("sender")
            if (sender == nickname) return // Ignore own echoed messages

            when (type) {
                "rejoin", "sync_request" -> {
                    Log.d("BentoRealtime", "Received sync request from $sender")
                    handler.post {
                        onSyncRequestedListener?.invoke()
                    }
                }
                "sync" -> {
                    val payload = json.optJSONObject("payload") ?: return
                    val (lists, items) = parseListsAndItems(payload)
                    currentStatus = ConnectionStatus.CONNECTED
                    handler.post {
                        onSyncReceived?.invoke(lists, items, sender)
                    }
                }
                "chat" -> {
                    val payload = json.optJSONObject("payload") ?: return
                    val msgId = payload.optString("id", UUID.randomUUID().toString())
                    val msgText = payload.optString("text", "")
                    val timestamp = payload.optString("timestamp", System.currentTimeMillis().toString())
                    val rawSticker = if (payload.isNull("sticker")) null else payload.optString("sticker", null)
                    val sticker = if (rawSticker == "null" || rawSticker.isNullOrBlank()) null else rawSticker

                    val chatMessage = ChatMessage(
                        id = msgId,
                        coupleCode = coupleCode,
                        sender = sender,
                        text = msgText,
                        timestamp = timestamp,
                        sticker = sticker
                    )
                    currentStatus = ConnectionStatus.CONNECTED
                    handler.post {
                        onChatReceived?.invoke(chatMessage)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BentoRealtime", "Failed to parse message: $text", e)
        }
    }

    private fun parseListsAndItems(payload: JSONObject): Pair<List<ShoppingList>, List<ShoppingItem>> {
        val listsArray = payload.optJSONArray("lists") ?: JSONArray()
        val itemsArray = payload.optJSONArray("items") ?: JSONArray()

        val lists = mutableListOf<ShoppingList>()
        for (i in 0 until listsArray.length()) {
            val obj = listsArray.getJSONObject(i)
            lists.add(
                ShoppingList(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    updatedAt = obj.optString("updatedAt", System.currentTimeMillis().toString()),
                    isDeleted = obj.optBoolean("isDeleted", false)
                )
            )
        }

        val items = mutableListOf<ShoppingItem>()
        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.getJSONObject(i)
            val listIdsJson = obj.optJSONArray("listIds") ?: JSONArray()
            val listIds = mutableListOf<String>()
            for (j in 0 until listIdsJson.length()) {
                listIds.add(listIdsJson.getString(j))
            }

            items.add(
                ShoppingItem(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    qty = obj.getString("qty"),
                    price = if (obj.isNull("price")) null else obj.getDouble("price"),
                    bought = obj.optBoolean("bought", false),
                    listIds = listIds,
                    updatedBy = obj.optString("updatedBy", ""),
                    updatedAt = obj.optString("updatedAt", System.currentTimeMillis().toString()),
                    priority = obj.optString("priority", "LOW"),
                    isDeleted = obj.optBoolean("isDeleted", false)
                )
            )
        }
        return Pair(lists, items)
    }

    /**
     * Fetches the full persistent snapshot from KVDB Cloud Store and/or ntfy cache.
     * This works regardless of whether the other device is online, locked, or turned off!
     */
    fun fetchCloudSnapshot(
        onResult: (lists: List<ShoppingList>, items: List<ShoppingItem>, chats: List<ChatMessage>) -> Unit
    ) {
        if (coupleCode.isBlank()) return

        val kvUrl = getKvUrl()
        val request = Request.Builder()
            .url(kvUrl)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("BentoRealtime", "Failed to read from persistent KVDB store: ${e.message}. Checking ntfy cache fallback...")
                fetchNtfyCacheFallback(onResult)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (resp.isSuccessful) {
                        val bodyString = resp.body?.string() ?: ""
                        if (bodyString.isNotBlank() && !bodyString.contains("Not Found", ignoreCase = true)) {
                            try {
                                val json = JSONObject(bodyString)
                                val (lists, items) = parseListsAndItems(json)

                                val chatsArray = json.optJSONArray("chats") ?: JSONArray()
                                val chats = mutableListOf<ChatMessage>()
                                for (i in 0 until chatsArray.length()) {
                                    val cObj = chatsArray.getJSONObject(i)
                                    val reactionsJson = cObj.optJSONArray("reactions") ?: JSONArray()
                                    val reactions = mutableListOf<String>()
                                    for (r in 0 until reactionsJson.length()) {
                                        reactions.add(reactionsJson.getString(r))
                                    }
                                    val rawSticker = if (cObj.isNull("sticker")) null else cObj.optString("sticker", null)
                                    chats.add(
                                        ChatMessage(
                                            id = cObj.getString("id"),
                                            coupleCode = cObj.optString("coupleCode", coupleCode),
                                            sender = cObj.getString("sender"),
                                            text = cObj.getString("text"),
                                            timestamp = cObj.getString("timestamp"),
                                            reactions = reactions,
                                            sticker = if (rawSticker == "null" || rawSticker.isNullOrBlank()) null else rawSticker
                                        )
                                    )
                                }

                                currentStatus = ConnectionStatus.CONNECTED
                                onResult(lists, items, chats)
                                return
                            } catch (e: Exception) {
                                Log.e("BentoRealtime", "Error parsing KVDB JSON: ${e.message}")
                            }
                        }
                    }
                    // If KVDB was empty, try ntfy cache fallback
                    fetchNtfyCacheFallback(onResult)
                }
            }
        })
    }

    private fun fetchNtfyCacheFallback(
        onResult: (lists: List<ShoppingList>, items: List<ShoppingItem>, chats: List<ChatMessage>) -> Unit
    ) {
        val room = getRoomKey()
        val ntfyCacheUrl = "$NTFY_BASE_URL/bento_couple_$room/json?poll=1&since=all"
        val request = Request.Builder().url(ntfyCacheUrl).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("BentoRealtime", "Ntfy cache fetch failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) return
                    val body = resp.body?.string() ?: return
                    val lines = body.lines()
                    var latestLists = listOf<ShoppingList>()
                    var latestItems = listOf<ShoppingItem>()
                    val chats = mutableListOf<ChatMessage>()

                    for (line in lines) {
                        if (line.isBlank()) continue
                        try {
                            val eventObj = JSONObject(line)
                            if (eventObj.optString("event") == "message") {
                                val msgText = eventObj.optString("message", "")
                                if (msgText.isNotBlank()) {
                                    val msgJson = JSONObject(msgText)
                                    val type = msgJson.optString("type")
                                    if (type == "sync") {
                                        val payload = msgJson.optJSONObject("payload")
                                        if (payload != null) {
                                            val (l, itms) = parseListsAndItems(payload)
                                            if (l.isNotEmpty()) latestLists = l
                                            if (itms.isNotEmpty()) latestItems = itms
                                        }
                                    } else if (type == "chat") {
                                        val payload = msgJson.optJSONObject("payload")
                                        if (payload != null) {
                                            val rawSticker = if (payload.isNull("sticker")) null else payload.optString("sticker", null)
                                            chats.add(
                                                ChatMessage(
                                                    id = payload.optString("id", UUID.randomUUID().toString()),
                                                    coupleCode = msgJson.optString("coupleCode", coupleCode),
                                                    sender = msgJson.optString("sender", "Partner"),
                                                    text = payload.optString("text", ""),
                                                    timestamp = payload.optString("timestamp", System.currentTimeMillis().toString()),
                                                    sticker = if (rawSticker == "null" || rawSticker.isNullOrBlank()) null else rawSticker
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    if (latestLists.isNotEmpty() || latestItems.isNotEmpty() || chats.isNotEmpty()) {
                        currentStatus = ConnectionStatus.CONNECTED
                        onResult(latestLists, latestItems, chats)
                    }
                }
            }
        })
    }

    /**
     * Broadcasts full snapshot to both Persistent Cloud Store (KVDB) and Real-time Stream (ntfy).
     */
    fun broadcastSync(lists: List<ShoppingList>, items: List<ShoppingItem>, chats: List<ChatMessage> = emptyList()) {
        if (coupleCode.isBlank()) return

        try {
            val listsJson = JSONArray()
            for (list in lists) {
                listsJson.put(JSONObject().apply {
                    put("id", list.id)
                    put("name", list.name)
                    put("updatedAt", list.updatedAt)
                    put("isDeleted", list.isDeleted)
                })
            }

            val itemsJson = JSONArray()
            for (item in items) {
                itemsJson.put(JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("qty", item.qty)
                    put("price", item.price ?: JSONObject.NULL)
                    put("bought", item.bought)
                    put("listIds", JSONArray(item.listIds))
                    put("updatedBy", item.updatedBy)
                    put("updatedAt", item.updatedAt)
                    put("priority", item.priority)
                    put("isDeleted", item.isDeleted)
                })
            }

            val chatsJson = JSONArray()
            for (c in chats.takeLast(50)) {
                chatsJson.put(JSONObject().apply {
                    put("id", c.id)
                    put("coupleCode", c.coupleCode)
                    put("sender", c.sender)
                    put("text", c.text)
                    put("timestamp", c.timestamp)
                    put("reactions", JSONArray(c.reactions))
                    put("sticker", c.sticker ?: JSONObject.NULL)
                })
            }

            val payload = JSONObject().apply {
                put("lists", listsJson)
                put("items", itemsJson)
                put("chats", chatsJson)
            }

            val fullSyncMessage = JSONObject().apply {
                put("type", "sync")
                put("coupleCode", coupleCode)
                put("sender", nickname)
                put("payload", payload)
            }

            val messageString = fullSyncMessage.toString()

            // 1. Asynchronously persist to KVDB Cloud Store
            val kvUrl = getKvUrl()
            val kvBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val kvRequest = Request.Builder().url(kvUrl).post(kvBody).build()
            client.newCall(kvRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w("BentoRealtime", "Failed to save cloud snapshot: ${e.message}")
                }
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    Log.d("BentoRealtime", "Cloud snapshot persistently saved to KVDB!")
                }
            })

            // 2. Publish real-time event to ntfy.sh stream
            val ntfyUrl = getHttpPublishUrl()
            val ntfyBody = messageString.toRequestBody("application/json; charset=utf-8".toMediaType())
            val ntfyRequest = Request.Builder().url(ntfyUrl).post(ntfyBody).build()
            client.newCall(ntfyRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.w("BentoRealtime", "Failed to push real-time event: ${e.message}")
                }
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e("BentoRealtime", "Error broadcasting sync", e)
        }
    }

    fun broadcastChat(text: String, sticker: String? = null) {
        if (coupleCode.isBlank()) return
        try {
            val payload = JSONObject().apply {
                put("id", "msg-${System.currentTimeMillis()}")
                put("text", text)
                put("timestamp", System.currentTimeMillis().toString())
                put("sticker", sticker ?: JSONObject.NULL)
            }

            val message = JSONObject().apply {
                put("type", "chat")
                put("coupleCode", coupleCode)
                put("sender", nickname)
                put("payload", payload)
            }

            val messageString = message.toString()

            // Publish to ntfy.sh stream
            val ntfyUrl = getHttpPublishUrl()
            val ntfyBody = messageString.toRequestBody("application/json; charset=utf-8".toMediaType())
            val ntfyRequest = Request.Builder().url(ntfyUrl).post(ntfyBody).build()
            client.newCall(ntfyRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e("BentoRealtime", "Error broadcasting chat", e)
        }
    }

    fun triggerSyncRequest() {
        if (coupleCode.isBlank()) return
        try {
            val message = JSONObject().apply {
                put("type", "rejoin")
                put("coupleCode", coupleCode)
                put("sender", nickname)
            }
            val messageString = message.toString()

            val ntfyUrl = getHttpPublishUrl()
            val ntfyBody = messageString.toRequestBody("application/json; charset=utf-8".toMediaType())
            val ntfyRequest = Request.Builder().url(ntfyUrl).post(ntfyBody).build()
            client.newCall(ntfyRequest).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {}
                override fun onResponse(call: Call, response: Response) {
                    response.close()
                }
            })
        } catch (_: Exception) {}
    }
}
