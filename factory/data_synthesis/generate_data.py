import boto3
import json
import time
import random
import os
from concurrent.futures import ThreadPoolExecutor, as_completed

# --- AWS CONFIGURATION ---
MODEL_ID = "amazon.nova-premier-v1:0" 
REGION = "us-east-1"
OUTPUT_FILE = "../../data/swaraj_training_dataset.jsonl"
TOTAL_SAMPLES = 10000
THREADS = 10  # Optimal for ml.m7i.xlarge

# --- REGIONAL PERSONAS (Linguistic Diversity Engine) ---
PERSONAS = [
    "A rural farmer in Tamil Nadu (Tanglish/Tamil priority)",
    "A student in Delhi using Gen-Z Hinglish slang",
    "A street vendor in Mumbai using 'Bambaiya' Hindi",
    "An elderly person in Kochi, Kerala (Manglish focus)",
    "A gig-worker in Bangalore (Kannada-English-Hindi mix)",
    "A taxi driver in Kolkata (Bengali-English focus)",
    "A teenager in Hyderabad (Tenglish focus)"
]

bedrock = boto3.client(service_name='bedrock-runtime', region_name=REGION)

def generate_batch(batch_id, persona, batch_size=40):
    """Generates a batch of intent-action pairs using Nova Premier."""
    streams = {
        "basic": ["Flashlight", "WiFi", "Bluetooth", "Volume Control", "Brightness", "Screenshot"],
        "contextual": ["Make Call", "WhatsApp Msg", "Add to List", "Set Alarm", "Navigation"]
    }
    current_stream = "basic" if batch_id % 2 == 0 else "contextual"
    
    prompt = f"""
    <task>
    Generate {batch_size} unique mobile voice commands for Swaraj AI.
    Persona: {persona} | Category: {current_stream}
    Targets: {', '.join(streams[current_stream])}
    
    [Rules]
    - Linguistic: 40% Hinglish, 40% Tanglish, 20% English.
    - Diversity: Use varied local names (e.g., Suresh, Priya) and places (e.g., Clinic, Market).
    - Format: Return ONLY a JSON list of objects.
    - JSON Schema: {{"text": "command string", "intent": {{"action": "string", "target": "string", "state": "ON/OFF/NA"}}}}
    </task>
    """

    try:
        response = bedrock.invoke_model(
            modelId=MODEL_ID,
            body=json.dumps({
                "inferenceConfig": {"max_new_tokens": 4096, "temperature": 0.9},
                "messages": [{"role": "user", "content": [{"text": prompt}]}]
            })
        )
        result = json.loads(response['body'].read())
        raw_output = result['output']['message']['content'][0]['text']
        
        # Extract JSON list
        start = raw_output.find("[")
        end = raw_output.rfind("]") + 1
        if start != -1 and end != 0:
            data = json.loads(raw_output[start:end])
            # Validate schema
            return [i for i in data if isinstance(i, dict) and "text" in i and "intent" in i]
    except Exception as e:
        print(f"Batch {batch_id} Error: {e}")
        return []
    return []

def main():
    os.makedirs("../../data", exist_ok=True)
    print(f"🚀 Starting Synthesis of {TOTAL_SAMPLES} samples...")
    
    global_dataset = []
    seen_texts = set()
    num_batches = (TOTAL_SAMPLES // 40) + 5

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        with ThreadPoolExecutor(max_workers=THREADS) as executor:
            futures = [executor.submit(generate_batch, i, random.choice(PERSONAS), 40) for i in range(num_batches)]
            for future in as_completed(futures):
                batch = future.result()
                if batch:
                    for item in batch:
                        txt = item['text'].strip().lower()
                        if txt not in seen_texts:
                            f.write(json.dumps(item, ensure_ascii=False) + "\n")
                            seen_texts.add(txt)
                            global_dataset.append(item)
                    print(f"📊 Progress: {len(global_dataset)} / {TOTAL_SAMPLES}")
                if len(global_dataset) >= TOTAL_SAMPLES: break

    print(f"✅ Dataset complete: {len(global_dataset)} unique samples.")

if __name__ == "__main__":
    main()
