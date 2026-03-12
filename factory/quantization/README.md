# Swaraj AI: Model Shrinking Pipeline (Quantization) 📉

To achieve 100% offline performance on budget smartphones, we "dehydrate" our models using quantization.

## 1. SLM (Reasoning Engine) -> GGUF
We use `llama.cpp` to convert the SageMaker LoRA adapters back into a single binary.

### Requirements:
- Python 3.10+
- `llama.cpp` repository

### Step-by-Step:
1. **Merge Weights:** Merge the LoRA adapter back into the base Qwen/Gemma model.
2. **Convert to GGUF:**
   ```bash
   python convert_hf_to_gguf.py models/swaraj-final/ --outfile swaraj_unquantized.gguf
   ```
3. **Quantize to 4-bit (Q4_K_M):**
   ```bash
   ./llama-quantize swaraj_unquantized.gguf swaraj_q4_k_m.gguf Q4_K_M
   ```
   *Result: ~374MB file size.*

---

## 2. STT (Listener) -> ONNX
For AI4Bharat's IndicConformer, we target **ONNX Runtime** for high performance on Android CPUs.

### Step-by-Step:
1. **Export:** Export the PyTorch model to ONNX.
2. **INT8 Quantization:**
   ```bash
   python -m onnxruntime.quantization.quantize_static \
     --input model.onnx \
     --output model_quant.onnx
   ```
   *Result: ~200MB file size.*

---

## 📱 Mobile Placement
Files must be placed in the Android project `assets/` folder:
- `mobile/app/src/main/assets/models/swaraj_q4_k_m.gguf`
- `mobile/app/src/main/assets/models/stt_quant.onnx`

---

## 📊 Hardware Benchmarks
| Metric | Baseline (BF16) | Optimized (Q4/INT8) |
|---|---|---|
| Model Size | ~1.4GB | **~570MB (Total)** |
| Peak RAM | ~2.2GB | **<600MB** |
| Latency (10 tokens) | ~12.5s | **~1.8s** |
