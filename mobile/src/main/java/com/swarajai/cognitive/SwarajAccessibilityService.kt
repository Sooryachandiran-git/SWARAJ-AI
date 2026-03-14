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



    private var isWakeWordActive = false
    private val handler = Handler(Looper.getMainLooper())
    
    // Background engines for seamless execution
    private var wakeSttProvider: SwarajNativeSTTProvider? = null
    private var ttsProvider: SwarajTTSProvider? = null
    private var backgroundEngine: SerialLoadingEngine? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "✅ Swaraj Accessibility Service Connected")
        instance = this
        
        ttsProvider = SwarajTTSProvider(this)
        backgroundEngine = SerialLoadingEngine(
            context = this,
            onOutput = { Log.i(TAG, "Background Engine: $it") },
            onStateChanged = { state -> 
                if (state == SerialLoadingEngine.EngineState.IDLE && isWakeWordActive) {
                    // Re-arm wake word detector when interaction finishes
                    handler.postDelayed({ monitorAudio() }, 1000)
                }
            }
        )
        // Ensure GGUF model is available for background commands
        backgroundEngine?.preWarm()
    }

    fun startWakeWordDetection() {
        if (isWakeWordActive) return
        isWakeWordActive = true
        wakeSttProvider = SwarajNativeSTTProvider(this)
        monitorAudio()
    }

    fun stopWakeWordDetection() {
        isWakeWordActive = false
        wakeSttProvider?.stop()
        wakeSttProvider = null
    }

    private fun monitorAudio() {
        if (!isWakeWordActive) return
        
        if (wakeSttProvider == null) {
            wakeSttProvider = SwarajNativeSTTProvider(this, isWakeWordMode = true)
        }

        // CRITICAL BUG FIX: Do NOT try to listen for wake word if the AI is currently processing a command. 
        // This stops two SpeechRecognizers from accessing the mic at the exact same time, breaking both and causing a 'beep' loop!
        if (backgroundEngine?.currentState != SerialLoadingEngine.EngineState.IDLE) {
            Log.d(TAG, "⏸️ Background engine busy. Pausing wake word detector.")
            // It will be re-armed automatically by the backgroundEngine's onStateChanged callback!
            return
        }

        Log.d(TAG, "👂 Monitoring for Wake Word (Active)")
        
        wakeSttProvider?.listen { text ->
             val lower = text.lowercase()
             if (lower.contains("swaraj") || lower.contains("suraj") || 
                 lower.contains("shivaji") || lower.contains("siraj")) {
                  onWakeWordDetected()
             } else {
                  // Restart listening silently if wake word wasn't heard or an error happened
                  if (isWakeWordActive && backgroundEngine?.currentState == SerialLoadingEngine.EngineState.IDLE) {
                      handler.postDelayed({ monitorAudio() }, 200)
                  }
             }
        }
    }

    private fun onWakeWordDetected() {
        Log.i(TAG, "🎯 WAKE WORD DETECTED: 'Swaraj'")
        
        // CRITICAL BUG FIX (Only Works Once bug):
        // We MUST unload/destroy the WakeWord STT instance completely.
        // If we only call stop(), it still holds a system lock on the Android Microphone hardware,
        // which completely blocks the backgroundEngine from capturing your voice the second time!
        wakeSttProvider?.unload()
        wakeSttProvider = null
        
        ttsProvider?.speak("Yes, listening") {
            // Run the main voice interaction entirely in the background!
            // This prevents MainActivity from opening and ruining the screenshot.
            backgroundEngine?.startInteraction()
        }
    }

    /**
     * Executes global system actions.
     */
    fun performGlobalAction(action: String): Boolean {
        Log.i(TAG, "🎬 Global action: $action")
        return when (action.uppercase()) {
            "BACK"          -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME"          -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS"       -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "SETTINGS"      -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            "LOCK_SCREEN"   -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            "SCREENSHOT"    -> {
                // GLOBAL_ACTION_TAKE_SCREENSHOT requires API 28+
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                } else {
                    Log.w(TAG, "⚠️ Screenshot requires Android 9+")
                    false
                }
            }
            else -> {
                Log.w(TAG, "⚠️ Unknown global action: $action")
                false
            }
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
