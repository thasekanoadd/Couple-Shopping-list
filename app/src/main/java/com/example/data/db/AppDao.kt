package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ChatMessage
import com.example.data.model.ShoppingItem
import com.example.data.model.ShoppingList
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    // Shopping Lists
    @Query("SELECT * FROM shopping_lists WHERE isDeleted = 0")
    fun getAllLists(): Flow<List<ShoppingList>>

    @Query("SELECT * FROM shopping_lists")
    suspend fun getAllListsDirect(): List<ShoppingList>

    @Query("SELECT * FROM shopping_lists WHERE id = :id LIMIT 1")
    suspend fun getListByIdDirect(id: String): ShoppingList?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: ShoppingList)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLists(lists: List<ShoppingList>)

    @Query("DELETE FROM shopping_lists WHERE id = :id")
    suspend fun deleteListById(id: String)

    @Query("DELETE FROM shopping_lists")
    suspend fun clearAllLists()

    // Shopping Items
    @Query("SELECT * FROM shopping_items WHERE isDeleted = 0")
    fun getAllItems(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_items")
    suspend fun getAllItemsDirect(): List<ShoppingItem>

    @Query("SELECT * FROM shopping_items WHERE id = :id LIMIT 1")
    suspend fun getItemByIdDirect(id: String): ShoppingItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ShoppingItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ShoppingItem>)

    @Query("DELETE FROM shopping_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("DELETE FROM shopping_items")
    suspend fun clearAllItems()

    // Chat Messages
    @Query("SELECT * FROM chat_messages WHERE coupleCode = :coupleCode ORDER BY timestamp ASC")
    fun getMessages(coupleCode: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE coupleCode = :coupleCode ORDER BY timestamp ASC")
    suspend fun getMessagesDirect(coupleCode: String): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>)

    @Query("DELETE FROM chat_messages WHERE coupleCode = :coupleCode")
    suspend fun clearMessagesForRoom(coupleCode: String)
}
