package com.swarajai.cognitive

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import org.json.JSONObject

/**
 * Swaraj AI: Accessibility Bridge
 *
 * The "Hands" of the AI — interprets Qwen's JSON output and
 * executes real Android system actions.
 *
 * Handles ALL action names from both the Qwen LLM output and FastPathEngine.
 */
class AccessibilityBridge(private val context: Context) {

    private val TAG = "SwarajHands"

    fun execute(actionSchema: String): Boolean {
        return try {
            val json = JSONObject(actionSchema)
            val action = json.optString("action").uppercase().trim()

            Log.i(TAG, "🎬 Executing action: $action | full=$actionSchema")

            when (action) {

                // ── FLASHLIGHT ─────────────────────────────────────────────
                "FLASHLIGHT_ON"  -> setFlashlight(true)
                "FLASHLIGHT_OFF" -> setFlashlight(false)

                // Legacy format support
                "TOGGLE_DEVICE" -> {
                    val target = json.optString("target").uppercase()
                    val value  = json.optString("value", json.optString("state", "ON")).uppercase()
                    when (target) {
                        "FLASHLIGHT" -> setFlashlight(value == "ON")
                        else -> false
                    }
                }

                // ── BRIGHTNESS ─────────────────────────────────────────────
                "BRIGHTNESS_UP"       -> adjustBrightness(increase = true)
                "BRIGHTNESS_DOWN"     -> adjustBrightness(increase = false)
                "BRIGHTNESS_MAX"      -> setBrightness(255)
                "BRIGHTNESS_MIN"      -> setBrightness(10)
                "BRIGHTNESS_INCREASE" -> adjustBrightness(increase = true)
                "BRIGHTNESS_DECREASE" -> adjustBrightness(increase = false)

                // ── VOLUME ─────────────────────────────────────────────────
                "VOLUME_UP"   -> adjustVolume(AudioManager.ADJUST_RAISE)
                "VOLUME_DOWN" -> adjustVolume(AudioManager.ADJUST_LOWER)
                "VOLUME_MUTE" -> adjustVolume(AudioManager.ADJUST_MUTE)

                // ── OPEN APP ───────────────────────────────────────────────
                "OPEN_APP" -> {
                    // Qwen returns package name in "package" field
                    val pkg  = json.optString("package")
                    val name = json.optString("name", json.optString("target"))
                    when {
                        pkg.isNotBlank()  -> openByPackage(pkg)
                        name.isNotBlank() -> openByName(name)
                        else -> false
                    }
                }

                // ── CALLS ──────────────────────────────────────────────────
                "CALL" -> {
                    val contact = json.optString("contact", json.optString("target"))
                    makeCall(contact)
                }

                // ── WHATSAPP ───────────────────────────────────────────────
                "WHATSAPP", "OPEN_WHATSAPP", "SEND_WHATSAPP" -> {
                    val contact = json.optString("contact", json.optString("target"))
                    val message = json.optString("message", json.optString("value", ""))
                    openWhatsApp(contact, message)
                }

                // ── NAVIGATION ─────────────────────────────────────────────
                "NAVIGATE_HOME", "HOME" -> performNav("HOME")
                "NAVIGATE_BACK", "BACK" -> performNav("BACK")
                "NAVIGATE_RECENTS", "RECENTS" -> performNav("RECENTS")

                // Legacy nav format
                "SYSTEM_NAV" -> {
                    val target = json.optString("target")
                    performNav(target)
                }

                // ── SCREENSHOT ─────────────────────────────────────────────
                "TAKE_SCREENSHOT", "SCREENSHOT" -> performNav("SCREENSHOT")

                // ── SETTINGS ───────────────────────────────────────────────
                "OPEN_WIFI"      -> openSettings(Settings.ACTION_WIFI_SETTINGS)
                "OPEN_BLUETOOTH" -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
                "OPEN_SETTINGS"  -> openSettings(Settings.ACTION_SETTINGS)
                "LOCK_SCREEN"    -> lockScreen()
                "OPEN_NOTIFICATIONS" -> performNav("NOTIFICATIONS")

                else -> {
                    Log.w(TAG, "⚠️ Unknown action: $action")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Execution Error on schema=$actionSchema : ${e.message}")
            false
        }
    }

    // ── FLASHLIGHT ────────────────────────────────────────────────────────
    private fun setFlashlight(on: Boolean): Boolean {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList[0], on)
            Log.i(TAG, "🔦 Flashlight -> ${if (on) "ON" else "OFF"}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "🔦 Flashlight error: ${e.message}")
            false
        }
    }

    // ── BRIGHTNESS ────────────────────────────────────────────────────────
    private fun adjustBrightness(increase: Boolean): Boolean {
        return try {
            // Check if we have WRITE_SETTINGS permission
            if (!Settings.System.canWrite(context)) {
                // Open settings page to grant permission
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return false
            }
            val current = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            val step = 40 // 16% of 255 per command
            val newVal = if (increase) {
                (current + step).coerceAtMost(255)
            } else {
                (current - step).coerceAtLeast(10)
            }
            setBrightness(newVal)
        } catch (e: Exception) {
            Log.e(TAG, "💡 Brightness error: ${e.message}")
            false
        }
    }

    private fun setBrightness(value: Int): Boolean {
        return try {
            if (!Settings.System.canWrite(context)) return false
            // Disable auto brightness first
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                value.coerceIn(10, 255)
            )
            Log.i(TAG, "💡 Brightness set to $value")
            true
        } catch (e: Exception) {
            Log.e(TAG, "💡 setBrightness error: ${e.message}")
            false
        }
    }

    // ── VOLUME ────────────────────────────────────────────────────────────
    private fun adjustVolume(direction: Int): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
            Log.i(TAG, "🔊 Volume adjusted: $direction")
            true
        } catch (e: Exception) {
            Log.e(TAG, "🔊 Volume error: ${e.message}")
            false
        }
    }

    // ── OPEN APP ──────────────────────────────────────────────────────────
    private fun openByPackage(packageName: String): Boolean {
        // Special cases: packages that need intent actions instead of direct launch
        when (packageName) {
            "com.android.camera2",
            "com.sec.android.app.camera",
            "camera" -> return openCamera()
        }

        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "📱 Opened by package: $packageName")
                true
            } else {
                // Package not found — try name-based fuzzy search
                Log.w(TAG, "📦 Package not installed: $packageName — trying name search")
                val appName = packageName.substringAfterLast(".")
                openByName(appName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "📱 openByPackage error: ${e.message}")
            false
        }
    }

    private fun openCamera(): Boolean {
        // Use standard camera intent — works on ALL Android devices (Samsung, Xiaomi, etc.)
        val cameraIntents = listOf(
            Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
            // Samsung-specific fallback
            context.packageManager.getLaunchIntentForPackage("com.sec.android.app.camera"),
            // Xiaomi fallback
            context.packageManager.getLaunchIntentForPackage("com.android.camera"),
        )

        for (intent in cameraIntents) {
            try {
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    Log.i(TAG, "📸 Camera opened")
                    return true
                }
            } catch (e: Exception) {
                continue // Try next intent
            }
        }

        Log.e(TAG, "📸 No camera app found")
        return false
    }

    private fun openByName(appName: String): Boolean {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
            val match = apps.find {
                it.loadLabel(pm).toString().contains(appName, ignoreCase = true)
            }?.activityInfo?.packageName

            if (match != null) {
                val launchIntent = pm.getLaunchIntentForPackage(match)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Log.i(TAG, "📱 Opened by name: $appName -> $match")
                true
            } else {
                Log.w(TAG, "🚫 App not found: $appName")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "📱 openByName error: ${e.message}")
            false
        }
    }

    // ── CALL ──────────────────────────────────────────────────────────────
    private fun makeCall(contact: String): Boolean {
        return try {
            val number = ContactResolver.resolveContact(context, contact) ?: contact
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "📞 Call error: ${e.message}")
            false
        }
    }

    private fun openWhatsApp(contact: String, message: String): Boolean {
        return try {
            val number = ContactResolver.resolveContact(context, contact)
            val encodedMsg = Uri.encode(message)

            val uri = when {
                // Best case: resolved phone number → direct chat + pre-filled message
                number != null ->
                    Uri.parse("https://api.whatsapp.com/send?phone=$number&text=$encodedMsg")
                // Contact name known but no number → open WhatsApp and search by name
                contact.isNotBlank() ->
                    Uri.parse("https://api.whatsapp.com/send?phone=&text=$encodedMsg")
                // No contact at all → just open WhatsApp
                else ->
                    Uri.parse("whatsapp://")
            }

            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.i(TAG, "💬 WhatsApp opened: contact=$contact msg=$message number=$number")
            true
        } catch (e: Exception) {
            Log.e(TAG, "💬 WhatsApp error: ${e.message}")
            false
        }
    }

    // ── NAVIGATION ────────────────────────────────────────────────────────
    private fun performNav(target: String): Boolean {
        val service = SwarajAccessibilityService.instance
        return if (service != null) {
            service.performGlobalAction(target)
        } else {
            Log.e(TAG, "🚨 Accessibility Service not active — Navigation failed")
            false
        }
    }

    // ── SETTINGS ─────────────────────────────────────────────────────────
    private fun openSettings(action: String): Boolean {
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) { false }
    }

    private fun lockScreen(): Boolean {
        val service = SwarajAccessibilityService.instance
        return service?.performGlobalAction("LOCK_SCREEN") ?: false
    }
}
