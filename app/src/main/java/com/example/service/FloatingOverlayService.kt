package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.repository.FormFillRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: FrameLayout? = null
    private var popupView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var isPopupOpen = false

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var repository: FormFillRepository

    companion object {
        const val CHANNEL_ID = "FormFillOverlayChannel"
        const val NOTIFICATION_ID = 2026
        const val ACTION_STOP_SERVICE = "com.example.STOP_FLOATING_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        repository = FormFillRepository(applicationContext)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        if (Settings.canDrawOverlays(this)) {
            initFloatingWidget()
            OverlayState.setFloatingServiceRunning(true)
        } else {
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initFloatingWidget() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        floatingView = FrameLayout(this).apply {
            val density = resources.displayMetrics.density
            setPadding((12 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())

            // Custom floating rectangular curved bar background
            val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 24f * density
                colors = intArrayOf(0xFF0D47A1.toInt(), 0xFF004D40.toInt()) // Deep Blue to Teal gradient
                orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
                setStroke((2 * density).toInt(), 0xFFFFC107.toInt()) // Amber curved border stroke
            }
            background = bgDrawable

            val barLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                val iconTv = TextView(context).apply {
                    text = "⚡"
                    textSize = 16f
                    setTextColor(0xFFFFC107.toInt())
                }

                val labelTv = TextView(context).apply {
                    text = "Fill Form"
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding((6 * density).toInt(), 0, (4 * density).toInt(), 0)
                }

                val arrowTv = TextView(context).apply {
                    text = "▾"
                    textSize = 11f
                    setTextColor(0xFFFFC107.toInt())
                }

                addView(iconTv)
                addView(labelTv)
                addView(arrowTv)
            }

            addView(barLayout, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingView?.setOnTouchListener { _, event ->
            val p = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = initialX + (event.rawX - initialTouchX).toInt()
                    p.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = Math.abs(event.rawX - initialTouchX)
                    val diffY = Math.abs(event.rawY - initialTouchY)
                    if (diffX < 10 && diffY < 10) {
                        toggleAutoFillPopup()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleAutoFillPopup() {
        if (isPopupOpen) {
            removePopupView()
            isPopupOpen = false
            return
        }

        val context = applicationContext
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val popupParams = WindowManager.LayoutParams(
            (280 * resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (params?.x ?: 20) + 70
            y = (params?.y ?: 300)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            val bg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 24f
                setColor(0xEE0A192F.toInt()) // Dark navy glassmorphic
                setStroke(3, 0xFF00897B.toInt()) // Teal border
            }
            background = bg
        }

        // Title Header
        val titleText = TextView(context).apply {
            text = "✨ FormFill Pro Quick Assistant"
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
        }
        container.addView(titleText)

        val subText = TextView(context).apply {
            text = "Tap field to paste into focused input:"
            textSize = 11f
            setTextColor(0xFFB0BEC5.toInt())
            setPadding(0, 4, 0, 16)
        }
        container.addView(subText)

        // Load fields from repository
        serviceScope.launch {
            val decryptedFields = repository.getDecryptedFieldsForProfile(1L)
            val displayFields = decryptedFields.take(6)

            displayFields.forEach { field ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(16, 12, 16, 12)
                    gravity = Gravity.CENTER_VERTICAL
                    val rowBg = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 12f
                        setColor(0x331565C0.toInt())
                    }
                    background = rowBg
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 4, 0, 8) }
                    layoutParams = lp

                    setOnClickListener {
                        val accService = FormFillAccessibilityService.activeInstance
                        if (accService != null) {
                            val success = accService.fillFocusedNodeOrMatch(field.fieldLabel, field.fieldValue, field.category.displayName)
                            if (success) {
                                Toast.makeText(context, "Filled ${field.fieldLabel}!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Tap an input field on screen to select target", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Enable Accessibility Service in Settings", Toast.LENGTH_LONG).show()
                        }
                        removePopupView()
                        isPopupOpen = false
                    }
                }

                val labelTv = TextView(context).apply {
                    text = field.fieldLabel
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(0xFFFFC107.toInt())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val valTv = TextView(context).apply {
                    text = if (field.isSensitive) "••••" else field.fieldValue.take(16)
                    textSize = 12f
                    setTextColor(0xFFE0E0E0.toInt())
                }

                row.addView(labelTv)
                row.addView(valTv)
                container.addView(row)
            }

            // Fill All Button
            val fillAllBtn = TextView(context).apply {
                text = "⚡ Fill Entire Form (Batch)"
                textSize = 13f
                gravity = Gravity.CENTER
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(0, 16, 0, 16)
                val btnBg = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 16f
                    setColor(0xFF00897B.toInt())
                }
                background = btnBg
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 4) }
                layoutParams = lp

                setOnClickListener {
                    val accService = FormFillAccessibilityService.activeInstance
                    if (accService != null && decryptedFields.isNotEmpty()) {
                        val count = accService.fillAllFieldsBatch(decryptedFields)
                        if (count > 0) {
                            Toast.makeText(context, "⚡ Auto-filled $count field(s) in form!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "No input fields detected on current screen", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Accessibility Service is OFF", Toast.LENGTH_SHORT).show()
                    }
                    removePopupView()
                    isPopupOpen = false
                }
            }
            container.addView(fillAllBtn)
        }

        popupView = container
        try {
            windowManager?.addView(popupView, popupParams)
            isPopupOpen = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removePopupView() {
        popupView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        popupView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FormFill Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps floating assistant overlay button active"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpenIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStopIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FormFill Pro Assistant Running")
            .setContentText("Tap floating button anytime to auto-fill form inputs")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pendingOpenIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Overlay", pendingStopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removePopupView()
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        OverlayState.setFloatingServiceRunning(false)
    }
}
