package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "shopping_lists")
@JsonClass(generateAdapter = true)
data class ShoppingList(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: String = System.currentTimeMillis().toString(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "shopping_items")
@JsonClass(generateAdapter = true)
data class ShoppingItem(
    @PrimaryKey val id: String,
    val name: String,
    val qty: String,
    val price: Double?,
    val bought: Boolean,
    val listIds: List<String>,
    val updatedBy: String,
    val updatedAt: String = System.currentTimeMillis().toString(),
    val priority: String = "LOW", // "LOW" (όταν μπορέσω), "MEDIUM" (επείγον), "HIGH" (πολύ επείγον)
    val isDeleted: Boolean = false
)

@Entity(tableName = "chat_messages")
@JsonClass(generateAdapter = true)
data class ChatMessage(
    @PrimaryKey val id: String,
    val coupleCode: String,
    val sender: String,
    val text: String,
    val timestamp: String,
    val reactions: List<String> = emptyList(),
    val sticker: String? = null // Emoji identifier or abbreviation for food sticker e.g. "🥚", "🍕"
)
