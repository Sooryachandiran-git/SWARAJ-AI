package com.swarajai.cognitive

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File

/**
 * Swaraj AI: Llama Inference Provider
 *
 * CRITICAL FIX (Hackathon Edition):
 * 
 * The model file (swaraj_brain.gguf, ~379MB) is stored in assets/models/.
 * 
 * WRONG approach: Copy the 379MB file to filesDir every time -> OOM kill
 * RIGHT approach: Extract ONCE to filesDir on first run, then reuse directly.
 * 
 * The model is kept in internal storage (context.filesDir) after the first extraction.
 * All subsequent loads read from there — no redundant copying, no OOM.
 */
class LlamaInferenceProvider(private val context: Context) {
    private val TAG = "SwarajLLM"
    private var isLoaded = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _llmFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val llmFlow = _llmFlow.asSharedFlow()

    private val llamaHelper by lazy {
        LlamaHelper(
            contentResolver = context.contentResolver,
            scope = scope,
            sharedFlow = _llmFlow
        )
    }

    /**
     * Finds the model. Priority:
     * 1. Already extracted to internal storage (fast path, most runs)
     * 2. Inside APK assets -> extract ONCE to internal storage
     * 3. On SDCARD via ADB push
     * 
     * KEY: We never extract if the file already exists. This prevents the 379MB
     * copy from running every time the app opens, which was the OOM killer.
     */
    private fun findModelFile(): File? {
        // PreWarm() already extracted the model from APK assets to filesDir.
        // We just need to find it here.
        val internalModel = File(context.filesDir, "swaraj_brain.gguf")
        if (internalModel.exists() && internalModel.length() > 1_000_000) {
            Log.i(TAG, "✅ Brain found: ${internalModel.length() / 1024 / 1024} MB")
            return internalModel
        }

        // Fallback: ADB-pushed model on sdcard (useful for testing different models)
        val sdcardModel = File(
            android.os.Environment.getExternalStorageDirectory(),
            "SwarajAI/swaraj_brain.gguf"
        )
        if (sdcardModel.exists()) {
            Log.i(TAG, "📋 Using SDCARD model: ${sdcardModel.absolutePath}")
            return sdcardModel
        }

        Log.e(TAG, "❌ Brain not found! Ensure app was opened once for extraction.")
        return null
    }

    fun load() {
        if (isLoaded) {
            Log.i(TAG, "✅ Brain already loaded, skipping.")
            return
        }

        val modelFile = findModelFile() ?: run {
            Log.e(TAG, "❌ No model file found. Cannot load LLM.")
            return
        }

        try {
            Log.i(TAG, "🧠 Loading Swaraj Brain from ${modelFile.absolutePath}...")

            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                modelFile
            )
            Log.i(TAG, "📎 Content URI: $contentUri")

            val latch = java.util.concurrent.CountDownLatch(1)

            llamaHelper.load(
                contentUri.toString(),
                512    // Minimal context window - reduces KV cache RAM from 300MB to ~50MB
            ) { contextId: Long ->
                Log.i(TAG, "✅ Brain loaded! Context ID: $contextId")
                isLoaded = true
                latch.countDown()
            }

            // Wait up to 90 seconds for large model loading
            val loaded = latch.await(90, java.util.concurrent.TimeUnit.SECONDS)
            if (!loaded) {
                Log.e(TAG, "❌ Model load timed out after 90s")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ LLM Fatal Error: ${e.message}")
        }
    }

    fun reason(userInput: String): String {
        if (!isLoaded) {
            Log.w(TAG, "⚠️ LLM not loaded — returning empty intent")
            return "{}"
        }

        // System prompt with Hinglish + Tanglish + English command patterns
        val systemPrompt = """
You are Swaraj AI, an offline voice assistant for India.
Map the user's voice command to ONE JSON action. Output ONLY the JSON. No explanation.

LANGUAGE PATTERNS you understand:
- English: "open whatsapp", "turn on flashlight", "call mom"
- Hinglish: "whatsapp kholo", "torch on kar", "camera chalu kar", "band kar"
- Tanglish: "whatsapp-a thirav", "torch pannu", "camera on pannu", "mooду pannu", "call pannu"
  Tamil verb mappings: pannu/panu=do/enable, thirav=open, moodu/moodu=close, 
  on pannu=turn on, off pannu=turn off, podu=enable, thora=stop/little

VALID ACTIONS (output exactly):
{"action":"FLASHLIGHT_ON"}
{"action":"FLASHLIGHT_OFF"}
{"action":"OPEN_APP","package":"com.whatsapp"}
{"action":"OPEN_APP","package":"com.android.camera2"}
{"action":"OPEN_APP","package":"com.google.android.youtube"}
{"action":"OPEN_APP","package":"com.google.android.apps.maps"}
{"action":"OPEN_APP","package":"com.android.settings"}
{"action":"OPEN_APP","package":"com.android.chrome"}
{"action":"OPEN_APP","package":"com.google.android.gm"}
{"action":"OPEN_APP","package":"com.spotify.music"}
{"action":"OPEN_APP","package":"com.instagram.android"}
{"action":"CALL","contact":"NAME"}
{"action":"OPEN_WHATSAPP","contact":"NAME"}
{"action":"NAVIGATE_HOME"}
{"action":"NAVIGATE_BACK"}
{"action":"NAVIGATE_RECENTS"}
{"action":"TAKE_SCREENSHOT"}
{"action":"VOLUME_UP"}
{"action":"VOLUME_DOWN"}
{"action":"VOLUME_MUTE"}
{"action":"BRIGHTNESS_UP"}
{"action":"BRIGHTNESS_DOWN"}
{"action":"BRIGHTNESS_MAX"}
{"action":"BRIGHTNESS_MIN"}

EXAMPLES:
"torch pannu" -> {"action":"FLASHLIGHT_ON"}
"torch on pannu" -> {"action":"FLASHLIGHT_ON"}
"torch off pannu" -> {"action":"FLASHLIGHT_OFF"}
"whatsapp thirav" -> {"action":"OPEN_APP","package":"com.whatsapp"}
"whatsapp-a thirav" -> {"action":"OPEN_APP","package":"com.whatsapp"}
"camera on pannu" -> {"action":"OPEN_APP","package":"com.android.camera2"}
"amma-va call pannu" -> {"action":"CALL","contact":"amma"}
"torch on kar" -> {"action":"FLASHLIGHT_ON"}
"whatsapp kholo" -> {"action":"OPEN_APP","package":"com.whatsapp"}
"ghar ja" -> {"action":"NAVIGATE_HOME"}
"send message to mom" -> {"action":"OPEN_WHATSAPP","contact":"mom","message":""}
"whatsapp mom" -> {"action":"OPEN_WHATSAPP","contact":"mom","message":""}
"send hi to mom on whatsapp" -> {"action":"OPEN_WHATSAPP","contact":"mom","message":"hi"}
"send hello message to john in whatsapp" -> {"action":"OPEN_WHATSAPP","contact":"john","message":"hello"}
"whatsapp mom good morning" -> {"action":"OPEN_WHATSAPP","contact":"mom","message":"good morning"}
"message karo mom ko hello" -> {"action":"OPEN_WHATSAPP","contact":"mom","message":"hello"}
"amma-ku hi message pannu" -> {"action":"OPEN_WHATSAPP","contact":"amma","message":"hi"}
"send whatsapp to john" -> {"action":"OPEN_WHATSAPP","contact":"john","message":""}
"open whatsapp" -> {"action":"OPEN_APP","package":"com.whatsapp"}
"call mom" -> {"action":"CALL","contact":"mom"}
"brightness up" -> {"action":"BRIGHTNESS_UP"}
"brightness down" -> {"action":"BRIGHTNESS_DOWN"}
"increase brightness" -> {"action":"BRIGHTNESS_UP"}
"decrease brightness" -> {"action":"BRIGHTNESS_DOWN"}
"brightness badhao" -> {"action":"BRIGHTNESS_UP"}
"brightness kam karo" -> {"action":"BRIGHTNESS_DOWN"}
"brightness koopdu" -> {"action":"BRIGHTNESS_UP"}
"volume up" -> {"action":"VOLUME_UP"}
"volume down" -> {"action":"VOLUME_DOWN"}
"silent karo" -> {"action":"VOLUME_MUTE"}
"take screenshot" -> {"action":"TAKE_SCREENSHOT"}
""".trimIndent()

        val prompt = "<|im_start|>system\n$systemPrompt<|im_end|>\n<|im_start|>user\n$userInput<|im_end|>\n<|im_start|>assistant\n"

        var fullResponse = ""

        try {
            runBlocking {
                withTimeout(20_000) {
                    val job = scope.launch {
                        llamaHelper.predict(prompt)
                        llmFlow.collect { event ->
                            when (event) {
                                is LlamaHelper.LLMEvent.Ongoing -> {
                                    fullResponse += event.word
                                    if (fullResponse.length % 10 == 0) {
                                        Log.d(TAG, "⚡ Streaming: ...${fullResponse.takeLast(10)}")
                                    }
                                    // Stop collecting once we have a complete JSON
                                    if (fullResponse.contains("}")) return@collect
                                }
                                is LlamaHelper.LLMEvent.Done -> {
                                    Log.i(TAG, "✅ LLM generation complete")
                                    return@collect
                                }
                                is LlamaHelper.LLMEvent.Error -> {
                                    Log.e(TAG, "❌ LLM Error: ${event.message}")
                                    return@collect
                                }
                                is LlamaHelper.LLMEvent.Started -> Log.d(TAG, "🚀 LLM started")
                                is LlamaHelper.LLMEvent.Loaded -> Log.d(TAG, "📚 LLM ready")
                                else -> {}
                            }
                        }
                    }
                    job.join()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Reasoning timed out: ${e.message}")
            try { llamaHelper.stopPrediction() } catch (ignored: Exception) {}
        }

        // ── STAGE 1: Strip special tokens ───────────────────────────────────
        val cleaned = fullResponse
            .replace("<|im_end|>", "")
            .replace("<|im_start|>", "")
            .replace("assistant", "")
            .replace("```json", "")
            .replace("```", "")
            .trim()

        Log.i(TAG, "🤖 Raw LLM output: $cleaned")

        // ── STAGE 2: Try to extract JSON ─────────────────────────────────────
        val jsonCandidate = try {
            val start = cleaned.indexOf("{")
            val end   = cleaned.lastIndexOf("}")
            if (start != -1 && end != -1 && end > start) cleaned.substring(start, end + 1)
            else ""
        } catch (e: Exception) { "" }

        // Validate it's parseable JSON with an "action" field
        if (jsonCandidate.isNotBlank()) {
            try {
                val parsed = org.json.JSONObject(jsonCandidate)
                if (parsed.has("action")) {
                    Log.i(TAG, "✅ Valid JSON intent: $jsonCandidate")
                    return jsonCandidate
                }
            } catch (_: Exception) {}
        }

        // ── STAGE 3: Keyword fallback ───────────────────────────────────────
        // Check BOTH the LLM response AND the original user input.
        // If Qwen says "I can't do that" (no keywords), userInput still has "take screenshot".
        Log.w(TAG, "⚠️ No JSON from LLM — running keyword fallback")
        val lower = (cleaned + " " + userInput).lowercase()  // combine both!

        val fallbackJson = when {
            lower.contains("flashlight") || lower.contains("torch") ||
            lower.contains("pannu") && (lower.contains("torch") || lower.contains("light")) ->
                if (lower.contains("off") || lower.contains("band") || lower.contains("disable"))
                    """{"action":"FLASHLIGHT_OFF"}"""
                else
                    """{"action":"FLASHLIGHT_ON"}"""

            lower.contains("brightness") || lower.contains("bright") ->
                if (lower.contains("down") || lower.contains("decrease") ||
                    lower.contains("low") || lower.contains("reduce") || lower.contains("dim") ||
                    lower.contains("kam") || lower.contains("kurai"))
                    """{"action":"BRIGHTNESS_DOWN"}"""
                else
                    """{"action":"BRIGHTNESS_UP"}"""

            lower.contains("volume") || lower.contains("sound") || lower.contains("awaaz") ->
                if (lower.contains("down") || lower.contains("decrease") ||
                    lower.contains("low") || lower.contains("reduce") || lower.contains("kam"))
                    """{"action":"VOLUME_DOWN"}"""
                else if (lower.contains("mute") || lower.contains("silent"))
                    """{"action":"VOLUME_MUTE"}"""
                else
                    """{"action":"VOLUME_UP"}"""

            lower.contains("screenshot") || lower.contains("screen shot") ||
            lower.contains("capture screen") || lower.contains("screenshoot") ->
                """{"action":"TAKE_SCREENSHOT"}"""

            lower.contains("whatsapp") || lower.contains("watsapp") || lower.contains("whatsup") -> {
                // Extract BOTH contact name AND message from user input
                // Patterns: "send [MSG] to [CONTACT] in whatsapp"
                //           "send [MSG] message to [CONTACT]"
                //           "whatsapp [CONTACT] [MSG]"
                //           "[CONTACT]-ku [MSG] message pannu"
                val userOrig = userInput.trim()
                val userLow = userOrig.lowercase()
                var contact = ""
                var message = ""

                // Pattern 1: "send [msg] (message) to [contact] (in whatsapp)"
                val p1 = Regex("send\\s+(.+?)\\s+(?:message\\s+)?to\\s+(.+?)(?:\\s+(?:in\\s+)?(?:whatsapp|watsapp))?$")
                // Pattern 2: "whatsapp [contact] [msg]"
                val p2 = Regex("(?:whatsapp|watsapp)\\s+(\\S+)\\s+(.+)")
                // Pattern 3: "[contact]-ku/ko [msg] message pannu"
                val p3 = Regex("(.+?)\\s*[-]?(?:ku|ko|ke|la|va)\\s+(.+?)\\s*(?:message\\s*)?(?:pannu|panu|bhejo|karo)?$")
                // Pattern 4: just contact, no message
                val p4 = Regex("(?:send\\s+(?:message\\s+)?to|whatsapp|message)\\s+(.+)")

                when {
                    p1.find(userLow)?.also { m ->
                        message = m.groupValues[1].trim()
                        contact = m.groupValues[2].trim().removePrefix("in ").removePrefix("on ")
                    } != null -> {}

                    p2.find(userLow)?.also { m ->
                        contact = m.groupValues[1].trim()
                        message = m.groupValues[2].trim()
                    } != null -> {}

                    p3.find(userLow)?.also { m ->
                        contact = m.groupValues[1].trim()
                        message = m.groupValues[2].trim()
                    } != null -> {}

                    p4.find(userLow)?.also { m ->
                        contact = m.groupValues[1].trim()
                    } != null -> {}
                }

                // Clean up contact (remove trailing app names)
                contact = contact
                    .removeSuffix(" whatsapp").removeSuffix(" watsapp")
                    .removeSuffix(" in").removeSuffix(" on").trim()

                if (contact.isNotBlank() && contact.length < 30) {
                    """{ "action":"OPEN_WHATSAPP","contact":"$contact","message":"$message"}"""
                } else {
                    """{ "action":"OPEN_APP","package":"com.whatsapp"}"""
                }
            }

            lower.contains("camera") || lower.contains("photo") || lower.contains("selfie") ->
                """{"action":"OPEN_APP","package":"com.sec.android.app.camera"}"""

            lower.contains("youtube") || lower.contains("video") ->
                """{"action":"OPEN_APP","package":"com.google.android.youtube"}"""

            lower.contains("maps") || lower.contains("navigation") || lower.contains("direction") ->
                """{"action":"OPEN_APP","package":"com.google.android.apps.maps"}"""

            lower.contains("instagram") ->
                """{"action":"OPEN_APP","package":"com.instagram.android"}"""

            lower.contains("settings") || lower.contains("setting") ->
                """{"action":"OPEN_APP","package":"com.android.settings"}"""

            lower.contains("home") || lower.contains("ghar") ->
                """{"action":"NAVIGATE_HOME"}"""

            lower.contains("back") || lower.contains("wapas") ->
                """{"action":"NAVIGATE_BACK"}"""

            lower.contains("recent") || lower.contains("multitask") ->
                """{"action":"NAVIGATE_RECENTS"}"""

            lower.contains("call") -> {
                // Extract contact from user input (not LLM response)
                val contactMatch = Regex("call\\s+(.+)").find(userInput.lowercase())
                val contact = contactMatch?.groupValues?.get(1)?.trim() ?: ""
                """{"action":"CALL","contact":"$contact"}"""
            }

            else -> "{}"
        }

        Log.i(TAG, "🔀 Keyword fallback result: $fallbackJson")
        return fallbackJson
    }

    fun unload() {
        Log.i(TAG, "🗑️ Freeing LLM memory...")
        if (isLoaded) {
            try { llamaHelper.abort() } catch (e: Exception) {
                Log.w(TAG, "abort warning: ${e.message}")
            }
        }
        isLoaded = false
        System.gc()
        Log.i(TAG, "✅ LLM memory freed")
    }
}
