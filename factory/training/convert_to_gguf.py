"""
Swaraj AI: GGUF Conversion + Quantization Script
=================================================
Converts the merged Qwen 0.5B model to GGUF format and quantizes to Q4_K_M.

Expected output sizes:
  F16 GGUF:      ~900 MB
  Q4_K_M GGUF:   ~350 MB  ← TARGET (push this to phone)

Usage:
    cd factory/training
    python3 convert_to_gguf.py
"""

import os
import subprocess
import sys

MERGED_DIR  = "./swaraj-qwen05b-merged"
LLAMA_CPP   = "./llama.cpp"
OUTPUT_F16  = "./swaraj-qwen05b-f16.gguf"
OUTPUT_Q4   = "./swaraj-qwen05b-q4km.gguf"

ANDROID_HOME = os.path.expanduser("~/Library/Android/sdk")
ADB          = os.path.join(ANDROID_HOME, "platform-tools", "adb")
SDCARD_PATH  = "/sdcard/SwarajAI/swaraj_brain.gguf"   # app reads this filename


def run(cmd, desc):
    print(f"\n🔧 {desc}")
    print(f"   $ {' '.join(cmd)}")
    result = subprocess.run(cmd, capture_output=False)
    if result.returncode != 0:
        print(f"❌ FAILED (exit code {result.returncode})")
        sys.exit(1)
    print(f"   ✅ Done")


def check_prerequisites():
    print("🔍 Checking prerequisites...")

    if not os.path.isdir(MERGED_DIR):
        print(f"❌ Merged model not found at {MERGED_DIR}")
        print(f"   Run finetune_qwen.py first!")
        sys.exit(1)

    convert_script = os.path.join(LLAMA_CPP, "convert_hf_to_gguf.py")
    if not os.path.exists(convert_script):
        print(f"❌ llama.cpp not found at {LLAMA_CPP}")
        print(f"   Make sure llama.cpp is cloned in factory/training/")
        sys.exit(1)

    # Check llama-quantize
    result = subprocess.run(["which", "llama-quantize"], capture_output=True)
    if result.returncode != 0:
        print("❌ llama-quantize not found. Install with: brew install llama.cpp")
        sys.exit(1)

    print(f"   ✅ All prerequisites found")


def convert_to_gguf():
    """Step 1: Convert HuggingFace merged model → F16 GGUF"""
    convert_script = os.path.join(LLAMA_CPP, "convert_hf_to_gguf.py")
    run(
        [sys.executable, convert_script,
         MERGED_DIR,
         "--outfile", OUTPUT_F16,
         "--outtype", "f16"],
        f"Converting {MERGED_DIR} → F16 GGUF"
    )
    size_mb = os.path.getsize(OUTPUT_F16) / 1024 / 1024
    print(f"   📦 F16 GGUF size: {size_mb:.0f} MB")


def quantize():
    """Step 2: Quantize F16 GGUF → Q4_K_M GGUF (~350 MB)"""
    run(
        ["llama-quantize", OUTPUT_F16, OUTPUT_Q4, "Q4_K_M"],
        f"Quantizing to Q4_K_M"
    )
    size_mb = os.path.getsize(OUTPUT_Q4) / 1024 / 1024
    print(f"   📦 Q4_K_M GGUF size: {size_mb:.0f} MB")


def push_to_phone():
    """Step 3: Push the quantized model to the Android phone via ADB"""
    print(f"\n📱 Checking for connected Android device...")
    result = subprocess.run([ADB, "devices"], capture_output=True, text=True)
    lines = result.stdout.strip().split("\n")
    devices = [l for l in lines[1:] if "device" in l and "offline" not in l]

    if not devices:
        print("⚠️  No device connected. To push manually, run:")
        print(f"   {ADB} shell mkdir -p /sdcard/SwarajAI")
        print(f"   {ADB} push {OUTPUT_Q4} {SDCARD_PATH}")
        return

    print(f"   ✅ Device found: {devices[0]}")

    # Create folder on phone
    subprocess.run([ADB, "shell", "mkdir", "-p", "/sdcard/SwarajAI"])

    # Push STT models if they exist in assets
    assets_dir = "../../mobile/src/main/assets/models"
    onnx_models = ["encoder_quantized_int8.onnx", "ctc_decoder_quantized_int8.onnx"]
    
    for model in onnx_models:
        local_path = os.path.join(assets_dir, model)
        if os.path.exists(local_path):
            print(f"📤 Pushing STT Engine: {model}")
            subprocess.run([ADB, "push", local_path, f"/sdcard/SwarajAI/{model}"])

    size_mb = os.path.getsize(OUTPUT_Q4) / 1024 / 1024
    print(f"\n📤 Pushing Swaraj Brain: {size_mb:.0f} MB...")
    run(
        [ADB, "push", OUTPUT_Q4, SDCARD_PATH],
        f"Pushing Q4_K_M model to {SDCARD_PATH}"
    )
    print(f"\n🎉 All done! Everything is on your phone.")
    print(f"   Location: /sdcard/SwarajAI/")
    print(f"   Tap 'TAP TO SPEAK' in the Swaraj app to test!")


if __name__ == "__main__":
    print("=" * 55)
    print("  Swaraj AI — GGUF Conversion + Quantization")
    print("=" * 55)

    check_prerequisites()
    convert_to_gguf()
    quantize()
    push_to_phone()

    print("\n" + "=" * 55)
    print("  ✅ PIPELINE COMPLETE")
    print(f"  Model: {OUTPUT_Q4}")
    print("=" * 55)
