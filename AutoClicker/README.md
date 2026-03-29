# AutoClicker for Android

An intelligent auto-clicker that uses **image recognition** (OpenCV) to find a target button/element on screen and tap it automatically. After each click it detects whether a new page has appeared using **pixel diff**, then continues the loop.

---

## Features
- 📸 Pick any image from your gallery as the click target
- 🔍 OpenCV template matching (multi-scale) to find it on screen
- 🔄 Page-change detection via pixel diff — waits for new page before next click
- ⚙️ Configurable: match threshold, click interval, max clicks
- 🔔 Runs as a foreground service (works while you use other apps)
- No root required — uses Android Accessibility Service

---

## How to get the APK

### Option A — GitHub Actions (easiest, free)

1. Create a free GitHub account at https://github.com
2. Click **New repository** → name it `AutoClicker` → **Create repository**
3. Upload all files from this ZIP (drag & drop in the browser, or use Git)
4. Go to the **Actions** tab → select **Build AutoClicker APK** → **Run workflow**
5. Wait ~5 minutes for the build to finish
6. Click the completed workflow run → scroll to **Artifacts** → download `AutoClicker-debug.zip`
7. Extract it — you have `app-debug.apk`

### Option B — Android Studio (local build)

1. Install Android Studio: https://developer.android.com/studio
2. Open Android Studio → **Open** → select this project folder
3. Wait for Gradle sync to finish (~2 min first time)
4. Menu: **Build → Build Bundle(s) / APK(s) → Build APK(s)**
5. APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Option C — Command line (if you have JDK 17+)

```bash
cd AutoClicker
chmod +x gradlew
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

---

## Install on your Android phone

1. Transfer `app-debug.apk` to your phone (email, USB, Google Drive, etc.)
2. On your phone: **Settings → Security → Install unknown apps** → allow your browser/file manager
3. Tap the APK file → **Install**

---

## First-time setup (required permissions)

The app will show yellow banners if permissions are missing:

### 1. Accessibility Service
- Tap the yellow banner **OR** go to:
  `Settings → Accessibility → AutoClicker → Enable`
- This allows the app to simulate taps

### 2. Overlay Permission
- Tap the yellow banner **OR** go to:
  `Settings → Apps → AutoClicker → Display over other apps → Allow`

### 3. Screen Capture (asked automatically when you press Start)
- A system dialog will ask "Allow AutoClicker to capture your screen?" → tap **Start Now**

---

## How to use

1. Open the app
2. Tap **Pick Target Image** → select a screenshot of the button/element you want clicked
3. Adjust settings:
   - **Match Threshold** — how similar the screen must look to your image (0.80 is good)
   - **Click Interval** — how long to wait after each click before checking for a new page
   - **Max Clicks** — safety limit
4. Tap **▶ Start**
5. Switch to the app you want to automate — the clicker runs in the background
6. Tap **■ Stop** in the notification or back in the app when done

---

## Architecture

```
UI (MainActivity)
    ↓
Auto-click controller (ScreenCaptureService)
    ├── Screen capturer   → MediaProjection API
    ├── Image matcher     → OpenCV TM_CCOEFF_NORMED
    ├── Page detector     → pixel diff (absdiff + threshold)
    └── Tap dispatcher    → AutoClickAccessibilityService
```

---

## Troubleshooting

| Problem | Fix |
|---|---|
| "Match not found" | Lower the match threshold, or pick a cleaner target image |
| Taps land in wrong place | Make sure your target image was captured on the same device |
| App crashes on start | Make sure both permissions are granted before pressing Start |
| Page change not detected | Increase click interval so the page has time to load |

---

## Requirements
- Android 8.0 (API 26) or higher
- Works on ARM64, ARMv7, x86, x86_64 devices
