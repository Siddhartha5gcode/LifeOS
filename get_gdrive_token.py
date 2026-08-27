#!/usr/bin/env python3
import sys
import json
import os

TOKEN_FILE = "/media/siddhartha/main_hard_disk1/personal/.gdrive_token.json"
FOLDER_ID = "1x9imE7jmbDwNUH94lF54Xc0MMDl1wqbJ"

print("========================================================")
print("🔑 GOOGLE DRIVE OAUTH TOKEN CONFIGURATION")
print("========================================================")
print("Note: '1x9imE7jmbDwNUH94lF54Xc0MMDl1wqbJ' is your Google Drive Folder ID.")
print("The script needs your Google Drive Access Token (starts with 'ya29.').")
print("========================================================")
print("📋 2-STEP TOKEN GENERATION (Takes 15 seconds):")
print("1. Open Google OAuth Playground in your browser:")
print("   https://developers.google.com/oauthplayground/")
print("2. Scroll down on the left, select 'Drive API v3' -> 'https://www.googleapis.com/auth/drive.file'")
print("3. Click 'Authorize APIs' -> Sign in as siddhartha12495@gmail.com")
print("4. Click 'Exchange authorization code for tokens'")
print("5. Copy the 'Access token' (ya29.a0...) and paste it below:")
print("========================================================")

try:
    token = input("🔑 Paste Access Token (ya29...): ").strip()
    if token.startswith("ya29") or len(token) > 20:
        with open(TOKEN_FILE, 'w') as f:
            json.dump({"access_token": token}, f)
        print("========================================================")
        print("✅ SUCCESS: Token saved to .gdrive_token.json!")
        print("🚀 Running automated upload now...")
        print("========================================================")
        os.system("python3 /media/siddhartha/main_hard_disk1/personal/auto_gdrive_upload.py")
    else:
        print("❌ Invalid token format. Access tokens usually start with 'ya29.'")
except Exception as e:
        print(f"Error: {e}")
