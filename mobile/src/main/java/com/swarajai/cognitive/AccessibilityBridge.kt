package com.swarajai.cognitive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

/**
 * Swaraj AI: Accessibility Bridge
 * 
 * This class acts as the "Hands" of the AI. It interprets the JSON intents
 * and executes real Android system actions.
 */
class AccessibilityBridge(private val context: Context) {

    private val TAG = "SwarajHands"

    fun execute(actionSchema: String): Boolean {
        return try {
            val json = JSONObject(actionSchema)
            val intentObj = json.optJSONObject("intent") ?: json
            
            // Flexible keys to support multiple model variants
            var action = intentObj.optString("action")
            var target = intentObj.optString("target")
            var value = intentObj.optString("value")

            // Support prototype format: {'action': 'system_toggle', 'state': 'ON'}
            if (action == "system_toggle") {
                action = "TOGGLE_DEVICE"
                target = "FLASHLIGHT" // Default for prototype demo
            }
            if (intentObj.has("state") && value.isEmpty()) {
                value = intentObj.getString("state")
            }

            Log.i(TAG, "🎬 Executing: $action on $target with $value")

            when (action) {
                "TOGGLE_DEVICE" -> handleDeviceToggle(target, value)
                "OPEN_APP" -> openApp(target)
                "WHATSAPP" -> sendWhatsApp(target, value)
                "CALL" -> makeCall(target)
                "SYSTEM_NAV" -> handleSystemNav(target)
                "MATH_CALC" -> true // Handled by UI/Speech logic
                else -> {
                    Log.w(TAG, "⚠️ Unknown action: $action")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Execution Error: ${e.message}")
            false
        }
    }

    private fun handleDeviceToggle(target: String, value: String): Boolean {
        return when (target.uppercase()) {
            "FLASHLIGHT" -> {
                try {
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                    val cameraId = cameraManager.cameraIdList[0]
                    cameraManager.setTorchMode(cameraId, value.uppercase() == "ON")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "🔦 Flashlight Error: ${e.message}")
                    false
                }
            }
            "WIFI" -> {
                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
            "BT", "BLUETOOTH" -> {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }
            else -> false
        }
    }

    private fun openApp(appName: String): Boolean {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(mainIntent, 0)
            
            // Fuzzy search for the best matching app name
            val pkgName = apps.find { 
                it.loadLabel(pm).toString().contains(appName, ignoreCase = true) 
            }?.activityInfo?.packageName

            if (pkgName != null) {
                val launchIntent = pm.getLaunchIntentForPackage(pkgName)
                context.startActivity(launchIntent)
                true
            } else {
                Log.w(TAG, "🚫 App not found: $appName")
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun sendWhatsApp(contact: String, message: String): Boolean {
        return try {
            val resolvedNumber = ContactResolver.resolveContact(context, contact)
            val uri = if (resolvedNumber != null) {
                Uri.parse("https://api.whatsapp.com/send?phone=$resolvedNumber&text=${Uri.encode(message)}")
            } else {
                Uri.parse("whatsapp://send?text=${Uri.encode(message)}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun makeCall(contact: String): Boolean {
        return try {
            val resolvedNumber = ContactResolver.resolveContact(context, contact)
            val numberToCall = resolvedNumber ?: contact
            
            val intent = Intent(Intent.ACTION_DIAL)
            if (numberToCall.isNotBlank()) {
                intent.data = Uri.parse("tel:$numberToCall")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun handleSystemNav(target: String): Boolean {
        val service = SwarajAccessibilityService.instance
        return if (service != null) {
            service.performGlobalAction(target)
        } else {
            Log.e(TAG, "🚨 Accessibility Service not active - Navigation failed")
            false
        }
    }
}
