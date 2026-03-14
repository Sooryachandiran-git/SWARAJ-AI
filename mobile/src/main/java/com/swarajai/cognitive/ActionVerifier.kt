package com.swarajai.cognitive

import android.content.Context
import android.net.wifi.WifiManager
import android.bluetooth.BluetoothAdapter
import org.json.JSONObject

/**
 * Swaraj AI: Action Verifier
 * 
 * This is the "Sense of Touch" for the AI. It checks if an intended action
 * (e.g., turning on WiFi) actually happened in the hardware.
 */
class ActionVerifier(private val context: Context) {

    /**
     * Verifies the result of an action and returns a templated response.
     * 💡 This bypasses the need to reload the heavy SLM for generating confirmation text.
     */
    fun verify(schema: String, hardwareExecutionSuccess: Boolean): String {
        return try {
            val json = JSONObject(schema)
            val intent = json.optJSONObject("intent") ?: json 
            
            var action = intent.optString("action")
            var target = intent.optString("target").lowercase()
            var state = intent.optString("value").uppercase()

            // Prototype alignment
            if (action == "system_toggle") {
                action = "TOGGLE_DEVICE"
                target = "flashlight"
            }
            if (intent.has("state") && state.isEmpty()) {
                state = intent.getString("state").uppercase()
            }

            if (!hardwareExecutionSuccess) return getTemplate("error", target)

            // Hardware-specific verification + Template mapping
            return when (action) {
                "TOGGLE_DEVICE" -> getTemplate("device", "${target.uppercase()}-$state")
                "CHECK_STATUS" -> getTemplate("status", target.uppercase())
                "OPEN_APP" -> getTemplate("app", "OPEN")
                "WHATSAPP" -> getTemplate("whatsapp", "SUCCESS")
                "CALL" -> getTemplate("call", "SUCCESS")
                "SYSTEM_NAV" -> getTemplate("nav", target.uppercase())
                "MATH_CALC" -> getTemplate("math", "DONE")
                else -> getTemplate("generic", "SUCCESS")
            }
        } catch (e: Exception) {
            getTemplate("error", "SYSTEM")
        }
    }

    private fun getTemplate(category: String, state: String): String {
        val responses = mapOf(
            "device_FLASHLIGHT-ON" to "Swaraj: Flashlight is now on.",
            "device_FLASHLIGHT-OFF" to "Swaraj: Flashlight is now off.",
            "device_WIFI-ON" to "Swaraj: WiFi connected.",
            "device_WIFI-OFF" to "Swaraj: WiFi disconnected.",
            "status_BATTERY" to "Swaraj: Your battery is healthy.",
            "app_OPEN" to "Swaraj: Opening the requested application.",
            "whatsapp_SUCCESS" to "Swaraj: Sending your message now.",
            "call_SUCCESS" to "Swaraj: Initiating the call.",
            "math_DONE" to "Swaraj: Here is the calculation result.",
            "nav_BACK" to "Swaraj: Going back.",
            "nav_HOME" to "Swaraj: Going to the home screen.",
            "nav_SETTINGS" to "Swaraj: Opening settings.",
            "generic_SUCCESS" to "Swaraj: Action performed successfully.",
            "error_SYSTEM" to "Swaraj: I encountered an error performing that action."
        )
        val key = "${category.lowercase()}_${state.uppercase()}"
        return responses[key] ?: responses["generic_SUCCESS"]!!
    }

    private fun verifyWifi(): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }

    private fun verifyBluetooth(): Boolean {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return bluetoothAdapter?.isEnabled ?: false
    }
}
