# Refined Swaraj AI Architecture: "The Hybrid Edge Intelligence"

This architecture uses the **"Fast Path / Slow Path"** pattern. It checks for a saved user macro first; if not found, it spins up the heavy AI model. This "Relay Race" strategy ensures maximum efficiency on budget hardware (<4GB RAM).

---

## 1. High-Level Flow (End-to-End)

1.  **Voice Perception**: Offline STT (**IndicConformer**) captures audio and transcribes it to text.
2.  **Fast-Path Check**: The system checks the **Local Macro DB (SQLite)**. If the text matches a saved trigger (e.g., "Safe"), it skips the AI and jumps to Step 4.
3.  **Slow-Path Reasoning**: If no macro exists, **Gemma 3 1B** is loaded into RAM. It parses the text into a JSON Action Schema.
4.  **Action Execution**: The **Action Router (Accessibility Service)** executes the physical UI clicks or system toggles.
5.  **Success Check & Feedback**: The app verifies the toggle state change, generates a text response, and speaks it via **IndicTTS**.

---

## 2. The On-Device "Relay Race" (RAM Management)

To maintain the **4GB RAM Wall**, models are swapped serially:

| Phase | Component | RAM Action | RAM Load (Est.) |
| :--- | :--- | :--- | :--- |
| **Phase 1** | IndicConformer | LOAD, Transcribe, UNLOAD | ~200 MB |
| **Phase 2** | Macro Matcher | Point-query SQLite Database | < 1 MB |
| **Phase 3** | Gemma 3 1B | LOAD (if no macro), Reason, UNLOAD | ~892 MB |
| **Phase 4** | Action Router | Physical execution via Accessibility | Minimal |
| **Phase 5** | IndicTTS | LOAD, Generate Speech, UNLOAD | ~150 MB |

---

## 3. The "Cloud Factory" (Pre-Hackathon R&D)

Before deployment, we use AWS to "distill" the intelligence:

*   **Data Generation**: Amazon Bedrock (**Nova Premier**) generates **20,000 unique samples** of Hinglish/Tanglish/Manglish commands, including CREATE_MACRO intents.
*   **Model Training**: Amazon SageMaker fine-tunes **Gemma 3 1B** on the generated dataset using **LoRA**.
*   **Optimization**: The model is quantized to **INT4 (GGUF)** for hyper-fast mobile CPU inference.

---

## 4. Key Innovation: The Intent-to-Action Schema

We use deterministic schemas to bridge the gap between human speech and Android system actions:

*   **Basic Control**: `{"action": "TOGGLE", "target": "wifi", "state": "ON"}`
*   **Contextual**: `{"action": "CALL", "target": "Suresh"}`
*   **Macro Setup**: 
    ```json
    {
      "action": "CREATE_MACRO",
      "trigger": "help",
      "steps": [
        {"action": "CALL", "target": "Son"},
        {"action": "TOGGLE", "target": "torch"}
      ]
    }
    ```

---

## 🛡️ Resilient Design: Hurdle Management

### 1. The "IO Latency" Heartbeat
To prevent the app from appearing "frozen" during a serial model swap (which can take 1-3s on slow storage), Swaraj AI uses:
*   **Haptic Tick**: A physical pulse (vibration) the moment the SLM starts loading.
*   **Shimmer Overlay**: A subtle visual feedback loop to bridge the "Dead Air" while data moves from storage to RAM.

### 2. Semantic Anchoring (Accessibility)
Traditional accessibility tools fail if a UI ID changes. Swaraj AI uses **Semantic Fallback**:
*   If a specific `resource-id` is not found, the system scans the screen hierarchy for nodes matching the **Intent Keyword** (e.g., searching for text "Send" or content-description "Submit").
*   This makes the assistant "Vision-Aware" and immune to app layout updates.

### 3. Model Fallback (Plan A vs Plan B)
The engine dynamically detects hardware capabilities at runtime:
*   **Plan A**: Gemma-3-1B (Full Reasoning) for devices with >3GB free RAM.
*   **Plan B**: Qwen-2.5-0.5B (Speed Optimized) for hyper-budget devices to prevent OS termination (LMK).
