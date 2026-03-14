#!/bin/bash

# Swaraj AI: Indic Model Downloader
# This script pulls the Ai4Bharat mobile-optimized models for STT and TTS.

ASSETS_DIR="/Users/sooryachandirang/SWARAJ-AI/mobile/src/main/assets/models"
mkdir -p "$ASSETS_DIR"

echo "🏗️ Initializing Model Retrieval (IndicConformer & IndicTTS)..."

# 1. IndicConformer (STT) - Mobile Optimized ONNX
# We recommend the 'Indic-Conformer-ASR' mobile suite from Ai4Bharat
echo "🎙️ Downloading IndicConformer (STT) [~180MB]..."
# Note: These are placeholder URLs. In a real environment, we'd use the HuggingFace CLI 
# or direct links from the AI4Bharat/IndicConformerASR repo.
curl -L "https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual/resolve/main/model.onnx?download=true" -o "$ASSETS_DIR/indic_stt.onnx"

# 2. IndicTTS (FastSpeech2 / VITS) - Mobile Optimized
echo "📢 Downloading IndicTTS (TTS) [~120MB]..."
curl -L "https://huggingface.co/ai4bharat/indic-tts-v2-hi/resolve/main/model.onnx?download=true" -o "$ASSETS_DIR/indic_tts.onnx"

echo "✅ Models placed in mobile/src/main/assets/models/"
echo "Total Storage Used: ~300MB"
