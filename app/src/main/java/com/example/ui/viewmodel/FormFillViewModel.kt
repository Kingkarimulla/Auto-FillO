package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AutoFillHistoryEntity
import com.example.data.model.CategoryType
import com.example.data.model.ProfileFieldEntity
import com.example.data.model.UserProfileEntity
import com.example.data.repository.DecryptedField
import com.example.data.repository.FormFillRepository
import com.example.service.FloatingOverlayService
import com.example.service.OverlayState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FormFillViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FormFillRepository(application)

    // Auth & App Lock State
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    // Active Selected Profile ID
    private val _activeProfileId = MutableStateFlow<Long>(1L)
    val activeProfileId: StateFlow<Long> = _activeProfileId.asStateFlow()

    val allProfiles: StateFlow<List<UserProfileEntity>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHistory: StateFlow<List<AutoFillHistoryEntity>> = repository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentHistory: StateFlow<List<AutoFillHistoryEntity>> = repository.getRecentHistory(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Decrypted Fields for currently selected profile
    val activeDecryptedFields: StateFlow<List<DecryptedField>> = _activeProfileId
        .flatMapLatest { profileId ->
            flow {
                val fields = repository.getDecryptedFieldsForProfile(profileId)
                emit(fields)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isFloatingServiceRunning: StateFlow<Boolean> = OverlayState.isFloatingServiceRunning
    val isAccessibilityEnabled: StateFlow<Boolean> = OverlayState.isAccessibilityEnabled

    init {
        viewModelScope.launch {
            repository.initializeDefaultDataIfNeeded()
            val profiles = repository.allProfiles.firstOrNull()
            profiles?.find { it.isDefault }?.let {
                _activeProfileId.value = it.id
            }
        }
    }

    fun login(emailOrPhone: String, pass: String): Boolean {
        if (emailOrPhone.isNotBlank() && pass.isNotBlank()) {
            _isLoggedIn.value = true
            _isAppLocked.value = false
            return true
        }
        return false
    }

    fun signUp(name: String, email: String, pass: String): Boolean {
        if (name.isNotBlank() && email.isNotBlank() && pass.isNotBlank()) {
            _isLoggedIn.value = true
            _isAppLocked.value = false
            return true
        }
        return false
    }

    fun unlockWithBiometricOrPin(pin: String): Boolean {
        if (pin == "1234" || pin.isEmpty()) {
            _isAppLocked.value = false
            return true
        }
        return false
    }

    fun lockApp() {
        _isAppLocked.value = true
    }

    fun setActiveProfile(profileId: Long) {
        _activeProfileId.value = profileId
        viewModelScope.launch {
            repository.setDefaultProfile(profileId)
        }
    }

    fun addNewProfile(name: String) {
        viewModelScope.launch {
            repository.addProfile(name)
        }
    }

    fun saveOrUpdateField(
        fieldId: Long = 0L,
        category: CategoryType,
        key: String,
        label: String,
        value: String,
        isSensitive: Boolean
    ) {
        viewModelScope.launch {
            if (fieldId != 0L) {
                repository.updateField(
                    id = fieldId,
                    profileId = _activeProfileId.value,
                    category = category,
                    key = key.ifBlank { label.lowercase().replace(" ", "_") },
                    label = label,
                    value = value,
                    isSensitive = isSensitive
                )
            } else {
                repository.saveField(
                    profileId = _activeProfileId.value,
                    category = category,
                    key = key.ifBlank { label.lowercase().replace(" ", "_") },
                    label = label,
                    value = value,
                    isSensitive = isSensitive
                )
            }
            refreshDecryptedFields()
        }
    }

    fun deleteField(fieldId: Long) {
        viewModelScope.launch {
            repository.deleteField(fieldId)
            refreshDecryptedFields()
        }
    }

    private fun refreshDecryptedFields() {
        val currentId = _activeProfileId.value
        _activeProfileId.value = currentId
    }

    fun toggleFloatingOverlay(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Toast.makeText(context, "Please enable 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            return
        }

        val serviceIntent = Intent(context, FloatingOverlayService::class.java)
        if (isFloatingServiceRunning.value) {
            context.stopService(serviceIntent)
            Toast.makeText(context, "Floating Overlay Stopped", Toast.LENGTH_SHORT).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Toast.makeText(context, "Floating Assistant Started!", Toast.LENGTH_SHORT).show()
        }
    }

    fun logAutoFillAction(appName: String, label: String, categoryName: String, value: String) {
        viewModelScope.launch {
            repository.logAutoFillEvent(appName, label, categoryName, value)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
