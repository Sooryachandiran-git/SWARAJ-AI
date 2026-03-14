"""
Swaraj AI: Qwen 0.5B Fine-Tuning Pipeline
==========================================
Fine-tunes Qwen2.5-0.5B-Instruct on the Swaraj intent classification dataset.

Why Qwen 0.5B?
- Base F16:  ~900MB
- Q4_K_M:   ~350MB (fits on any Android phone)
- Task:     Intent JSON classification (simple → small model wins)
- Dataset:  7524 Tamil/English voice command → JSON pairs

Usage:
    cd factory/training
    source venv/bin/activate
    python3 finetune_qwen.py
"""

import os
import json
import torch
from datasets import load_dataset
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import LoraConfig, get_peft_model
from trl import SFTTrainer, SFTConfig

# ─── CONFIG ────────────────────────────────────────────────────
MODEL_ID      = "Qwen/Qwen2.5-0.5B-Instruct"
DATASET_PATH  = "swaraj_final_clean.jsonl"
OUTPUT_DIR    = "./swaraj-qwen05b-adapter"
MERGED_DIR    = "./swaraj-qwen05b-merged"

# Qwen 0.5B fits entirely in Mac RAM — no chunking needed
MAX_SEQ_LEN   = 256   # Intent responses are short JSON → 256 is plenty
BATCH_SIZE    = 4     # Higher than Gemma (model is smaller)
GRAD_ACCUM    = 4     # effective batch = 16
EPOCHS        = 3
LR            = 2e-4
# ───────────────────────────────────────────────────────────────


def format_sample(example):
    """
    Converts dataset row to Qwen chat format.
    Input:  {"text": "Open WhatsApp", "intent": {"action": "OPEN_APP", ...}}
    Output: <|im_start|>user\n...<|im_end|>\n<|im_start|>assistant\n{...}<|im_end|>
    """
    user_cmd = example["text"]
    intent   = example["intent"]
    intent_json = json.dumps(intent, ensure_ascii=False) if isinstance(intent, dict) else str(intent)

    return {
        "text": (
            f"<|im_start|>system\n"
            f"You are Swaraj AI. Map voice commands to intent JSON. Reply only with JSON.<|im_end|>\n"
            f"<|im_start|>user\n{user_cmd}<|im_end|>\n"
            f"<|im_start|>assistant\n{intent_json}<|im_end|>"
        )
    }


def main():
    # ── 1. Load Dataset ──────────────────────────────────────────
    print(f"📋 Loading dataset from {DATASET_PATH}...")
    if not os.path.exists(DATASET_PATH):
        print(f"❌ Dataset not found: {DATASET_PATH}")
        return

    dataset = load_dataset("json", data_files=DATASET_PATH, split="train")
    print(f"   ✅ {len(dataset)} samples loaded")

    dataset = dataset.map(format_sample, remove_columns=dataset.column_names)
    print(f"   ✅ Formatted to Qwen chat template")

    # ── 2. Load Tokenizer ─────────────────────────────────────────
    print(f"\n🔤 Loading tokenizer for {MODEL_ID}...")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    # ── 3. Load Model ─────────────────────────────────────────────
    print(f"\n🧠 Loading {MODEL_ID} onto Apple MPS...")
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_ID,
        torch_dtype=torch.float16,
        device_map={"": "mps"},
        trust_remote_code=True
    )
    model.config.use_cache = False   # Required for gradient checkpointing
    print(f"   ✅ Model loaded ({sum(p.numel() for p in model.parameters())/1e6:.0f}M params)")

    # ── 4. Apply LoRA ─────────────────────────────────────────────
    # Qwen uses same attention projection names as Llama
    lora_config = LoraConfig(
        r=16,                   # rank — 16 is good for small models
        lora_alpha=32,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj",
                        "gate_proj", "up_proj", "down_proj"],
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM"
    )
    model = get_peft_model(model, lora_config)
    model.print_trainable_parameters()

    # ── 5. Train ──────────────────────────────────────────────────
    print(f"\n🚀 Starting Fine-Tuning (Qwen 0.5B × {len(dataset)} samples × {EPOCHS} epochs)...")
    print(f"   ETA: ~10-15 minutes on Apple Silicon\n")

    sft_config = SFTConfig(
        output_dir=OUTPUT_DIR,
        per_device_train_batch_size=BATCH_SIZE,
        gradient_accumulation_steps=GRAD_ACCUM,
        learning_rate=LR,
        num_train_epochs=EPOCHS,
        max_length=MAX_SEQ_LEN,
        logging_steps=25,
        save_strategy="epoch",
        fp16=False,             # MPS uses float16 via model dtype, not trainer flag
        optim="adamw_torch",
        report_to="none",
        dataset_text_field="text",
        warmup_ratio=0.03,
        lr_scheduler_type="cosine",
    )

    trainer = SFTTrainer(
        model=model,
        train_dataset=dataset,
        args=sft_config,
        processing_class=tokenizer,
    )

    trainer.train()

    # ── 6. Save LoRA Adapter ──────────────────────────────────────
    print(f"\n💾 Saving LoRA adapter to {OUTPUT_DIR}...")
    trainer.model.save_pretrained(OUTPUT_DIR)
    tokenizer.save_pretrained(OUTPUT_DIR)
    print(f"   ✅ Adapter saved!")

    # ── 7. Merge and Save Full Model ──────────────────────────────
    print(f"\n🔗 Merging LoRA weights into base model...")
    from peft import PeftModel

    base_model = AutoModelForCausalLM.from_pretrained(
        MODEL_ID,
        torch_dtype=torch.float16,
        device_map={"": "cpu"},  # CPU for safe merge
        trust_remote_code=True
    )
    merged_model = PeftModel.from_pretrained(base_model, OUTPUT_DIR)
    merged_model = merged_model.merge_and_unload()

    print(f"💾 Saving merged model to {MERGED_DIR}...")
    os.makedirs(MERGED_DIR, exist_ok=True)
    merged_model.save_pretrained(MERGED_DIR, safe_serialization=True)
    tokenizer.save_pretrained(MERGED_DIR)
    print(f"   ✅ Merged model saved! ({MERGED_DIR})")
    print(f"\n🎯 Next step: Convert to GGUF and quantize:")
    print(f"   python3 convert_to_gguf.py")


if __name__ == "__main__":
    main()
