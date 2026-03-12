import os
import torch
from datasets import load_dataset
from transformers import AutoModelForCausalLM, AutoTokenizer, TrainingArguments
from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
from trl import SFTTrainer, SFTConfig

# --- HACKATHON CONFIG ---
MODEL_ID = "Qwen/Qwen2.5-0.5B-Instruct"  # Optimized for sub-1GB RAM targets
DATASET_PATH = "../../data/swaraj_training_dataset.jsonl"
OUTPUT_DIR = "./swaraj-slm-lora-adapter"

def main():
    if not os.path.exists(DATASET_PATH):
        print(f"❌ Error: Dataset not found at {DATASET_PATH}. Run synthesis first.")
        return

    print(f"🏗 Preparing Training for {MODEL_ID}...")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
    tokenizer.pad_token = tokenizer.eos_token
    tokenizer.padding_side = "right"

    # 1. Load & Format Data
    dataset = load_dataset("json", data_files=DATASET_PATH, split="train")
    
    def format_prompts(example):
        system_msg = "You are a mobile action assistant. Map the voice command to a tool call."
        return {
            "text": f"<|im_start|>system\n{system_msg}<|im_end|>\n"
                    f"<|im_start|>user\n{example['text']}<|im_end|>\n"
                    f"<|im_start|>assistant\n{example['intent']}<|im_end|>"
        }
    
    dataset = dataset.map(format_prompts)

    # 2. Model Prep (LoRA)
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_ID, 
        torch_dtype=torch.bfloat16,
        device_map="auto"
    )
    model = prepare_model_for_kbit_training(model)
    
    lora_config = LoraConfig(
        r=16, lora_alpha=32,
        target_modules=["q_proj", "v_proj", "o_proj", "k_proj"],
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM"
    )
    model = get_peft_model(model, lora_config)

    # 3. SageMaker Optimized Training
    training_args = SFTConfig(
        output_dir=OUTPUT_DIR,
        dataset_text_field="text",
        max_seq_length=256,
        per_device_train_batch_size=4,
        gradient_accumulation_steps=4,
        learning_rate=2e-4,
        num_train_epochs=3,
        logging_steps=10,
        bf16=True,
        save_strategy="epoch",
        optim="paged_adamw_32bit"
    )

    trainer = SFTTrainer(
        model=model,
        train_dataset=dataset,
        args=training_args,
    )

    print("🚀 Starting Fine-Tuning...")
    trainer.train()
    
    trainer.model.save_pretrained(OUTPUT_DIR)
    tokenizer.save_pretrained(OUTPUT_DIR)
    print(f"✅ LoRA Adapter saved to {OUTPUT_DIR}")

if __name__ == "__main__":
    main()
