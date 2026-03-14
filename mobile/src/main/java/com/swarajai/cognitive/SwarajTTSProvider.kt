package com.swarajai.cognitive

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Swaraj AI: TTS Provider (Optimized for Hackathon English Response)
 * 
 * Uses the built-in Android TTS engine for Zero-Storage high-quality English synthesis.
 */
class SwarajTTSProvider(private val context: Context) : TextToSpeech.OnInitListener {
    private val TAG = "SwarajTTS"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeech: String? = null

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "❌ English not supported")
            } else {
                isInitialized = true
                Log.i(TAG, "✅ Swaraj Voice (English) initialized")
                
                // If there was a request before we were ready, speak it now
                pendingSpeech?.let {
                    speak(it) {}
                    pendingSpeech = null
                }
            }
        }
    }

    fun speak(text: String, onFinished: () -> Unit) {
        if (!isInitialized) {
            pendingSpeech = text
            return
        }
        
        Log.i(TAG, "🗣️ Swaraj: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "swaraj_tts")
        
        // Simulating the delay for the callback in a hackathon setting
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            onFinished()
        }, 2000)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isInitialized = false
    }
}
