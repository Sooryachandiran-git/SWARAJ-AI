package com.swarajai.cognitive

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Swaraj AI: Macro Logic (System 1 - Fast Path)
 * Handles custom regional triggers (e.g. "Danger", "Ghar")
 * and maps them to multi-step sequences stored in local persistent storage.
 */
class MacroManager(context: Context) {
    private val prefs = context.getSharedPreferences("SwarajMacros", Context.MODE_PRIVATE)

    /**
     * Checks if a macro trigger already exists.
     */
    fun exists(trigger: String): Boolean {
        return prefs.contains(trigger.lowercase().trim())
    }

    /**
     * Stores a new macro sequence.
     * @return Boolean - true if saved, false if already exists.
     */
    fun saveMacro(trigger: String, stepsJson: String): Boolean {
        val key = trigger.lowercase().trim()
        if (exists(key)) return false
        
        prefs.edit().putString(key, stepsJson).apply()
        return true
    }

    /**
     * Checks if a user's command is a registered macro (Fuzzy Matching).
     * Bypasses SLM reasoning for 5ms latency execution.
     */
    fun checkFastPath(transcription: String): String? {
        val input = transcription.lowercase().trim()
        val allMacros = prefs.all
        
        // Logical Lookup: If input contains the trigger or vice-versa
        // This handles "Ghar" vs "Swaraj, Ghar" logic.
        for ((trigger, actions) in allMacros) {
            if (input.contains(trigger) || trigger.contains(input)) {
                return actions as String
            }
        }
        return null
    }

    /**
     * Deletes a macro.
     */
    fun deleteMacro(trigger: String) {
        prefs.edit().remove(trigger.lowercase()).apply()
    }
}
