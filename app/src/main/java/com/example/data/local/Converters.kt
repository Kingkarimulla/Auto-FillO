package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.CategoryType

class Converters {
    @TypeConverter
    fun fromCategoryType(value: CategoryType): String = value.name

    @TypeConverter
    fun toCategoryType(value: String): CategoryType = try {
        CategoryType.valueOf(value)
    } catch (e: Exception) {
        CategoryType.PRIMARY
    }
}
