package com.swarajai.cognitive

import android.util.Log

/**
 * Swaraj AI: Fast Path Engine (0ms Latency)
 *
 * To avoid the 25-second LLM load time for basic commands, this engine uses
 * ultra-fast keyword matching to resolve intent instantly.
 * If this fails, it falls back to the heavy Qwen LLM.
 */
object FastPathEngine {
    private const val TAG = "FastPath"

    fun tryResolve(text: String): String? {
        val lower = text.lowercase().trim()

        val json = when {
            // ── FLASHLIGHT ──
            lower.contains("flashlight") || lower.contains("torch") || 
            lower.contains("pannu") && (lower.contains("torch") || lower.contains("light")) ->
                if (lower.contains("off") || lower.contains("band") || lower.contains("disable"))
                    """{"action":"FLASHLIGHT_OFF"}"""
                else
                    """{"action":"FLASHLIGHT_ON"}"""

            // ── BRIGHTNESS ──
            lower.contains("brightness") || lower.contains("bright") ->
                if (lower.contains("down") || lower.contains("decrease") ||
                    lower.contains("low") || lower.contains("reduce") || lower.contains("dim") ||
                    lower.contains("kam") || lower.contains("kurai"))
                    """{"action":"BRIGHTNESS_DOWN"}"""
                else
                    """{"action":"BRIGHTNESS_UP"}"""

            // ── VOLUME ──
            lower.contains("volume") || lower.contains("sound") || lower.contains("awaaz") ->
                if (lower.contains("down") || lower.contains("decrease") ||
                    lower.contains("low") || lower.contains("reduce") || lower.contains("kam"))
                    """{"action":"VOLUME_DOWN"}"""
                else if (lower.contains("mute") || lower.contains("silent"))
                    """{"action":"VOLUME_MUTE"}"""
                else
                    """{"action":"VOLUME_UP"}"""

            // ── SCREENSHOT ──
            lower.contains("screenshot") || lower.contains("screen shot") ||
            lower.contains("capture screen") || lower.contains("screenshoot") ->
                """{"action":"TAKE_SCREENSHOT"}"""

            // ── WHATSAPP ──
            lower.contains("whatsapp") || lower.contains("watsapp") || lower.contains("whatsup") -> {
                // Strip out standard words to simplify the parsing exactly
                val cleanedText = lower
                    .replace(" in whatsapp", "").replace(" on whatsapp", "")
                    .replace(" whatsapp", "").replace(" watsapp", "")
                    .replace("whatsapp ", "").replace("watsapp ", "")
                    .trim()

                var contact = ""
                var message = ""

                // Pattern 1: send <msg> to <contact>
                val p1 = Regex("^send\\s+(?:message\\s+)?(.+?)\\s+(?:to|ko)\\s+(.+)$")
                // Pattern 2: message <contact> <msg>
                val p2 = Regex("^message\\s+(.+?)\\s+(.+)$")
                // Pattern 3 Tanglish: <contact> ku <msg> message pannu
                val p3 = Regex("^(.+?)\\s*[-]?(?:ku|ko|ke|la|va)\\s+(.+?)\\s*(?:message\\s*)?(?:pannu|panu|bhejo|karo)$")
                // Pattern 4 simple: send message to <contact>
                val p4 = Regex("^send\\s+(?:message|msg)\\s+(?:to\\s+)?(.+)$")
                // Pattern 5 simple 2: message / send <contact>
                val p5 = Regex("^(?:message|send)\\s+(.+)$")

                when {
                    p1.find(cleanedText) != null -> {
                        val m = p1.find(cleanedText)!!
                        message = m.groupValues[1].trim()
                        contact = m.groupValues[2].trim()
                        if (message == "message" || message == "msg") message = ""
                    }
                    p3.find(cleanedText) != null -> {
                        val m = p3.find(cleanedText)!!
                        contact = m.groupValues[1].trim()
                        message = m.groupValues[2].trim()
                    }
                    p4.find(cleanedText) != null -> {
                        contact = p4.find(cleanedText)!!.groupValues[1].trim()
                    }
                    p2.find(cleanedText) != null -> {
                        val m = p2.find(cleanedText)!!
                        contact = m.groupValues[1].trim()
                        message = m.groupValues[2].trim()
                        if (message == "message" || message == "msg") message = "" // handled by p4 mostly
                    }
                    p5.find(cleanedText) != null -> {
                        contact = p5.find(cleanedText)!!.groupValues[1].trim()
                    }
                    else -> {
                        // Just simple contact name if no patterns match (e.g. "amma")
                        if (cleanedText.isNotBlank() && !cleanedText.contains("open") && cleanedText.split(" ").size <= 3) {
                            contact = cleanedText.removePrefix("to ").trim()
                        }
                    }
                }

                // Clean the contact string to be strictly text
                contact = contact.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()

                if (contact.isNotBlank() && contact.length < 30) {
                    """{"action":"OPEN_WHATSAPP","contact":"$contact","message":"$message"}"""
                } else {
                    """{"action":"OPEN_APP","package":"com.whatsapp"}"""
                }
            }

            // ── CAMERA ──
            lower.contains("camera") || lower.contains("photo") || lower.contains("selfie") ->
                """{"action":"OPEN_APP","package":"com.sec.android.app.camera"}"""

            // ── OTHER APPS ──
            lower.contains("youtube") || lower.contains("video") ->
                """{"action":"OPEN_APP","package":"com.google.android.youtube"}"""
            lower.contains("maps") || lower.contains("navigation") || lower.contains("direction") ->
                """{"action":"OPEN_APP","package":"com.google.android.apps.maps"}"""
            lower.contains("instagram") ->
                """{"action":"OPEN_APP","package":"com.instagram.android"}"""
            lower.contains("settings") || lower.contains("setting") ->
                """{"action":"OPEN_APP","package":"com.android.settings"}"""

            // ── NAVIGATION ──
            lower.contains("home") || lower.contains("ghar") ->
                """{"action":"NAVIGATE_HOME"}"""
            lower.contains("back") || lower.contains("wapas") ->
                """{"action":"NAVIGATE_BACK"}"""
            lower.contains("recent") || lower.contains("multitask") ->
                """{"action":"NAVIGATE_RECENTS"}"""

            // ── CALLS ──
            lower.contains("call") -> {
                val contactMatch = Regex("call\\s+(.+)").find(lower)
                val contact = contactMatch?.groupValues?.get(1)?.trim() ?: ""
                """{"action":"CALL","contact":"$contact"}"""
            }

            else -> null // Signal to fallback to the heavy LLM
        }

        if (json != null) {
            Log.i(TAG, "⚡ FastPath matched instantly: $json")
        }
        return json
    }
}
