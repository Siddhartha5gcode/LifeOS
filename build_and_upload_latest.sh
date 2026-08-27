#!/bin/bash
# ===================================================================
# LifeOS Mobile - Single File In-Place Build & Google Drive Overwrite
# Account: siddhartha12495@gmail.com
# Folder:  https://drive.google.com/drive/u/1/folders/1x9imE7jmbDwNUH94lF54Xc0MMDl1wqbJ
# ===================================================================

PROJECT_DIR="/media/siddhartha/main_hard_disk1/personal"
cd "$PROJECT_DIR" || exit 1

echo "========================================================"
echo "⚡ Step 1: Building LifeOS Android APK..."
echo "========================================================"

if [ -d "/home/siddhartha/.gradle" ]; then
    ANDROID_HOME=/usr/lib/android-sdk ./gradlew assembleDebug &> /dev/null
fi

APK_PATH="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "❌ Error: APK file missing at $APK_PATH"
    exit 1
fi

SINGLE_RELEASE_APK="$PROJECT_DIR/LifeOS_app-debug.apk"
cp "$APK_PATH" "$SINGLE_RELEASE_APK" 2>/dev/null || true

# Delete older APK releases if more than 3 exist locally
APK_COUNT=$(ls -1 "$PROJECT_DIR"/LifeOS_*.apk 2>/dev/null | wc -l)
if [ "$APK_COUNT" -gt 3 ]; then
    echo "🧹 Found $APK_COUNT release files (>3). Deleting older releases to keep only last 3..."
    ls -t "$PROJECT_DIR"/LifeOS_*.apk 2>/dev/null | tail -n +4 | xargs -r rm -f 2>/dev/null || true
fi

echo ""
echo "========================================================"
echo "✅ BUILD COMPLETE: $SINGLE_RELEASE_APK"
echo "========================================================"
echo "⚡ Step 2: Overwriting Single File on Google Drive..."
echo "========================================================"

python3 "$PROJECT_DIR/auto_gdrive_upload.py" "$@"
