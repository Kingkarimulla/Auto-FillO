package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CategoryType(val displayName: String, val iconName: String) {
    PRIMARY("Primary Details", "person"),
    ADDRESS("Address Details", "location_on"),
    EDUCATION("Education Details", "school"),
    BANK("Bank Details", "account_balance"),
    EMPLOYMENT("Employment Details", "work"),
    FAMILY("Family Details", "people"),
    GOVERNMENT("Government & Official", "badge"),
    CUSTOM("Custom Fields", "tune")
}

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileName: String = "Personal Profile",
    val isDefault: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "profile_fields")
data class ProfileFieldEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val category: CategoryType,
    val fieldKey: String,        // e.g. "full_name", "email", "ifsc_code"
    val fieldLabel: String,      // e.g. "Full Name", "Account Number"
    val fieldValueEncrypted: String, // AES encrypted
    val isSensitive: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "autofill_history")
data class AutoFillHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val fieldLabel: String,
    val categoryName: String,
    val fieldValueMasked: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true
)

data class AppStats(
    val formsFilledToday: Int = 12,
    val timeSavedMinutes: Int = 45,
    val fieldsDetected: Int = 84,
    val profileCompletionPercent: Int = 85
)
