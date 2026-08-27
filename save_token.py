#!/usr/bin/env python3
import sys
import json
import os

TOKEN_FILE = "/media/siddhartha/main_hard_disk1/personal/.gdrive_token.json"

if len(sys.argv) > 1:
    token = sys.argv[1].strip()
    data = {}
    if os.path.exists(TOKEN_FILE):
        try:
            with open(TOKEN_FILE, 'r') as f:
                data = json.load(f)
        except Exception:
            pass
    data["access_token"] = token
    with open(TOKEN_FILE, 'w') as f:
        json.dump(data, f, indent=2)
    print("========================================================")
    print("✅ SUCCESS: Google Drive OAuth Access Token Saved!")
    print(f"📁 Token File: {TOKEN_FILE}")
    print("========================================================")
    print("🚀 You can now run ./upload_apk.sh anytime to upload automatically!")
    print("========================================================")
else:
    print("Usage: python3 save_token.py <YOUR_OAUTH_ACCESS_TOKEN>")
