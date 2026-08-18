package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.repository.DecryptedField
import com.example.data.repository.FormFillRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

class FormFillAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var repository: FormFillRepository? = null

    companion object {
        var activeInstance: FormFillAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        OverlayState.setAccessibilityEnabled(true)
        repository = FormFillRepository(applicationContext)
        Log.d("FormFillAccessibility", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: "Unknown App"
        if (packageName == applicationContext.packageName) return // Ignore self

        val rootNode = rootInActiveWindow ?: return

        serviceScope.launch {
            val detectedNodes = mutableListOf<DetectedFieldInfo>()
            scanNodeForInputs(rootNode, detectedNodes)
            rootNode.recycle()

            if (detectedNodes.isNotEmpty()) {
                OverlayState.updateDetectedFields(packageName, detectedNodes)
            }
        }
    }

    private suspend fun scanNodeForInputs(node: AccessibilityNodeInfo, list: MutableList<DetectedFieldInfo>) {
        val className = node.className?.toString() ?: ""
        val isEditable = node.isEditable || className.contains("EditText", ignoreCase = true) ||
                (className.contains("View", ignoreCase = true) && (node.isFocusable || node.isClickable) && node.childCount == 0)

        if (isEditable) {
            val contextText = getNodeContextText(node)
            val (category, suggestion) = matchKeywordToSuggestion(contextText)

            val hint = node.hintText?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""

            list.add(
                DetectedFieldInfo(
                    fieldId = node.viewIdResourceName ?: "field_${list.size + 1}",
                    hintText = hint.ifBlank { text.ifBlank { contentDesc.ifBlank { contextText.take(24).ifBlank { "Input Field" } } } },
                    className = className,
                    matchedCategory = category,
                    suggestedValue = suggestion
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            scanNodeForInputs(child, list)
            child.recycle()
        }
    }

    private fun getApplicationRootNodes(): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()

        try {
            val allWindows = windows
            if (!allWindows.isNullOrEmpty()) {
                for (window in allWindows) {
                    val root = window.root ?: continue
                    val pkg = root.packageName?.toString() ?: ""
                    if (pkg != applicationContext.packageName) {
                        list.add(root)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FormFillAccessibility", "Error fetching windows: ${e.message}")
        }

        if (list.isEmpty()) {
            val activeRoot = rootInActiveWindow
            if (activeRoot != null) {
                list.add(activeRoot)
            }
        }

        return list
    }

    private fun getNodeContextText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()

        node.hintText?.let { sb.append(it).append(" ") }
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        node.viewIdResourceName?.let { sb.append(it).append(" ") }

        // Sibling & Parent label check (critical for WebViews and Google Forms)
        node.parent?.let { parent ->
            parent.contentDescription?.let { sb.append(it).append(" ") }
            parent.text?.let { sb.append(it).append(" ") }
            for (i in 0 until parent.childCount) {
                val child = parent.getChild(i)
                if (child != null && child != node && !child.isEditable) {
                    child.text?.let { sb.append(it).append(" ") }
                    child.contentDescription?.let { sb.append(it).append(" ") }
                    child.hintText?.let { sb.append(it).append(" ") }
                }
            }

            parent.parent?.let { grandParent ->
                grandParent.contentDescription?.let { sb.append(it).append(" ") }
                grandParent.text?.let { sb.append(it).append(" ") }
                for (j in 0 until grandParent.childCount) {
                    val gChild = grandParent.getChild(j)
                    if (gChild != null && gChild != parent && !gChild.isEditable) {
                        gChild.text?.let { sb.append(it).append(" ") }
                        gChild.contentDescription?.let { sb.append(it).append(" ") }
                    }
                }
            }
        }

        return sb.toString().lowercase(Locale.getDefault())
    }

    private suspend fun matchKeywordToSuggestion(text: String): Pair<String, String> {
        val repo = repository ?: return Pair("General Field", "Sample Value")
        val fields = repo.getDecryptedFieldsForProfile(1L) // Default profile

        return when {
            text.contains("pass") || text.contains("pwd") || text.contains("secret") -> Pair("Password", fields.find { it.fieldKey == "password" || it.fieldLabel.lowercase().contains("pass") }?.fieldValue ?: "P@ssword123!")
            text.contains("user") || text.contains("login") || text.contains("account") -> Pair("Username / Login ID", fields.find { it.fieldKey == "username" || it.fieldKey == "email" }?.fieldValue ?: "rahul_sharma")
            text.contains("first") && text.contains("name") -> Pair("First Name", fields.find { it.fieldKey == "first_name" }?.fieldValue ?: "Rahul")
            text.contains("last") && text.contains("name") -> Pair("Last Name", fields.find { it.fieldKey == "last_name" }?.fieldValue ?: "Sharma")
            text.contains("full") || text.contains("name") -> Pair("Full Name", fields.find { it.fieldKey == "full_name" }?.fieldValue ?: "Rahul Kumar Sharma")
            text.contains("email") || text.contains("mail") -> Pair("Email Address", fields.find { it.fieldKey == "email" }?.fieldValue ?: "rahul.sharma@example.com")
            text.contains("mobile") || text.contains("phone") || text.contains("contact") -> Pair("Mobile Number", fields.find { it.fieldKey == "mobile" }?.fieldValue ?: "+91 9876543210")
            text.contains("aadhaar") || text.contains("ssn") || text.contains("uid") -> Pair("Aadhaar / SSN", fields.find { it.fieldKey == "aadhaar" }?.fieldValue ?: "4512 8890 3341")
            text.contains("pan") || text.contains("tax") -> Pair("PAN Number", fields.find { it.fieldKey == "pan" }?.fieldValue ?: "ABCDE1234F")
            text.contains("address") || text.contains("street") -> Pair("Address Line 1", fields.find { it.fieldKey == "address_line_1" }?.fieldValue ?: "Flat 402, Green Valley")
            text.contains("city") -> Pair("City", fields.find { it.fieldKey == "city" }?.fieldValue ?: "Bengaluru")
            text.contains("state") -> Pair("State", fields.find { it.fieldKey == "state" }?.fieldValue ?: "Karnataka")
            text.contains("pin") || text.contains("zip") || text.contains("postal") -> Pair("PIN Code", fields.find { it.fieldKey == "pincode" }?.fieldValue ?: "560038")
            text.contains("account") || text.contains("bank") -> Pair("Account Number", fields.find { it.fieldKey == "account_no" }?.fieldValue ?: "50100234567890")
            text.contains("ifsc") || text.contains("swift") -> Pair("IFSC Code", fields.find { it.fieldKey == "ifsc" }?.fieldValue ?: "HDFC0001234")
            text.contains("dob") || text.contains("birth") -> Pair("Date of Birth", fields.find { it.fieldKey == "dob" }?.fieldValue ?: "15/08/1996")
            else -> Pair("Form Field", fields.firstOrNull()?.fieldValue ?: "Sample Data")
        }
    }

    private fun matchKeywordToSuggestionSync(text: String): Pair<String, String> {
        return when {
            text.contains("pass") || text.contains("pwd") || text.contains("secret") -> Pair("Password", "P@ssword123!")
            text.contains("user") || text.contains("login") || text.contains("account") -> Pair("Username / Login ID", "rahul_sharma")
            text.contains("first") && text.contains("name") -> Pair("First Name", "Rahul")
            text.contains("last") && text.contains("name") -> Pair("Last Name", "Sharma")
            text.contains("full") || text.contains("name") -> Pair("Full Name", "Rahul Kumar Sharma")
            text.contains("email") || text.contains("mail") -> Pair("Email Address", "rahul.sharma@example.com")
            text.contains("mobile") || text.contains("phone") || text.contains("contact") -> Pair("Mobile Number", "+91 9876543210")
            text.contains("address") || text.contains("street") -> Pair("Address Line 1", "Flat 402, Green Valley")
            text.contains("city") -> Pair("City", "Bengaluru")
            text.contains("state") -> Pair("State", "Karnataka")
            text.contains("pin") || text.contains("zip") -> Pair("PIN Code", "560038")
            text.contains("dob") || text.contains("birth") -> Pair("Date of Birth", "15/08/1996")
            else -> Pair("Form Field", "Sample Value")
        }
    }

    /**
     * Fills the currently focused node, or searches for a matching input node on screen by label, or fills the first available node.
     */
    fun fillFocusedNodeOrMatch(fieldLabel: String, fieldValue: String, categoryName: String = "Form Data", appName: String = "External App"): Boolean {
        val rootNodes = getApplicationRootNodes()
        if (rootNodes.isEmpty()) return false

        // 1. Check focused node across application windows
        for (rootNode in rootNodes) {
            val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                ?: rootNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)

            if (focusedNode != null && isNodeFillable(focusedNode)) {
                val success = applyTextToNode(focusedNode, fieldValue)
                if (success) {
                    logFillEvent(appName, fieldLabel, categoryName, fieldValue)
                    focusedNode.recycle()
                    rootNodes.forEach { it.recycle() }
                    return true
                }
            }
        }

        // 2. Scan all input nodes on current screen
        val inputNodes = mutableListOf<AccessibilityNodeInfo>()
        for (rootNode in rootNodes) {
            collectInputNodes(rootNode, inputNodes)
        }

        if (inputNodes.isEmpty()) {
            rootNodes.forEach { it.recycle() }
            return false
        }

        // Look for best matching node by field label
        val labelLower = fieldLabel.lowercase()
        var targetNode = inputNodes.firstOrNull { node ->
            val context = getNodeContextText(node)
            context.contains(labelLower) || isLabelMatch(labelLower, context)
        }

        if (targetNode == null) {
            if (labelLower.contains("pass") || labelLower.contains("pwd") || labelLower.contains("secret")) {
                targetNode = inputNodes.firstOrNull { it.isPassword } ?: inputNodes.lastOrNull()
            } else if (labelLower.contains("user") || labelLower.contains("email") || labelLower.contains("login")) {
                targetNode = inputNodes.firstOrNull { !it.isPassword } ?: inputNodes.first()
            } else {
                targetNode = inputNodes.first()
            }
        }

        val success = targetNode?.let { applyTextToNode(it, fieldValue) } == true
        if (success) {
            logFillEvent(appName, fieldLabel, categoryName, fieldValue)
        }

        // Recycle nodes
        inputNodes.forEach { it.recycle() }
        rootNodes.forEach { it.recycle() }
        return success
    }

    /**
     * Batch fills all matching input fields on screen (whether 3, 10, or any number) using profile fields.
     */
    fun fillAllFieldsBatch(fields: List<DecryptedField>, appName: String = "External Web Form"): Int {
        val rootNodes = getApplicationRootNodes()
        if (rootNodes.isEmpty()) return 0

        val inputNodes = mutableListOf<AccessibilityNodeInfo>()
        for (rootNode in rootNodes) {
            collectInputNodes(rootNode, inputNodes)
        }

        if (inputNodes.isEmpty()) {
            rootNodes.forEach { it.recycle() }
            return 0
        }

        var filledCount = 0

        for (node in inputNodes) {
            val contextText = getNodeContextText(node)
            val (label, value) = findBestValueForNodeContext(node, contextText, fields)

            val success = applyTextToNode(node, value)
            if (success) {
                filledCount++
                logFillEvent(appName, label, "Batch Fill", value)
            }
        }

        // Cleanup
        inputNodes.forEach { it.recycle() }
        rootNodes.forEach { it.recycle() }

        return filledCount
    }

    private fun findBestValueForNodeContext(
        node: AccessibilityNodeInfo,
        contextText: String,
        fields: List<DecryptedField>
    ): Pair<String, String> {
        val ctx = contextText.lowercase(Locale.getDefault())

        // Password & Confirm Password
        if (node.isPassword || ctx.contains("pass") || ctx.contains("pwd") || ctx.contains("secret")) {
            val passVal = fields.find { it.fieldKey == "password" || it.fieldLabel.lowercase().contains("pass") }?.fieldValue ?: "P@ssword123!"
            val label = if (ctx.contains("confirm") || ctx.contains("re-enter") || ctx.contains("repeat")) "Confirm Password" else "Password"
            return Pair(label, passVal)
        }

        // Company / Business
        if (ctx.contains("company") || ctx.contains("org") || ctx.contains("business") || ctx.contains("firm")) {
            val compVal = fields.find { it.fieldKey == "company" || it.fieldLabel.lowercase().contains("company") || it.fieldLabel.lowercase().contains("business") }?.fieldValue
                ?: fields.find { it.fieldKey == "college" || it.fieldKey == "university" }?.fieldValue
                ?: "Acme Corporation"
            return Pair("Company Name", compVal)
        }

        // First Name
        if (ctx.contains("first")) {
            val valStr = fields.find { it.fieldKey == "first_name" }?.fieldValue ?: "Rahul"
            return Pair("First Name", valStr)
        }

        // Last Name
        if (ctx.contains("last")) {
            val valStr = fields.find { it.fieldKey == "last_name" }?.fieldValue ?: "Sharma"
            return Pair("Last Name", valStr)
        }

        // Full Name / Name
        if (ctx.contains("full") || ctx.contains("name")) {
            val valStr = fields.find { it.fieldKey == "full_name" }?.fieldValue
                ?: fields.find { it.fieldKey == "first_name" }?.fieldValue
                ?: "Rahul Kumar Sharma"
            return Pair("Full Name", valStr)
        }

        // Email
        if (ctx.contains("email") || ctx.contains("mail")) {
            val valStr = fields.find { it.fieldKey == "business_email" || it.fieldKey == "email" }?.fieldValue ?: "rahul.sharma@example.com"
            return Pair("Email Address", valStr)
        }

        // Phone / Mobile
        if (ctx.contains("phone") || ctx.contains("mobile") || ctx.contains("contact") || ctx.contains("tel")) {
            val valStr = fields.find { it.fieldKey == "mobile" }?.fieldValue ?: "+91 9876543210"
            return Pair("Phone Number", valStr)
        }

        // Username / Login
        if (ctx.contains("user") || ctx.contains("login") || ctx.contains("account") || ctx.contains("id")) {
            val valStr = fields.find { it.fieldKey == "username" || it.fieldKey == "email" }?.fieldValue ?: "rahul_sharma"
            return Pair("Username", valStr)
        }

        // DOB / Birth
        if (ctx.contains("dob") || ctx.contains("birth") || ctx.contains("date")) {
            val valStr = fields.find { it.fieldKey == "dob" }?.fieldValue ?: "15/08/1996"
            return Pair("Date of Birth", valStr)
        }

        // Address
        if (ctx.contains("address") || ctx.contains("street")) {
            val valStr = fields.find { it.fieldKey == "address_line_1" }?.fieldValue ?: "Flat 402, Green Valley"
            return Pair("Address", valStr)
        }

        // City
        if (ctx.contains("city")) {
            val valStr = fields.find { it.fieldKey == "city" }?.fieldValue ?: "Bengaluru"
            return Pair("City", valStr)
        }

        // State
        if (ctx.contains("state")) {
            val valStr = fields.find { it.fieldKey == "state" }?.fieldValue ?: "Karnataka"
            return Pair("State", valStr)
        }

        // Zip / Pin Code
        if (ctx.contains("zip") || ctx.contains("pin") || ctx.contains("postal")) {
            val valStr = fields.find { it.fieldKey == "pincode" }?.fieldValue ?: "560038"
            return Pair("ZIP / PIN Code", valStr)
        }

        // Country
        if (ctx.contains("country")) {
            val valStr = fields.find { it.fieldKey == "country" }?.fieldValue ?: "India"
            return Pair("Country", valStr)
        }

        // Default fallback to first non-sensitive profile field
        val fallbackField = fields.firstOrNull { !it.isSensitive }
        return Pair(fallbackField?.fieldLabel ?: "Form Input", fallbackField?.fieldValue ?: "Sample Data")
    }

    private fun isNodeFillable(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        val className = node.className?.toString() ?: ""

        if (node.isEditable || node.isPassword) return true
        if (className.contains("EditText", ignoreCase = true) || className.contains("TextField", ignoreCase = true)) return true
        if (node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) return true

        val isButton = className.contains("Button", ignoreCase = true) ||
                className.contains("Image", ignoreCase = true) ||
                className.contains("Check", ignoreCase = true) ||
                className.contains("Radio", ignoreCase = true)

        if (!isButton && (node.isFocusable || node.isClickable || node.isFocused)) {
            if (className.contains("View", ignoreCase = true) ||
                className.contains("Input", ignoreCase = true) ||
                className.contains("Edit", ignoreCase = true) ||
                className.contains("Box", ignoreCase = true)
            ) {
                if (node.childCount <= 1) {
                    val ctx = getNodeContextText(node)
                    if (ctx.isNotBlank()) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun applyTextToNode(node: AccessibilityNodeInfo, text: String): Boolean {
        try {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            val arguments = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            var success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            if (!success) {
                val activeRoot = rootInActiveWindow
                val focused = activeRoot?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null && focused != node) {
                    focused.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                    success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                }
            }

            if (!success) {
                // Clipboard paste fallback for WebViews, password fields, or complex custom inputs
                try {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (clipboard != null) {
                        val clip = ClipData.newPlainText("form_fill_value", text)
                        clipboard.setPrimaryClip(clip)

                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                        success = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)

                        if (!success) {
                            val activeRoot = rootInActiveWindow
                            val focused = activeRoot?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                            focused?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            success = focused?.performAction(AccessibilityNodeInfo.ACTION_PASTE) == true
                        }
                    }
                } catch (e: Exception) {
                    Log.e("FormFillAccessibility", "Clipboard paste fallback error: ${e.message}")
                }
            }
            return success
        } catch (e: Exception) {
            Log.e("FormFillAccessibility", "Failed to set text on node: ${e.message}")
            return false
        }
    }

    private fun collectInputNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (!node.isVisibleToUser) return

        if (node.childCount > 0 && !node.isEditable && !node.className.toString().contains("EditText", ignoreCase = true)) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                collectInputNodes(child, list)
            }
            return
        }

        if (isNodeFillable(node)) {
            list.add(node)
            return
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectInputNodes(child, list)
        }
    }

    private fun isLabelMatch(label: String, context: String): Boolean {
        val l = label.lowercase(Locale.getDefault())
        val c = context.lowercase(Locale.getDefault())

        return when {
            l.contains("company") || l.contains("organization") || l.contains("business") ->
                c.contains("company") || c.contains("org") || c.contains("business") || c.contains("firm")
            l.contains("pass") || l.contains("pwd") || l.contains("secret") ->
                c.contains("pass") || c.contains("pwd") || c.contains("secret") || c.contains("pin")
            l.contains("user") || l.contains("login") || l.contains("account") ->
                c.contains("user") || c.contains("login") || c.contains("account") || c.contains("id") || c.contains("email") || c.contains("mail")
            l.contains("first") -> c.contains("first")
            l.contains("last") -> c.contains("last")
            l.contains("full") || l.contains("name") -> c.contains("full") || c.contains("name")
            l.contains("email") || l.contains("mail") -> c.contains("email") || c.contains("mail") || c.contains("user")
            l.contains("mobile") || l.contains("phone") || l.contains("contact") -> c.contains("mobile") || c.contains("phone") || c.contains("contact") || c.contains("tel")
            l.contains("dob") || l.contains("birth") -> c.contains("dob") || c.contains("birth") || c.contains("date")
            l.contains("address") -> c.contains("address") || c.contains("street")
            l.contains("city") -> c.contains("city") || c.contains("town")
            l.contains("pin") || l.contains("zip") -> c.contains("pin") || c.contains("zip")
            l.contains("state") -> c.contains("state")
            else -> false
        }
    }

    private fun logFillEvent(appName: String, label: String, category: String, value: String) {
        serviceScope.launch {
            repository?.logAutoFillEvent(appName, label, category, value)
        }
    }

    override fun onInterrupt() {
        OverlayState.setAccessibilityEnabled(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        activeInstance = null
        OverlayState.setAccessibilityEnabled(false)
    }
}
