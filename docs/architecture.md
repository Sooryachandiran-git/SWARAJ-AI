# Swaraj AI: Detailed Solution Architecture

## 1. Problem Statement
Budget smartphones in India (₹8k range) lack the RAM and connectivity to run modern AI assistants (Siri/Google) reliably. Users often rely on regional vernacular (Hinglish/Tanglish) which cloud models often misinterpret or latency makes unusable.

## 2. Infrastructure (The AWS Factory)
We treat the cloud as a manufacturing plant for "Intelligence Assets."

### A. Data Layer (Amazon Bedrock)
- **Teacher Model:** Nova Premier.
- **Role:** Generates synthetic intent mappings from raw speech transcripts. It understands the "Slang-to-JSON" conversion.
- **Output:** 10,000 unit instruction dataset.

### B. Training Layer (Amazon SageMaker)
- **Base Model:** Qwen-2.5-0.5B (or Gemma 3 1B).
- **Strategy:** Low-Rank Adaptation (LoRA).
- **Validation:** We validate that the model outputs **Zero-Shot JSON** even for names it hasn't seen before by teaching it the "Position of Entity" (Slot-Filling logic).

## 3. The On-Device Runtime (Sovereign AI)
The user's data never leaves the phone. 100% Offline.

### A. Serial Loading State Machine
Since budget phones have <4GB RAM, we cannot run STT and SLM concurrently.
1. **State 1:** Load **IndicConformer** -> Hear Speech -> Transcription String.
2. **State 2:** **Unload STT** -> Flush RAM -> Load **SLM**.
3. **State 3:** SLM parses Text -> Deterministic JSON.
4. **State 4:** **Unload SLM** -> Pass JSON to Action Executor.

### B. The Bridge (Accessibility Services)
Standard Android apps are siloed. Swaraj AI uses Accessibility Bridge to:
- Identify UI Nodes (Buttons, Toggles).
- Simulate Clicks (e.g., Click "Call" button in WhatsApp).
- Read Screen Context (e.g., "Is the flashlight currently ON?").

## 4. Key Performance Indicators (KPIs)
- **Model Size:** 374MB (SLM) + 200MB (STT).
- **RAM Usage:** ~550MB Peak.
- **Privacy:** 0 bytes uploaded to cloud during runtime.
- **Linguistic Coverage:** 22 Indian languages via IndicConformer + Synthetic Vernacular fine-tuning.
