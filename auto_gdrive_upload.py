#!/usr/bin/env python3
import os
import sys
import json
import urllib.request
import urllib.parse
import urllib.error

# LifeOS Single In-Place Google Drive Uploader (No Redundant Files)
# Account: siddhartha12495@gmail.com
# Target Folder ID: 1x9imE7jmbDwNUH94lF54Xc0MMDl1wqbJ

PROJECT_DIR = "/media/siddhartha/main_hard_disk1/personal"
APK_PATH = os.path.join(PROJECT_DIR, "app/build/outputs/apk/debug/app-debug.apk")
FOLDER_ID = "1x9imE7jmbDwNUH94lF54Xc0MMDl1wqbJ"
TOKEN_FILE = os.path.join(PROJECT_DIR, ".gdrive_token.json")
SINGLE_FILE_NAME = "LifeOS_app-debug.apk"

def load_tokens():
    if os.path.exists(TOKEN_FILE):
        try:
            with open(TOKEN_FILE, 'r') as f:
                return json.load(f)
        except Exception:
            pass
    return {}

def save_tokens(tokens):
    with open(TOKEN_FILE, 'w') as f:
        json.dump(tokens, f, indent=2)

def refresh_access_token(refresh_token, client_id=None, client_secret=None):
    print("🔄 Access token expired. Auto-refreshing Google OAuth token...")
    tokens = load_tokens()
    c_id = client_id or tokens.get("client_id") or "407408718192.apps.googleusercontent.com"
    c_secret = client_secret or tokens.get("client_secret")

    data = {
        "client_id": c_id,
        "refresh_token": refresh_token,
        "grant_type": "refresh_token"
    }
    if c_secret:
        data["client_secret"] = c_secret

    encoded_data = urllib.parse.urlencode(data).encode('utf-8')
    req = urllib.request.Request(
        "https://oauth2.googleapis.com/token",
        data=encoded_data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST"
    )
    try:
        with urllib.request.urlopen(req) as resp:
            res_data = json.loads(resp.read().decode('utf-8'))
            new_access_token = res_data.get("access_token")
            if new_access_token:
                tokens["access_token"] = new_access_token
                # Also update refresh_token if Google rotated it
                if res_data.get("refresh_token"):
                    tokens["refresh_token"] = res_data["refresh_token"]
                    print("🔄 Refresh token also rotated and saved.")
                save_tokens(tokens)
                print("✅ Access token refreshed and saved successfully!")
                return new_access_token
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='replace')
        print(f"❌ Token refresh failed: HTTP {e.code} {e.reason}")
        print(f"   Response body: {body}")
        print("\n========================================================")
        print("🔑 OAUTH TOKEN RENEWAL REQUIRED (15-Second Step):")
        print("1. Open Google OAuth Playground:")
        print("   https://developers.google.com/oauthplayground/")
        print("2. Select 'Drive API v3' -> 'https://www.googleapis.com/auth/drive.file'")
        print("3. Click 'Authorize APIs' -> Sign in as siddhartha12495@gmail.com")
        print("4. Click 'Exchange authorization code for tokens'")
        print("5. Copy BOTH access_token AND refresh_token into .gdrive_token.json")
        print("   OR run: python3 /media/siddhartha/main_hard_disk1/personal/save_token.py <NEW_ACCESS_TOKEN>")
        print("========================================================\n")
    except Exception as e:
        print(f"❌ Token refresh failed: {e}")
    return None


def find_existing_file_id(token):
    query = f"'{FOLDER_ID}' in parents and name = '{SINGLE_FILE_NAME}' and trashed = false"
    url = f"https://www.googleapis.com/drive/v3/files?q={urllib.parse.quote(query)}"
    req = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            files = data.get('files', [])
            if files:
                return files[0].get('id')
    except Exception as e:
        print(f"⚠️ Search for existing file: {e}")
    return None

def do_single_file_upload(token):
    file_size = os.path.getsize(APK_PATH)
    existing_id = find_existing_file_id(token)

    if existing_id:
        print(f"🔄 Overwriting existing file (ID: {existing_id}) on Google Drive...")
        upload_url = f"https://www.googleapis.com/upload/drive/v3/files/{existing_id}?uploadType=media"
        method = "PATCH"
        headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/vnd.android.package-archive",
            "Content-Length": str(file_size)
        }
        with open(APK_PATH, "rb") as f:
            req = urllib.request.Request(upload_url, data=f.read(), headers=headers, method=method)
            with urllib.request.urlopen(req) as resp:
                return json.loads(resp.read().decode('utf-8'))
    else:
        print("📄 Creating single primary APK file on Google Drive...")
        metadata = {"name": SINGLE_FILE_NAME, "parents": [FOLDER_ID]}
        init_req = urllib.request.Request(
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable",
            data=json.dumps(metadata).encode('utf-8'),
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json; charset=UTF-8",
                "X-Upload-Content-Type": "application/vnd.android.package-archive",
                "X-Upload-Content-Length": str(file_size)
            },
            method="POST"
        )
        with urllib.request.urlopen(init_req) as resp:
            resumable_url = resp.headers.get("Location")

        with open(APK_PATH, "rb") as f:
            upload_req = urllib.request.Request(
                resumable_url,
                data=f.read(),
                headers={"Content-Type": "application/vnd.android.package-archive"},
                method="PUT"
            )
            with urllib.request.urlopen(upload_req) as upload_resp:
                return json.loads(upload_resp.read().decode('utf-8'))

def upload_apk():
    if not os.path.exists(APK_PATH):
        print(f"❌ Error: APK file missing at {APK_PATH}")
        return False

    tokens = load_tokens()
    access_token = tokens.get("access_token")
    refresh_token = tokens.get("refresh_token")
    client_id = tokens.get("client_id")

    if not access_token:
        print("❌ No OAuth token found in .gdrive_token.json!")
        return False

    file_size = os.path.getsize(APK_PATH) / (1024 * 1024)

    print("========================================================")
    print("⚡ SINGLE IN-PLACE GOOGLE DRIVE OVERWRITE UPLOAD...")
    print(f"📦 File: {SINGLE_FILE_NAME} ({file_size:.2f} MB)")
    print(f"🎯 Target Drive Folder ID: {FOLDER_ID}")
    print("========================================================")

    try:
        res = do_single_file_upload(access_token)
        file_id = res.get('id')
        print("========================================================")
        print("🎉 SUCCESS: SINGLE FILE OVERWRITTEN ON GOOGLE DRIVE!")
        print("========================================================")
        print(f"📄 Google Drive File ID:   {file_id}")
        print(f"🔗 Direct Download Link:   https://drive.google.com/uc?export=download&id={file_id}")
        print(f"👁️ View Drive Link:       https://drive.google.com/file/d/{file_id}/view")
        print("========================================================")
        return True

    except urllib.error.HTTPError as e:
        if e.code in (401, 403) and refresh_token:
            new_token = refresh_access_token(refresh_token, client_id)
            if new_token:
                try:
                    res = do_single_file_upload(new_token)
                    file_id = res.get('id')
                    print("========================================================")
                    print("🎉 SUCCESS: SINGLE FILE OVERWRITTEN AFTER TOKEN REFRESH!")
                    print("========================================================")
                    print(f"📄 Google Drive File ID:   {file_id}")
                    print(f"🔗 Direct Download Link:   https://drive.google.com/uc?export=download&id={file_id}")
                    print("========================================================")
                    return True
                except Exception as ex:
                    print(f"❌ Overwrite failed after refresh: {ex}")
        print(f"❌ HTTP Error ({e.code}): {e.reason}")
        return False
    except Exception as e:
        print(f"❌ Upload Failed: {e}")
        return False

if __name__ == "__main__":
    upload_apk()
