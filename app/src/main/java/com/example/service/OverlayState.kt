package com.example.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DetectedFieldInfo(
    val fieldId: String,
    val hintText: String,
    val className: String,
    val matchedCategory: String,
    val suggestedValue: String
)

object OverlayState {
    private val _isFloatingServiceRunning = MutableStateFlow(false)
    val isFloatingServiceRunning: StateFlow<Boolean> = _isFloatingServiceRunning.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _detectedFields = MutableStateFlow<List<DetectedFieldInfo>>(emptyList())
    val detectedFields: StateFlow<List<DetectedFieldInfo>> = _detectedFields.asStateFlow()

    private val _lastDetectedApp = MutableStateFlow("Current App")
    val lastDetectedApp: StateFlow<String> = _lastDetectedApp.asStateFlow()

    fun setFloatingServiceRunning(running: Boolean) {
        _isFloatingServiceRunning.value = running
    }

    fun setAccessibilityEnabled(enabled: Boolean) {
        _isAccessibilityEnabled.value = enabled
    }

    fun updateDetectedFields(appName: String, fields: List<DetectedFieldInfo>) {
        _lastDetectedApp.value = appName
        _detectedFields.value = fields
    }
}
