package com.swarajai.cognitive

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Swaraj AI: Primary Accessibility Service
 * 
 * This is the "God Mode" of the application. It allows the AI to perform
 * system-level actions like Global Back, Global Home, and Recent Apps.
 */
class SwarajAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SwarajService"
        var instance: SwarajAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "✅ Swaraj Accessibility Service Connected")
        instance = this
        // Configuration is handled via accessibility_service_config.xml
    }

    private var isWakeWordActive = false
    private val handler = Handler(Looper.getMainLooper())

    fun startWakeWordDetection() {
        isWakeWordActive = true
        monitorAudio()
    }

    fun stopWakeWordDetection() {
        isWakeWordActive = false
    }

    private fun monitorAudio() {
        // Real-time wake-word detection would go here.
        // Simulated timer removed to prevent unwanted app opening.
        Log.d(TAG, "👂 Monitoring for 'Swaraj' (Active)")
    }

    private fun onWakeWordDetected() {
        Log.i(TAG, "🎯 WAKE WORD DETECTED: 'Swaraj'")
        
        // Use a single intent to trigger main activity instead of new engine instance
        val intent = Intent(this, com.swarajai.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("VOICE_TRIGGER", true)
        }
        startActivity(intent)
        
        // Re-arm the detector after the interaction completes (simulated)
        handler.postDelayed({ monitorAudio() }, 20000)
    }

    /**
     * Executes global system actions.
     */
    fun performGlobalAction(action: String): Boolean {
        return when (action.uppercase()) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "SETTINGS" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            else -> false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Optional: Monitor UI changes if needed for reinforcement learning
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ Swaraj Accessibility Service Interrupted")
        instance = null
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }
}
