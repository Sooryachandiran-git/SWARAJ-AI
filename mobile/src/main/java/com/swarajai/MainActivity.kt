package com.swarajai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.swarajai.cognitive.SerialLoadingEngine
import com.swarajai.cognitive.SwarajAccessibilityService

/**
 * Swaraj AI: Main Control Center
 * 
 * Provides the UI for enabling permissions and triggering the AI interaction.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var engine: SerialLoadingEngine
    private lateinit var statusText: TextView
    private lateinit var accessibilityStatusText: TextView
    private lateinit var micContainer: View
    private lateinit var wakeWordToggle: SwitchCompat
    private lateinit var cognitiveConsole: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        accessibilityStatusText = findViewById(R.id.accessibility_status)
        micContainer = findViewById(R.id.mic_container)
        cognitiveConsole = findViewById(R.id.cognitive_console)

        engine = SerialLoadingEngine(
            this,
            onOutput = { output ->
                runOnUiThread {
                    cognitiveConsole.append("\n$output")
                    val scroll = cognitiveConsole.parent as? android.widget.ScrollView
                    scroll?.post { scroll.fullScroll(View.FOCUS_DOWN) }
                }
            },
            onStateChanged = { state ->
                runOnUiThread {
                    updateUiForState(state)
                }
            }
        )

        // 🔥 Pre-warm the Vosk STT model immediately on app open.
        // This ensures first tap is instant and we detect errors early.
        engine.preWarm()

        findViewById<View>(R.id.mic_button).setOnClickListener {
            handleVoiceTrigger()
        }

        handleIntent(intent)

        findViewById<View>(R.id.setup_button).setOnClickListener {
            promptAccessibility()
        }

        wakeWordToggle = findViewById(R.id.wake_word_toggle)
        wakeWordToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!isAccessibilityServiceEnabled()) {
                    wakeWordToggle.isChecked = false
                    promptAccessibility()
                } else {
                    SwarajAccessibilityService.instance?.startWakeWordDetection()
                    Toast.makeText(this, "Swaraj Voice Wake Active", Toast.LENGTH_SHORT).show()
                }
            } else {
                SwarajAccessibilityService.instance?.stopWakeWordDetection()
            }
        }
        
        updateSystemStatus()
        checkAndRequestPermissions()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("VOICE_TRIGGER", false) == true) {
            handleVoiceTrigger()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_CONTACTS)
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsNeeded.toTypedArray())
        }

        // Special check for Manage External Storage removed. 
        // Models are now bundled in assets for a smoother user experience.
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Permissions required for AI to function", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleVoiceTrigger() {
        if (!isAccessibilityServiceEnabled()) {
            promptAccessibility()
            return
        }

        // PERMISSION CHECK: Must have Mic access to use Whisper
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
            checkAndRequestPermissions()
            return
        }

        // Pulse Animation for "Listening" state
        micContainer.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).withEndAction {
            micContainer.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
        }.start()

        statusText.text = "LISTENING..."
        statusText.setTextColor(ContextCompat.getColor(this, R.color.swaraj_green))
        
        engine.startInteraction()
    }

    private fun updateSystemStatus() {
        if (isAccessibilityServiceEnabled()) {
            accessibilityStatusText.text = "✅ Swaraj Service Active"
            accessibilityStatusText.setTextColor(ContextCompat.getColor(this, R.color.swaraj_green))
        } else {
            accessibilityStatusText.text = "⚠️ Accessibility Service Disabled"
            accessibilityStatusText.setTextColor(ContextCompat.getColor(this, R.color.swaraj_saffron))
        }
    }

    override fun onResume() {
        super.onResume()
        updateSystemStatus()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return SwarajAccessibilityService.instance != null
    }

    private fun updateUiForState(state: SerialLoadingEngine.EngineState) {
        when (state) {
            SerialLoadingEngine.EngineState.IDLE -> {
                statusText.text = "TAP TO SPEAK"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.swaraj_white))
            }
            SerialLoadingEngine.EngineState.LISTENING -> {
                statusText.text = "LISTENING..."
                statusText.setTextColor(ContextCompat.getColor(this, R.color.swaraj_green))
            }
            SerialLoadingEngine.EngineState.THINKING -> {
                statusText.text = "THINKING..."
                statusText.setTextColor(ContextCompat.getColor(this, R.color.swaraj_saffron))
            }
            SerialLoadingEngine.EngineState.EXECUTING -> {
                statusText.text = "EXECUTING..."
                statusText.setTextColor(ContextCompat.getColor(this, R.color.swaraj_green))
            }
            SerialLoadingEngine.EngineState.SPEAKING -> {
                statusText.text = "SPEAKING..."
                statusText.setTextColor(ContextCompat.getColor(this, R.color.swaraj_white))
            }
        }
    }

    private fun promptAccessibility() {
        Toast.makeText(this, "Enable Swaraj AI in Settings", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
