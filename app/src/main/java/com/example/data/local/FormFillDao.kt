package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AutoFillHistoryEntity
import com.example.data.model.CategoryType
import com.example.data.model.ProfileFieldEntity
import com.example.data.model.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FormFillDao {

    // Profiles
    @Query("SELECT * FROM user_profiles ORDER BY isDefault DESC, createdAt ASC")
    fun getAllProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity): Long

    @Query("UPDATE user_profiles SET isDefault = 0")
    suspend fun clearDefaultProfiles()

    @Query("UPDATE user_profiles SET isDefault = 1 WHERE id = :profileId")
    suspend fun setDefaultProfile(profileId: Long)

    @Query("DELETE FROM user_profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: Long)

    // Fields
    @Query("SELECT * FROM profile_fields WHERE profileId = :profileId ORDER BY id ASC")
    fun getFieldsForProfile(profileId: Long): Flow<List<ProfileFieldEntity>>

    @Query("SELECT * FROM profile_fields WHERE profileId = :profileId AND category = :category")
    suspend fun getFieldsByCategory(profileId: Long, category: CategoryType): List<ProfileFieldEntity>

    @Query("SELECT * FROM profile_fields WHERE profileId = :profileId")
    suspend fun getFieldsListForProfile(profileId: Long): List<ProfileFieldEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: ProfileFieldEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFields(fields: List<ProfileFieldEntity>)

    @Update
    suspend fun updateField(field: ProfileFieldEntity)

    @Query("DELETE FROM profile_fields WHERE id = :fieldId")
    suspend fun deleteField(fieldId: Long)

    @Query("DELETE FROM profile_fields WHERE profileId = :profileId")
    suspend fun deleteFieldsForProfile(profileId: Long)

    // History
    @Query("SELECT * FROM autofill_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<AutoFillHistoryEntity>>

    @Query("SELECT * FROM autofill_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<AutoFillHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: AutoFillHistoryEntity)

    @Query("DELETE FROM autofill_history")
    suspend fun clearHistory()
}
