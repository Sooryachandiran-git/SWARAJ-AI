# 🇮🇳 Swaraj AI (स्वराज AI)
**Sovereign Offline AI for the Next Billion Users.**

Swaraj AI is a high-performance, **100% offline** voice assistant designed for budget Android smartphones (<4GB RAM). It bridges the digital divide for non-literate and disabled users by providing a "Hybrid Edge Intelligence" that understands regional Indian dialects (Hinglish, Tanglish, Manglish) and learns custom user routines locally.

---

## 🏗️ Architecture: The Hybrid Edge Engine

Swaraj AI uses a **"Relay Race" (Serial Loading)** strategy to overcome local hardware constraints.

### 1. Fast-Path / System 1 (Reflex Memory)
*   **Speed**: < 10ms | **RAM**: < 1MB
*   Performs instant database lookups for custom user **Macros** (e.g., *"Danger"* -> [Call Police, Toggle Torch]).

### 2. Slow-Path / System 2 (Reasoning Engine)
*   **Speed**: ~1s | **RAM**: ~892MB
*   Serially loads **Gemma 3 1B** (Quantized INT4) to parse complex natural language intent and irregular time/name slots.

### 3. The Relay Race Lifecycle
- **STT (IndicConformer)** -> [Unload] -> **Fast-Path Check** -> **SLM (Gemma 3)** -> [Unload] -> **Action (Accessibility)** -> **TTS (IndicTTS)** -> [Unload].

---

## 🏭 The Cloud Factory (Intelligence Manufacturing)

Our intelligence is distilled using a Teacher-Student model distillation on AWS:
*   **Teacher**: Amazon Bedrock (**Nova Premier**) synthesizes 20,000 high-fidelity regional command samples.
*   **Student**: **Gemma 3 1B** is fine-tuned on this dataset via **LoRA** on Amazon SageMaker.
*   **Quantization**: Optimized to INT4 (GGUF) for hyper-fast CPU execution on budget devices.

---

## 📁 Project Structure

*   **/factory**: Cloud-side engineering (Bedrock synthesis, SageMaker LoRA training).
*   **/mobile**: On-device Android runtime (Kotlin State Machine, Macro Database).
*   **/docs**: Detailed architecture diagrams and pitch-ready strategy documents.
*   **/data**: Training dataset and intent schemas.

---

## 🚀 Vision
To empower every Indian citizen with **Sovereign AI** that respects their language, their privacy, and their hardware.

## 📂 Project Structure
```text
SWARAJ-AI/
├── factory/                # Training Pipeline (AWS)
│   ├── data_synthesis/     # Amazon Bedrock generation logic
│   ├── training/           # SageMaker fine-tuning scripts
│   └── quantization/       # llama.cpp conversion guides
├── mobile/                 # Android Implementation
│   ├── assets/models/      # Target folder for GGUF/ONNX binaries
│   ├── src/main/java/      # Cognitive & Bridge logic
│   └── README.md           # Mobile setup guide
├── docs/                   # Documentation & Pitch Material
│   └── architecture.md     # Detailed technical workflow
├── scripts/                # Utility scripts
└── README.md               # Main project overview
```

## 🚀 Quick Start
### 1. Generate Training Data
```bash
cd factory/data_synthesis
python generate_data.py
```
### 2. Fine-Tune on SageMaker
```bash
cd factory/training
python finetune_lora.py
```
### 3. Deploy to Phone
Move the quantized `.gguf` file to `mobile/assets/models/` and build the Android scaffold.

---

## 👥 Hackathon Team (4 Members)
- **Data/Bedrock Engineer:** Synthesis & Regional Persona tuning.
- **Model/Training Engineer:** SageMaker Fine-tuning & Weight management.
- **Optimization Specialist:** Quantization & GGUF conversion.
- **Android/Kotlin Lead:** Serial Loading Logic & Accessibility Bridge.

---

## ⚖️ License
Licensed under the Apache 2.0 License. Optimized for Indian budget hardware.
