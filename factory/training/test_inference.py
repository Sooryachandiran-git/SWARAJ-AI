import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import PeftModel
import json

# --- CONFIG ---
BASE_MODEL = "google/gemma-3-1b-it" 
ADAPTER_PATH = "./swaraj-gemma3-1b-adapter"

def test_swaraj():
    print(f"🧪 Initializing Swaraj AI Test Bench...")
    
    # 1. Load Tokenizer
    tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL)
    
    # 2. Load Base Model (MPS Optimized)
    print("🧠 Loading Base Model...")
    base_model = AutoModelForCausalLM.from_pretrained(
        BASE_MODEL,
        torch_dtype=torch.float16,
        device_map={"": "mps"}
    )
    
    # 3. Load Trained Adapter
    print("💎 Injecting Swaraj Knowledge (LoRA Adapter)...")
    model = PeftModel.from_pretrained(base_model, ADAPTER_PATH)
    model.eval()

    print("\n✅ SWARAJ AI IS ONLINE")
    print("Type a command (or 'exit' to quit)")
    print("-" * 30)

    while True:
        user_input = input("\n🗣️ User: ")
        if user_input.lower() in ['exit', 'quit']: break

        # Format using the exact same template used in training
        prompt = (
            f"<start_of_turn>user\nMap this voice command to a Swaraj AI intent JSON:\n{user_input}<end_of_turn>\n"
            f"<start_of_turn>model\n"
        )

        inputs = tokenizer(prompt, return_tensors="pt").to("mps")
        
        with torch.no_grad():
            outputs = model.generate(
                **inputs, 
                max_new_tokens=128,
                do_sample=False # Greedy search for deterministic JSON
            )
        
        # Decode and extract only the model's response
        decoded = tokenizer.decode(outputs[0], skip_special_tokens=True)
        # Extract everything after the prompt intent instruction
        response = decoded.split("model\n")[-1].strip()
        
        print(f"🤖 Swaraj Intent: {response}")

if __name__ == "__main__":
    test_swaraj()
