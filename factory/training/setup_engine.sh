#!/bin/bash

# Swaraj AI: llama.cpp Setup Script for Mac M-series
# This prepares the lightning-fast inference engine for your merged model.

echo "🏗️ Initializing Swaraj Cognitive Engine Setup..."

# 1. Clone the repository
if [ ! -d "llama.cpp" ]; then
    echo "📥 Cloning llama.cpp..."
    git clone https://github.com/ggerganov/llama.cpp
fi

cd llama.cpp

# 2. Build for Apple Silicon (Metal Acceleration)
echo "🛠️ Building with Metal support for M-series GPU..."
make -j

# 3. Install Python dependencies for conversion
echo "🐍 Installing conversion dependencies..."
pip install -r requirements.txt

echo "✅ Engine Ready! Once your model is merged, we will use this to convert it to .gguf format."
echo "Location: $(pwd)"
