#!/usr/bin/env python3
import os
import sys
import json
import urllib.request

# Google Drive Direct Upload Helper
# Target Account: siddhartha12495@gmail.com
# Target Folder:  1x9imE7jmbDwNUH94lF54Xc0MMDl1wqbJ (apk)

APK_PATH = "/media/siddhartha/main_hard_disk1/personal/app/build/outputs/apk/debug/app-debug.apk"
FOLDER_ID = "1x9imE7jmbDwNUH94lF54Xc0MMDl1wqbJ"

def run_upload(token=None):
    if not os.path.exists(APK_PATH):
        print("========================================================")
        print("❌ Error: Compiled APK not found at:")
        print(f"   {APK_PATH}")
        print("   Please run ./build_apk.sh first!")
        print("========================================================")
        return

    file_size = os.path.getsize(APK_PATH) / (1024 * 1024)

    if not token or token.strip() == "":
        print("========================================================")
        print("📦 LIFEOS APK PACKAGE IS READY:")
        print("========================================================")
        print(f"📱 File Location: {APK_PATH} ({file_size:.2f} MB)")
        print("🎯 Target Folder: https://drive.google.com/drive/u/1/folders/1x9imE7jmbDwNUH94lF54Xc0MMDl1wqbJ")
        print("========================================================")
        print("💡 QUICK 1-STEP UPLOAD:")
        print("1. In your open Google Drive tab, click '+ New' -> 'File upload'")
        print(f"2. Select file: {APK_PATH}")
        print("========================================================")
        return

    # If token is passed
    print(f"⚡ Uploading APK ({file_size:.2f} MB) directly to Google Drive...")
    file_name = "LifeOS_v1.1_app-debug.apk"
    metadata = {"name": file_name, "parents": [FOLDER_ID]}

    try:
        req = urllib.request.Request(
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable",
            data=json.dumps(metadata).encode('utf-8'),
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json; charset=UTF-8",
                "X-Upload-Content-Type": "application/vnd.android.package-archive",
                "X-Upload-Content-Length": str(os.path.getsize(APK_PATH))
            },
            method="POST"
        )
        with urllib.request.urlopen(req) as resp:
            location = resp.headers.get("Location")
            
        with open(APK_PATH, "rb") as f:
            upload_req = urllib.request.Request(
                location,
                data=f.read(),
                headers={"Content-Type": "application/vnd.android.package-archive"},
                method="PUT"
            )
            with urllib.request.urlopen(upload_req) as upload_resp:
                res_data = json.loads(upload_resp.read().decode('utf-8'))
                print("========================================================")
                print("✅ DIRECT UPLOAD SUCCESSFUL!")
                print(f"📄 File ID: {res_data.get('id')}")
                print(f"🔗 Link: https://drive.google.com/file/d/{res_data.get('id')}/view")
                print("========================================================")
    except Exception as e:
        print(f"❌ Upload Error: {e}")

if __name__ == "__main__":
    user_token = sys.argv[1] if len(sys.argv) > 1 else None
    run_upload(user_token)
