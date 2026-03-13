import boto3
import json
import time
import random
import os
from concurrent.futures import ThreadPoolExecutor, as_completed

# --- SWARAJ AI: COMPREHENSIVE DATA FACTORY (V5: PRODUCTION OPTIMIZED) ---
# Covers: Hardware, Status, Apps, WhatsApp, Navigation, Math, and Accessibility.
# Optimized for speed (10 threads) and data quality (strict formatting).

# --- OPTIMIZED SETTINGS ---
MODEL_ID = "us.amazon.nova-premier-v1:0" 
REGION = "us-east-1"
OUTPUT_FILE = "swaraj_training_dataset.jsonl"
TOTAL_SAMPLES = 10000 
THREADS = 10  # 🏎️ 10x Speed boost for hackathon generation

# --- DESIGN SYSTEM: FINAL MASTER REPERTOIRE ---
SCHEMA_DEFINITIONS = """
ACTIONS:
- TOGGLE_DEVICE (target: WIFI/BT/FLASHLIGHT/AIRPLANE/LOCATION, value: ON/OFF)
- SET_VOLUME (value: UP/DOWN/MUTE/UNMUTE)
- SET_BRIGHTNESS (value: UP/DOWN/MAX)
- LOG_SCREENSHOT
- CHECK_STATUS (target: TIME/BATTERY/DATE/NETWORK)
- OPEN_APP (target: AppName)
- CLOSE_APP
- SYSTEM_NAV (target: HOME/BACK/SETTINGS)
- REPEAT_RESPONSE
- MATH_CALC (value: pure_numeric_expression)
- MEDIA_CONTROL (value: PLAY/PAUSE/NEXT/PREV)
- CALL (target: Name/Relation)
- WHATSAPP (target: Name/Relation, value: message_text)
- SET_ALARM (value: time_string)
- NAVIGATE (target: destination_name)
"""

PERSONAS = [
    "A Gen-Z student in Delhi who speaks rapid Hinglish, uses slang like 'yaar' and 'zara', and loves social media/music apps",
    "A college student in Chennai who uses Tanglish (Tamil-English mix), frequently code-switches, and asks for Maps or WhatsApp",
    "A retired elderly person in a Tamil village who speaks simple Tamil but knows English words like 'WiFi', 'Battery', and 'Flashlight'",
    "A small shopkeeper in North India who speaks 'Bazaari' Hindi mixed with English keywords, very direct and practical in commands",
    "A visually impaired professional who uses standard English or Hinglish and relies heavily on repeat responses and system navigation",
    "A busy office-goer in Bangalore who uses a mix of English and Hindi, focusing on productivity, alarms, and quick status checks"
]

bedrock = boto3.client(service_name='bedrock-runtime', region_name=REGION)

def generate_batch(batch_id, persona, batch_size=50):
    seed = batch_id % 5
    if seed == 0:
        cat = "ACCESSIBILITY_AND_NAV"
        rules = "Swaraj, repeat that, Go home, Go back, Open settings, Repeat response"
        schema_hint = """
        - Nav: {"action": "SYSTEM_NAV", "target": "HOME/BACK/SETTINGS"}
        - Repeat: {"action": "REPEAT_RESPONSE"}
        """
    elif seed == 1:
        cat = "MEDIA_AND_MATH"
        rules = "Play music, Pause, Next song, What is 55 plus 23?, Calculate total"
        schema_hint = """
        - Media: {"action": "MEDIA_CONTROL", "value": "PLAY/PAUSE/NEXT/PREV"}
        - Math: {"action": "MATH_CALC", "value": "pure_numeric_expression e.g. '55+23'"}
        """
    elif seed == 2:
        cat = "STATUS_AND_HARDWARE"
        rules = "Battery status, Flashlight on, WiFi off, Brightness up, Screenshot"
        schema_hint = """
        - Hardware: {"action": "TOGGLE_DEVICE", "target": "WIFI/BT/FLASHLIGHT/AIRPLANE/LOCATION", "value": "ON/OFF"}
        - Status: {"action": "CHECK_STATUS", "target": "TIME/BATTERY/DATE/NETWORK"}
        """
    elif seed == 3:
        cat = "COMMUNICATION_WHATSAPP"
        rules = "Send [Hi] to [Ram] on WhatsApp, Call Mom, WhatsApp Suresh, Msg Driver"
        schema_hint = """
        - WhatsApp: {"action": "WHATSAPP", "target": "Name/Relation", "value": "Msg"}
        - Call: {"action": "CALL", "target": "Name/Relation"}
        """
    else:
        cat = "APP_AND_UTILITIES"
        rules = "Open Instagram, Close app, Set alarm for 8am, Navigate to Hospital"
        schema_hint = """
        - Apps: {"action": "OPEN_APP", "target": "AppName"} or {"action": "CLOSE_APP"}
        - Alarm: {"action": "SET_ALARM", "value": "Natural sounding time"}
        - Maps: {"action": "NAVIGATE", "target": "Destination"}
        """

    prompt = f"""
    <task>
    Generate exactly {batch_size} unique JSON objects for Swaraj AI training.
    Persona: {persona} | Category: {cat}
    Target Patterns: {rules}
    [SCHEMA] {schema_hint}

    [STRICT RULES]
    1. For MATH_CALC: 'value' MUST be a pure numeric expression (e.g. '45/5', '100-23', '2*50'). NO words like 'plus' in value.
    2. For WHATSAPP/CALL: Use diverse Indian names (Priya, Rajesh) and relationship titles (Amma, Uncle, Boss, Driver).
    3. For SET_ALARM: Use varied formats ('5:30 PM', 'subah 6 baje', 'evening 7:15').
    4. For TEXT: NO underscores, use natural spacing and conversational phrasing.
    5. Linguistic: Use Hindi, English, Tamil, Hinglish, Tanglish. Mix 50/50 Code-Switching.

    Output ONLY a JSON list of: {{"text": "...", "intent": {{...}}}}
    </task>
    """

    for attempt in range(5):
        try:
            # Reduced sleep for faster multi-threading, but still avoiding heavy spikes
            time.sleep(random.uniform(0.5, 2.0)) 
            res = bedrock.invoke_model(modelId=MODEL_ID, body=json.dumps({
                "inferenceConfig": {"max_new_tokens": 8192, "temperature": 0.8},
                "messages": [{"role": "user", "content": [{"text": prompt}]}]
            }))
            raw = json.loads(res['body'].read())['output']['message']['content'][0]['text']
            
            start = raw.find("[")
            end = raw.rfind("]") + 1
            if start != -1 and end != 0:
                data = json.loads(raw[start:end])
                validated = []
                for item in data:
                    if "text" in item and "intent" in item:
                        # --- STRICT KEY FILTERING (No Hallucinated Keys) ---
                        clean_item = {
                            "text": str(item["text"]),
                            "intent": {
                                "action": str(item["intent"].get("action", "")).upper(),
                                "target": str(item["intent"].get("target", "")),
                                "value": str(item["intent"].get("value", ""))
                            }
                        }
                        validated.append(clean_item)
                return validated
        except Exception as e:
            if "ThrottlingException" in str(e):
                time.sleep(10)
            else:
                time.sleep(2)
    return []

def main():
    print(f"🚀 LAUNCHING V5 PRODUCTION RUN: {TOTAL_SAMPLES} SAMPLES (THREADS={THREADS})...")
    dataset = []
    seen = set()
    
    # Pre-clean the output file if it exists only if starting fresh
    # with open(OUTPUT_FILE, "w", encoding="utf-8") as f: pass

    with open(OUTPUT_FILE, "a+", encoding="utf-8") as f:
        f.seek(0)
        for line in f:
            try:
                obj = json.loads(line)
                seen.add(obj['text'].strip().lower())
                dataset.append(obj)
            except: continue
        
        print(f"📊 Resuming from {len(dataset)} existing samples...")

        with ThreadPoolExecutor(max_workers=THREADS) as ex:
            batches_needed = (TOTAL_SAMPLES - len(dataset)) // 50 + 20
            futures = [ex.submit(generate_batch, i, random.choice(PERSONAS), 50) for i in range(batches_needed)]
            
            for fut in as_completed(futures):
                batch = fut.result()
                if not batch: continue
                
                for item in batch:
                    t = item['text'].strip().lower()
                    if t not in seen:
                        f.write(json.dumps(item, ensure_ascii=False) + "\n")
                        f.flush()
                        seen.add(t)
                        dataset.append(item)
                
                print(f"📈 Progress: {len(dataset)} / {TOTAL_SAMPLES} (Latest: {batch[0]['text'][:30]}...)")
                if len(dataset) >= TOTAL_SAMPLES: 
                    # Cancel pending futures
                    for f_to_cancel in futures:
                        f_to_cancel.cancel()
                    break

    print(f"✅ PRODUCTION COMPLETE! Dataset saved to {OUTPUT_FILE}")

if __name__ == "__main__": 
    main()
