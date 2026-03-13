package com.swarajai.cognitive

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Swaraj AI: The "Relay Race" Runtime Engine
 * 
 * Optimized for 4GB RAM phones. Models are swapped serially:
 * STT -> [Fast Path Check] -> SLM (Reasoning) -> Action -> TTS
 */
class SerialLoadingEngine(private val context: Context) {

    private val TAG = "SwarajEngine"
    private val macroManager = MacroManager(context)

    enum class EngineState { IDLE, LISTENING, THINKING, EXECUTING, SPEAKING }
    private var currentState = EngineState.IDLE

    /**
     * Entry Point: Triggered by wake-word or button
     * Phase 1: Perception (IndicConformer)
     */
    fun startInteraction() {
        Log.i(TAG, "🏁 Relay Race Started - Phase 1: STT")
        
        loadModel(STT_MODEL_NAME)
        currentState = EngineState.LISTENING
        
        // Simulating transcription output
        val transcription = "whenever I say hospital call the doctor" 
        
        unloadModel(STT_MODEL_NAME) // 🚨 Clean RAM for next stage
        
        processText(transcription)
    }

    private fun processText(text: String) {
        // PHASE 2: Fast-Path / System 1 Check (Latency: <10ms)
        val macroActions = macroManager.checkFastPath(text)
        if (macroActions != null) {
            Log.i(TAG, "🚀 FAST-PATH: Instant Macro execution starting...")
            executeActions(macroActions)
            return
        }

        // PHASE 3: Slow-Path / System 2 Reasoning (Gemma 3 1B / Qwen 0.5B)
        Log.i(TAG, "🧠 SLOW-PATH: Initializing reasoning engine...")
        
        // 💎 UX RESILIENCE: Handle IO Latency with a physical cue
        triggerHapticPulse() 
        showThinkingUI() 

        // Strategy: Load the most capable model the hardware permits
        val modelToLoad = if (isHigherEndDevice()) "gemma3_1b.gguf" else "qwen2.5_0.5b.gguf"
        
        loadModel(modelToLoad) 
        currentState = EngineState.THINKING
        
        val slmOutput = simulateSLMReasoning(text) 
        
        unloadModel(modelToLoad) // 🚨 Vital: Release ~900MB RAM immediately
        
        handleSLMResult(slmOutput)
    }

    /**
     * UX Heartbeat: Prevents the app from feeling "frozen" during IO load (Phase 3).
     */
    private fun triggerHapticPulse() {
        // Implementation for haptic ticking or short vibration
        // Log.d(TAG, "📳 Haptic pulse triggered - Smoothing IO latency")
    }

    private fun showThinkingUI() {
        // Logic to show a shimmer or "Swaraj is thinking..." overlay
    }

    private fun isHigherEndDevice(): Boolean {
        // Check RAM and CPU to decide between Plan A (1B) or Plan B (0.5B)
        return true 
    }

    private fun handleSLMResult(jsonResult: String) {
        val json = JSONObject(jsonResult)
        val intent = json.getJSONObject("intent")
        val action = intent.getString("action")

        if (action == "CREATE_MACRO") {
            val trigger = intent.getString("trigger")
            val steps = intent.getJSONArray("steps").toString()
            
            // Check if it already exists before saving
            val saved = macroManager.saveMacro(trigger, steps)
            
            if (saved) {
                provideFeedback(getTemplateFor("macro_SAVE", trigger))
                println("💾 Macro Saved: $trigger")
            } else {
                provideFeedback("Swaraj: A macro with the word $trigger already exists.")
                println("⚠️ Macro Duplicate: $trigger")
            }
        } else {
            executeActions(jsonResult)
        }
    }

    /**
     * PHASE 4: Action Execution & Verification
     */
    private fun executeActions(actionSchema: String) {
        currentState = EngineState.EXECUTING
        Log.i(TAG, "🎬 Action Router: Executing...")

        // 1. Physical Toggle (via Accessibility or System API)
        val success = accessibilityBridge.execute(actionSchema)

        // 2. Verification Loop: Did the state actually change?
        val verificationMessage = actionVerifier.verify(actionSchema, success)
        
        // 3. Move to Final Phase
        generateAndSpeakResponse(verificationMessage)
    }

    /**
     * PHASE 5: Response Generation & TTS Feedback
     */
    private fun generateAndSpeakResponse(message: String) {
        currentState = EngineState.SPEAKING
        
        // 💡 UX Strategy: We use a lightweight template rather than re-loading the SLM
        // to save the 15-second "Thinking" delay for simple feedback.
        val spokenText = "Swaraj: $message"
        
        Log.i(TAG, "📢 Loading IndicTTS Engine...")
        loadModel(TTS_MODEL_NAME)
        
        ttsProvider.speak(spokenText) {
            Log.i(TAG, "✅ Interaction Complete. Shutting down engines.")
            unloadModel(TTS_MODEL_NAME)
            currentState = EngineState.IDLE
        }
    }

    // --- Core Component Bridges ---
    
    // In a real Android build, these are injected or initialized in onCreate
    private val accessibilityBridge = AccessibilityBridge(context)
    private val actionVerifier = ActionVerifier(context)
    private val ttsProvider = IndicTTSProvider(context)
    private val STT_MODEL_NAME = "indic_conformer_mobile.onnx"
    private val TTS_MODEL_NAME = "indic_tts_male.onnx"

    private fun loadModel(name: String) {
        Log.d(TAG, "💾 LOADING: $name into RAM (Est. +800MB)")
    }

    private fun unloadModel(name: String) {
        Log.d(TAG, "🗑️ UNLOADING: $name. RAM Cleared.")
        System.gc() // Force GC for safety on budget hardware
    }

    private fun simulateSLMReasoning(text: String): String {
        // This simulates the Gemma-3-1B output for a macro creation
        return """
        {
          "text": "$text",
          "intent": {
            "action": "CREATE_MACRO",
            "trigger": "hospital",
            "steps": [
              {"action": "CALL", "target": "Doctor"},
              {"action": "TOGGLE", "target": "Torch"}
            ]
          }
        }
        """.trimIndent()
    }
}
