# 📱 LifeOS Android App - Installation Guide

This document contains step-by-step instructions on how to install and run your **LifeOS Personal Assistant App** on your Android mobile phone.

---

## ⚡ Method 1: Instant Install via Mobile Browser (Fastest - No USB Cable Needed)

You can run the web server on your computer and open/install the app directly onto your Android phone over Wi-Fi.

### Step 1: Start the Local Server on your PC
Open your Linux terminal on your computer and run:
```bash
cd /media/siddhartha/main_hard_disk1/personal
python3 -m http.server 8085
```

### Step 2: Connect Phone & PC to the Same Wi-Fi
Make sure your Android phone and PC are connected to the same Wi-Fi network.

### Step 3: Open on Your Phone
Open **Google Chrome** or **Firefox** on your Android phone and visit:
```text
http://172.24.98.31:8085
```

### Step 4: Add to Home Screen (Installs like a Native App)
1. In Chrome, tap the **3 dots (⋮)** menu at the top right.
2. Tap **"Add to Home screen"** or **"Install App"**.
3. A **LifeOS ⚡** icon will now appear on your mobile home screen and launch in full-screen mode like a native Android app!

---

## 🛠️ Method 2: Compile & Install Native `.apk` Package

If you want a standalone `.apk` file that installs directly without needing a running computer:

### Step 1: Generate the `.apk` File
Open a terminal on your computer and build the project:
```bash
cd /media/siddhartha/main_hard_disk1/personal
gradle assembleDebug
```
*(Or open the `/media/siddhartha/main_hard_disk1/personal` project folder in **Android Studio** and click **Build → Build APK**).*

The generated file will be located at:
`/media/siddhartha/main_hard_disk1/personal/app/build/outputs/apk/debug/app-debug.apk`

### Step 2: Transfer APK to Your Phone
* **Via USB:** Plug your Android phone into your PC, copy `app-debug.apk` to your phone's **Downloads** folder.
* **Via Local Web Download:** Run `python3 -m http.server 8085` on your PC, then open `http://<your-pc-ip>:8085/app/build/outputs/apk/debug/app-debug.apk` on your phone to download it.

### Step 3: Install on Android
1. Open the **Files / Downloads** app on your Android phone.
2. Tap `app-debug.apk`.
3. If prompted **"For your security, your phone is not allowed to install unknown apps"**:
   - Tap **Settings**.
   - Enable **"Allow from this source"**.
4. Tap **Install** and open **LifeOS Personal App**!

---

## 🔒 Offline Capability & Data Privacy
- **100% Private:** All your financial data, habits, notes, and reminders are saved **locally on your device (`localStorage`)**.
- **Works Offline:** No internet connection required after opening/installing!
