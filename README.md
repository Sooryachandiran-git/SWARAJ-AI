# Swaraj AI: Offline Multilingual Assistant for Budget Smartphones 🇮🇳

Swaraj AI is an offline "Sovereign AI" agent designed for India's ₹8,000 smartphone segment. By distilling massive cloud intelligence into a sub-1GB local model, we provide a high-performance voice assistant that works without internet, respects privacy, and understands regional vernacular (Hinglish/Tanglish).

## 🏗 High-Level Architecture
Our system follows a **"Factory-Runtime-Bridge"** architecture, split across the AWS cloud and local Android device.

### 🌐 The Cloud Factory (AWS)
- **Data Teacher (Amazon Bedrock):** Uses Nova Premier to generate 10,000+ synthetic intent-action samples across regional personas.
- **Training Ground (Amazon SageMaker):** Uses LoRA (Low-Rank Adaptation) to fine-tune a student model (Gemma 3 1B or Qwen 0.5B) to output deterministic JSON schemas.
- **Shrinking Lab:** Models are quantized to GGUF (4-bit) to run on budget hardware.

### 📱 The Offline Runtime (Android)
- **The Listener (STT):** Uses AI4Bharat's **IndicConformer** for speech-to-text.
- **The Thinker (SLM):** A local reasoning engine parses text into JSON intents.
- **Serial Loading Logic:** To save RAM, the app uses a state machine to swap models in and out of memory sequentially.
- **Action Executor (Bridge):** Android **Accessibility Services** translate JSON intents into physical UI interactions (clicks, scrolling, system toggles).

---

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
