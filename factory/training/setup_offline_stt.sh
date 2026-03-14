#!/bin/bash

# Swaraj AI: Offline Voice Engine Setup
# This script downloads the Whisper Tiny (INT8 Quantized) model
# directly to the Android assets folder for 100% offline usage.

ASSETS_DIR="/Users/sooryachandirang/SWARAJ-AI/mobile/src/main/assets/models/stt"
mkdir -p "$ASSETS_DIR"

echo "📥 Downloading Whisper Tiny (INT8 Quantized)..."

# Whisper Tiny Multilingual (Quantized for Mobile)
# URL: HuggingFace (ggml-tiny.bin is the standard format for whisper.cpp android libs)
curl -L "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin?download=true" -o "$ASSETS_DIR/whisper-tiny.bin"

echo "✅ Whisper model downloaded to: $ASSETS_DIR/whisper-tiny.bin"
echo "📏 Model size: ~75MB"
