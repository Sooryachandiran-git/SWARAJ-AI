package com.swarajai.cognitive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Swaraj AI: 100% Offline STT using Android's Built-in On-Device Speech Engine
 *
 * Uses EXTRA_PREFER_OFFLINE = true to force the system to use its
 * locally-stored voice model (no internet required).
 *
 * On Samsung Galaxy (One UI 5/6, Android 13/14), the offline pack is
 * pre-installed. Zero extra downloads needed.
 *
 * RAM cost: ~0 MB (model lives in system, not our process).
 * Crash risk: None (pure Java system API, no JNI in our process).
 */
class SwarajNativeSTTProvider(
    private val context: Context,
    private val onOutput: ((String) -> Unit)? = null,
    private val isWakeWordMode: Boolean = false
) {
    private val TAG = "NativeSTT"
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    // Cache best partial — used as fallback if onResults() returns empty (Samsung offline quirk)
    private var lastPartialResult = ""
    // Guard: ensure onResult() is delivered EXACTLY ONCE per listen() session
    private var hasDeliveredResult = false

    fun listen(onResult: (String) -> Unit) {
        if (isListening) {
            Log.w(TAG, "Already listening, ignoring duplicate call")
            return
        }
        // Reset session state
        lastPartialResult = ""
        hasDeliveredResult = false

        // SpeechRecognizer MUST be created on the Main Thread
        android.os.Handler(android.os.Looper.getMainLooper()).post {

            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    Log.e(TAG, "❌ SpeechRecognizer not available on this device")
                    if (!isWakeWordMode) onOutput?.invoke("❌ Voice recognition not available")
                    onResult("")
                    return@post
                }

                // Destroy any old instance before creating new one
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

                    // Primary: Indian English (handles most commands + Hinglish base)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")

                    // Additional: Hindi (hi-IN) for Hinglish, Tamil (ta-IN) for Tanglish
                    putStringArrayListExtra(
                        "android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES",
                        arrayListOf("hi-IN", "ta-IN")
                    )

                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    // Force on-device offline recognition
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    
                    if (isWakeWordMode) {
                        // In background wake word mode, wait very long before timing out
                        // so Android doesn't stop/start repeatedly and make beep sounds constantly!
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 60000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 60000L)
                    } else {
                        // User command mode: Faster end-of-speech detection
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                    }
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        Log.i(TAG, "🎙️ On-device mic ready")
                        onOutput?.invoke("🎙️ Listening (Offline)...")
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Speech detected")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        Log.i(TAG, "End of speech — processing...")
                        onOutput?.invoke("⚙️ Processing voice...")
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        val errorMsg = getErrorText(error)
                        Log.e(TAG, "⚠️ Voice error: $errorMsg (code $error)")

                        // Samsung offline quirk: ERROR_NO_MATCH / ERROR_CLIENT means the final
                        // recognizer failed but we may have a good partial result.
                        if ((error == SpeechRecognizer.ERROR_NO_MATCH ||
                             error == SpeechRecognizer.ERROR_CLIENT) &&
                            lastPartialResult.length > 2 &&
                            !hasDeliveredResult) {  // Only if we haven't already delivered a result
                            Log.i(TAG, "📝 Using partial fallback: \"$lastPartialResult\"")
                            hasDeliveredResult = true
                            onResult(lastPartialResult)
                            return
                        }

                        if (!hasDeliveredResult && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            onOutput?.invoke("⚠️ Voice error: $errorMsg")
                        }
                        if (!hasDeliveredResult) onResult("")
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        if (hasDeliveredResult) return  // Guard: ignore duplicate callbacks
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.takeIf { it.length > 1 }
                            ?: lastPartialResult
                        Log.i(TAG, "📝 Recognised: \"$text\"")
                        hasDeliveredResult = true
                        onResult(text)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull() ?: return
                        if (partial.length > 2) {
                            lastPartialResult = partial  // Cache best partial
                            onOutput?.invoke("💬 \"$partial\"...")
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                speechRecognizer?.startListening(intent)

            } catch (e: Exception) {
                isListening = false
                Log.e(TAG, "❌ Failed to start listening: ${e.message}")
                onResult("")
            }
        }
    }

    fun stop() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                speechRecognizer?.stopListening()
                isListening = false
                Log.i(TAG, "🛑 Listening stopped")
            } catch (e: Exception) { /* ignore */ }
        }
    }

    fun unload() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                isListening = false
                Log.i(TAG, "🗑️ Native STT released")
            } catch (e: Exception) { /* ignore */ }
        }
    }

    // Needed for API compatibility with SerialLoadingEngine
    fun load(onReady: (Boolean) -> Unit = {}) {
        // Native STT has no model to load — always ready instantly
        onReady(true)
    }

    private fun getErrorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission missing"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Unknown error ($code)"
    }
}
