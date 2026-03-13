# 🏆 Swaraj AI: Project Milestone & Architecture Deep-Dive

This document serves as the comprehensive "Master Plan" and record of the engineering breakthroughs achieved for **Swaraj AI**. It details the transition from a simple prototype to a resilient, "Hybrid Edge Intelligence" system designed for the **DAKSH Final Round**.

---

## 📅 Project Phase: Pre-Hackathon Foundation
**Objective:** Build a modular, GitHub-ready repository that overcomes the "4GB RAM Wall" and provides truly "Sovereign" (offline) AI for the Indian demographic.

---

## 1. The Core Philosophy: "Relay Race" Memory Management
Traditional mobile AI fails on budget hardware because loading **STT (Speech-to-Text)** and **SLM (Small Language Model)** simultaneously exceeds RAM limits (often >2.5GB).

### The Breakthrough: Serial Loading State Machine
We implemented a "Stage-Gate" lifecycle in the `SerialLoadingEngine.kt`:
- **Phase 1: Perception** (IndicConformer LOADED -> Transcription -> UNLOADED).
- **Phase 2: Reasoning** (Gemma 3 1B / Qwen 0.5B LOADED -> Intent JSON -> UNLOADED).
- **Phase 3: Feedback** (IndicTTS LOADED -> Voice Feedback -> UNLOADED).

**Result:** At no point does the app's active RAM footprint exceed the critical 1.2GB threshold, ensuring stability on devices like the ₹8,000 Android segment.

---

## 2. Hybrid Intelligence: System 1 (Fast Path) vs. System 2 (Slow Path)
Inspired by Daniel Kahneman's model of human thinking, we split the AI's "brain" into two speeds:

### System 1: The "Fast Path" (Reflex Memory)
- **Component:** Local SQLite / SharedPreferences via `MacroManager.kt`.
- **Latency:** < 10ms.
- **Logic:** Performs a high-speed search for custom user **Macros** (e.g., *"Swaraj, Danger!"*).
- **Advantage:** Bypasses the 900MB LLM entirely for 80% of routine tasks, saving massive battery and compute cycles.

### System 2: The "Slow Path" (Reasoning Engine)
- **Component:** Fine-tuned Gemma 3 1B quantized for Mobile.
- **Latency:** ~1s.
- **Logic:** Only wakes up when it needs to "think" about a new or complex command.
- **Capability:** Extracts irregular times (5:35 PM), specific Indian names, and complex regional intent.

---

## 3. The "Cloud Factory" Distillation Strategy
To make a 1.1 billion parameter model smart enough for rural India, we used a **Teacher-Student** training pipeline:

- **The Teacher (Amazon Bedrock - Nova Premier):** We built a synthesis script (`generate_data.py`) to manufacture **20,000 samples** of high-fidelity vernacular data.
- **Regional Personas:** The data isn't generic; it covers 7 distinct personas (Tamil farmer, Delhi student, Kochi elderly, Bangalore gig-worker, etc.).
- **Intent Schemas:** We standardized three deterministic schemas: `Basic Control`, `Contextual Actions`, and `CREATE_MACRO`.

---

## 4. Resilience Engineering: Handling Hurdles
We proactively identified "Loophole" risks and built "Shields" against them:

1.  **IO Latency Heartbeat:** Added **Haptic Pulses** (physical vibration) during the 1-2s model loading phase to prevent the app from feeling "frozen."
2.  **Semantic Anchoring:** In the Action Router, we moved beyond fragile UI IDs. If a button's ID changes (e.g., WhatsApp update), the system falls back to **Text-Based Finding** (searching for "Send" or its regional equivalent).
3.  **The "Plan B" Fallback:** The system checks device RAM at runtime. If it's a hyper-budget phone, it swaps the heavy **Gemma 1B** for the ultra-light **Qwen 0.5B**.
4.  **Foreground Shield:** Wrapped the engine in an Android **Foreground Service** to prevent the Low Memory Killer (LMK) from terminating the AI during inference.

---

## 5. Technical Stack Summary
- **Cloud Foundation:** Amazon Bedrock (Nova Premier), Amazon SageMaker (LoRA Fine-tuning), us-east-1 region.
- **On-Device Runtime:** Kotlin, `llama.cpp` (GGUF), ONNX Runtime, Android Accessibility Services.
- **Linguistic Scope:** 7 major Indian regions with focus on South Indian (Malayalam/Kannada) and North Indian (Hindi/Punjabi) vernaculars.

---

## 🏁 The Pitch Vision
*"Swaraj AI isn't an app; it's a Sovereign OS Layer. It turns a ₹8,000 phone into a companion that learns the user's personal language and routines locally, ensuring that the 'Next Billion' users aren't left behind by the AI revolution because of their language, their privacy, or their hardware."*
