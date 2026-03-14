package com.swarajai.cognitive

import android.content.Context
import android.util.Log

/**
 * Swaraj AI: The "Relay Race" Runtime Engine
 * 
 * Optimized for 4GB RAM phones. Models are swapped serially:
 * STT -> [Fast Path Check] -> SLM (Reasoning) -> Action -> TTS
 * 
 * KEY INSIGHT:
 * The model files are extracted from APK assets to internal storage.
 * This extraction must ONLY happen once (on first run).
 * If extraction happens every launch, we spike 800MB+ RAM -> OOM kill.
 */
class SerialLoadingEngine(
    private val context: Context,
    private val onOutput: ((String) -> Unit)? = null,
    private val onStateChanged: ((EngineState) -> Unit)? = null
) {

    private val TAG = "SwarajEngine"

    private val sttProvider = SwarajNativeSTTProvider(context, onOutput)
    private val llmProvider = LlamaInferenceProvider(context)
    private val ttsProvider = SwarajTTSProvider(context)
    private val accessibilityBridge = AccessibilityBridge(context)
    private val actionVerifier = ActionVerifier(context)

    /**
     * Called on app launch. Pre-warms Vosk model in background.
     * This ensures first "Tap to Speak" is instant, not slow.
     */
    fun preWarm() {
        // Native STT has no model to load — signal ready immediately on UI
        onOutput?.invoke("⚙️ Swaraj AI starting up...")

        // Pre-extract LLM model from APK assets in the background.
        // 379MB extraction takes ~10-20s. By the time user taps, it's ready.
        Thread {
            android.util.Log.i(TAG, "🔥 Pre-extracting Swaraj Brain from APK assets...")
            val internalModel = java.io.File(context.filesDir, "swaraj_brain.gguf")
            
            if (internalModel.exists() && internalModel.length() > 1_000_000) {
                android.util.Log.i(TAG, "✅ Brain already extracted (${internalModel.length() / 1024 / 1024} MB). Ready.")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onOutput?.invoke("✅ Swaraj AI Ready — Tap to Speak")
                }
                return@Thread
            }

            // Extract from APK assets (one-time operation)
            try {
                val assets = context.assets.list("models") ?: emptyArray()
                if (assets.contains("swaraj_brain.gguf")) {
                    android.util.Log.i(TAG, "📦 Extracting 379MB brain from APK... (first-time only)")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onOutput?.invoke("📦 First launch: Preparing AI Brain... (~30s)")
                    }
                    context.assets.open("models/swaraj_brain.gguf").use { input ->
                        internalModel.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 8 * 1024 * 1024)
                        }
                    }
                    android.util.Log.i(TAG, "✅ Brain extracted successfully!")
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "❌ Brain extraction failed: ${e.message}")
            }

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onOutput?.invoke("✅ Swaraj AI Ready — Tap to Speak")
            }
        }.start()
    }

    /**
     * Entry Point: User Trigger
     * PHASE 1: STT
     */
    fun startInteraction() {
        if (currentState != EngineState.IDLE) {
            Log.w(TAG, "⚠️ startInteraction() ignored — engine is $currentState")
            return
        }
        
        Log.i(TAG, "🏁 Phase 1: Activating Offline Ear...")
        onOutput?.invoke("⚡ Activating Offline Ear...")
        triggerHapticPulse(100)
        currentState = EngineState.LISTENING

        // Model is already pre-warmed - load() returns immediately if model != null
        sttProvider.load { success ->
            if (success) {
                sttProvider.listen { transcription ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val cleanTranscript = transcription.trim().lowercase()
                        
                        // NOISE FILTER
                        if (cleanTranscript.length < 3 || 
                            cleanTranscript == "है" || 
                            cleanTranscript == "हैं" ||
                            cleanTranscript == "the" ||
                            cleanTranscript == "a") {
                            Log.w(TAG, "🔇 Filtered noise: \"$transcription\"")
                            currentState = EngineState.IDLE
                            return@post
                        }

                        onOutput?.invoke("🗣️ Captured: \"$transcription\"")

                        // FAST PATH OPTIMIZATION: 0ms Latency
                        // Try matching command statically before loading the heavy 379MB LLM.
                        onOutput?.invoke("⚡ Analysing command instantly...")
                        val fastIntent = FastPathEngine.tryResolve(cleanTranscript)

                        if (fastIntent != null) {
                            onOutput?.invoke("🤖 Intent: $fastIntent (✨ Instant)")
                            // Skip STT stop wait, execute immediately
                            sttProvider.stop()
                            executeActionPhase(fastIntent)
                        } else {
                            // FAST PATH FAILED -> PHASE 1 -> PHASE 2 HANDOVER:
                            // We need full LLM cognition.
                            sttProvider.stop()
                            onOutput?.invoke("🧠 Swapping to LLM Brain... (~20s)")

                            // 500ms safety buffer — gives OS time to fully close audio device
                            // before llama.cpp's JNI layer tries to allocate its context.
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                startCognitionPhase(transcription)
                            }, 500)
                        }
                    }
                }

            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    onOutput?.invoke("❌ Error: Offline Ear failed to sync.")
                    currentState = EngineState.IDLE
                }
            }
        }
    }

    /**
     * PHASE 2: Cognitive Reasoning (Offline LLM)
     */
    private fun startCognitionPhase(text: String) {
        onOutput?.invoke("\n🧠 Swaraj Brain Activating...")
        onOutput?.invoke("📄 Processing: \"$text\"")
        Log.i(TAG, "🧠 Phase 2: Starting offline cognition for: $text")
        currentState = EngineState.THINKING
        triggerHapticPulse(50, 2)

        // CRITICAL: Run ENTIRELY on a background thread.
        // Never block the main thread with LLM loading (causes ANR/crash).
        Thread {
            try {
                onOutput?.invoke("⚙️ Loading reasoning engine...")
                llmProvider.load()

                onOutput?.invoke("⚡ Reasoning offline...")
                val intentJson = llmProvider.reason(text)
                onOutput?.invoke("🤖 Intent: $intentJson")

                // Free LLM memory now that reasoning is done
                llmProvider.unload()
                
                // Return to Main Thread for UI + Actions
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (intentJson.isEmpty() || intentJson == "{}") {
                        startSynthesisPhase("Sorry, I couldn't understand that command.")
                        return@post
                    }
                    executeActionPhase(intentJson)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ LLM Phase Error: ${e.message}")
                llmProvider.unload()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    startSynthesisPhase("I'm having trouble processing that. Please try again.")
                }
            }
        }.start()
    }

    /**
     * PHASE 3: Action Execution & Verification
     */
    private fun executeActionPhase(actionSchema: String) {
        Log.i(TAG, "🎬 Phase 3: Executing action: $actionSchema")
        currentState = EngineState.EXECUTING

        val executionSuccess = accessibilityBridge.execute(actionSchema)
        val feedbackText = actionVerifier.verify(actionSchema, executionSuccess)
        
        startSynthesisPhase(feedbackText)
    }

    /**
     * PHASE 4: TTS Feedback
     */
    private fun startSynthesisPhase(feedback: String) {
        currentState = EngineState.SPEAKING
        ttsProvider.speak(feedback) {
            Log.i(TAG, "✅ Interaction complete. Returning to IDLE.")
            currentState = EngineState.IDLE
        }
    }

    var currentState: EngineState = EngineState.IDLE
        private set(value) {
            field = value
            onStateChanged?.invoke(value)
        }

    enum class EngineState {
        IDLE, LISTENING, THINKING, EXECUTING, SPEAKING
    }

    private fun triggerHapticPulse(duration: Long, count: Int = 1) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            if (vibrator.hasVibrator()) {
                repeat(count) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    Thread.sleep(duration + 50)
                }
            }
        } catch (e: Exception) { /* IGNORE */ }
    }
}
