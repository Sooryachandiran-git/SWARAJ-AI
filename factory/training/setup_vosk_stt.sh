#!/bin/bash

# Swaraj AI: Plan B - Offline Voice Engine (Vosk Hindi)
# This script downloads a stable Hindi model for Vosk.

ASSETS_DIR="/Users/sooryachandirang/SWARAJ-AI/mobile/src/main/assets"
MODEL_NAME="vosk-model-small-hi-0.22"
mkdir -p "$ASSETS_DIR"

echo "📥 Downloading Vosk Hindi Model ($MODEL_NAME)..."
# Use direct link from alphacephei
curl -L "https://alphacephei.com/vosk/models/$MODEL_NAME.zip" -o "/tmp/$MODEL_NAME.zip"

echo "📦 Extracting model to assets..."
# Unzip directly into assets
unzip -o -q "/tmp/$MODEL_NAME.zip" -d "$ASSETS_DIR"

echo "✅ Vosk model ready: assets/$MODEL_NAME"
echo "📏 Size: ~42MB"

# Cleanup
rm "/tmp/$MODEL_NAME.zip"
