package com.example.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split(",")
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String {
        if (list == null) return ""
        return list.joinToString(",")
    }
}
