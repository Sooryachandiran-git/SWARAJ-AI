package com.swarajai.cognitive

import android.content.Context

/**
 * DEPRECATED: Replaced by SwarajNativeSTTProvider (Android built-in offline STT)
 * Kept as an empty stub to avoid any lingering references during refactor.
 */
class SwarajWhisperProvider(
    private val context: Context,
    private val onOutput: ((String) -> Unit)? = null
) {
    fun load(onReady: (Boolean) -> Unit = {}) { onReady(false) }
    fun listen(onResult: (String) -> Unit) {}
    fun stop() {}
    fun unload() {}
}
