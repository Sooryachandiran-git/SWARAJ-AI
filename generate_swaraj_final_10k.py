import boto3
import json
import time
import random
import os
from concurrent.futures import ThreadPoolExecutor, as_completed

# --- AWS CONFIGURATION (DAKSH MANDATED) ---
MODEL_ID = "amazon.nova-premier-v1:0" 
REGION = "us-east-1"
OUTPUT_FILE = "swaraj_final_dataset.jsonl"
TOTAL_SAMPLES = 10000
THREADS = 10  # Optimal for ml.m7i.xlarge (4 vCPUs)

# --- DIVERSITY ENGINE: REGIONAL PERSONAS ---
PERSONAS = [
    "A rural farmer in Tamil Nadu using Tanglish (Tamil + English) for basic phone tasks.",
    "A college student in Delhi using 'Gen-Z' Hinglish (Hindi + English) with lots of slang.",
    "A busy street food vendor in Mumbai using rapid-fire, shorthand 'Bambaiya' Hinglish.",
    "An elderly person in Kochi, Kerala using formal Malayalam-English (Manglish).",
    "A gig-worker (delivery partner) in Bangalore using a Kannada-English-Hindi mix.",
    "A taxi driver in Kolkata using polite but direct Bengali-English commands.",
    "A teenager in Hyderabad using 'Tenglish' (Telugu + English) slang for mobile shortcuts."
]

bedrock = boto3.client(service_name='bedrock-runtime', region_name=REGION)

def generate_batch(batch_id, persona, batch_size=40):
    """Generates a batch with Schema Enforcement and Validation."""
    streams = {
        "basic": ["Flashlight (ON/OFF)", "WiFi", "Bluetooth", "Volume", "Brightness", "Screenshot"],
        "contextual": ["Make Call", "WhatsApp Message", "Add to List", "Set Alarm", "Navigate to Place"]
    }
    current_stream = "basic" if batch_id % 2 == 0 else "contextual"
    
    prompt = f"""
    <task>
    Generate exactly {batch_size} unique mobile voice commands.
    Persona: {persona} | Category: {current_stream}
    
    [Parameters]
    - Diversity: Use varied Indian names and destinations.
    - Linguistic: 40% Hinglish, 40% Tanglish, 20% English.
    - Tone: Natural, conversational, include regional fillers.
    
    [Format]
    Return ONLY a valid JSON list. 
    Schema: [{{'text': '...', 'intent': {{'action': '...', 'target': '...', 'state': '...'}}}}]
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
        
        start = raw_output.find("[")
        end = raw_output.rfind("]") + 1
        
        if start != -1 and end != 0:
            data = json.loads(raw_output[start:end])
            
            # --- SAFETY NET 1: SCHEMA ENFORCEMENT ---
            validated_batch = [
                item for item in data 
                if isinstance(item, dict) and "text" in item and "intent" in item
            ]
            return validated_batch
            
    except Exception as e:
        print(f"⚠️ Batch {batch_id} Failed: {e}")
        return []
    return []

def main():
    print("--- 🚀 SWARAJ AI: STARTING 10K PRODUCTION DATASET SYNTHESIS ---")
    
    global_dataset = []
    seen_texts = set() # --- SAFETY NET 2: GLOBAL DUPLICATE HANDLING ---
    batch_size = 40
    num_batches = (TOTAL_SAMPLES // batch_size) + 5 # Extra batches to offset validation drops

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        with ThreadPoolExecutor(max_workers=THREADS) as executor:
            futures = [
                executor.submit(generate_batch, i, random.choice(PERSONAS), batch_size) 
                for i in range(num_batches)
            ]
            
            for future in as_completed(futures):
                batch = future.result()
                if batch:
                    for item in batch:
                        if item['text'].strip().lower() not in seen_texts:
                            f.write(json.dumps(item, ensure_ascii=False) + "\n")
                            seen_texts.add(item['text'].strip().lower())
                            global_dataset.append(item)
                    
                    print(f"📊 Progress: {len(global_dataset)} / {TOTAL_SAMPLES} (Unique)")
                
                if len(global_dataset) >= TOTAL_SAMPLES:
                    break

    print(f"\n✨ COMPLETE: {len(global_dataset)} unique, validated samples generated.")
    print(f"Saved to: {os.path.abspath(OUTPUT_FILE)}")

if __name__ == "__main__":
    start = time.time()
    main()
    print(f"Total Time: {round((time.time() - start)/60, 2)} minutes")
