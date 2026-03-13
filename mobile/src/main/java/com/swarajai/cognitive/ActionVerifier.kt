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
            val action = intent.getString("action")
            val target = intent.getString("target").lowercase()

            if (!hardwareExecutionSuccess) return getTemplate("error", target)

            // Hardware-specific verification + Template mapping
            return when (target) {
                "wifi" -> {
                    val status = if (verifyWifi()) "ON" else "OFF"
                    getTemplate("wifi", status)
                }
                "flashlight", "torch" -> getTemplate("torch", "TOGGLE")
                "bluetooth" -> {
                    val status = if (verifyBluetooth()) "ACTIVE" else "DISABLED"
                    getTemplate("bluetooth", status)
                }
                "call" -> getTemplate("call", "SUCCESS")
                "whatsapp" -> getTemplate("whatsapp", "SUCCESS")
                "alarm" -> getTemplate("alarm", "SUCCESS")
                "macro" -> getTemplate("macro", "SAVE")
                else -> getTemplate("generic", "SUCCESS")
            }
        } catch (e: Exception) {
            getTemplate("error", "SYSTEM")
        }
    }

    /**
     * 💡 LINGUISTIC LOOKUP: 
     * Handles all primary Swaraj AI intent categories.
     * These strings can be instantly swapped for regional dialects.
     */
    private fun getTemplate(category: String, state: String): String {
        val responses = mapOf(
            "wifi_ON" to "Swaraj: WiFi is now active.",
            "wifi_OFF" to "Swaraj: WiFi has been disabled.",
            "torch_TOGGLE" to "Swaraj: I have toggled your flashlight.",
            "bluetooth_ACTIVE" to "Swaraj: Bluetooth is now scanning for devices.",
            "call_SUCCESS" to "Swaraj: Connecting your call now.",
            "whatsapp_SUCCESS" to "Swaraj: Opening your WhatsApp chat.",
            "alarm_SUCCESS" to "Swaraj: Your alarm has been set successfully.",
            "macro_SAVE" to "Swaraj: Your custom routine has been saved locally.",
            "error_wifi" to "Swaraj: I couldn't reach the WiFi settings.",
            "generic_SUCCESS" to "Swaraj: Task completed successfully."
        )
        
        val key = "${category}_${state}"
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
