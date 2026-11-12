# SimplTvOS<img width="447" height="447" alt="somebody took my fucking bananas" src="https://github.com/user-attachments/assets/e6a1912d-8c66-433a-bd5e-3b5f57c9c606" />


A minimal, privacy-first Android TV shell access OS (no root required/adb) and wallpaper manager. No ads, no tracking, no internet permission — just a clean Home screen for your TV.

![License](https://img.shields.io/badge/license-MIT-green)
![Android](https://img.shields.io/badge/Android_TV-8.0%2B-brightgreen)
![APK Size](https://img.shields.io/badge/Launcher_APK-%3C576KB-blue)

---

## ✨ Features

### 🏠 Launcher
- **Clean dock-style Home screen** — your apps front and centre, no clutter
- **Wallpaper rotation** — wallpapers change as you navigate the dock (random or sequential order)
- **Clock, date & greeting** on the Home screen
- **Inputs pill** — quick access to HDMI/AV sources
- **Wi-Fi, Bluetooth & Settings** shortcuts in the status bar
- **Gamepad passthrough, accessibility helpers** built in
- **No INTERNET permission** — zero network access, zero telemetry, zero ads
- **Tiny APK** — under 576 KB, runs smooth on 1 GB RAM TVs

### 🖼️ Wallpaper Manager
- **Gallery view** of all wallpapers with Active/Inactive status
- **Fullscreen preview** with Left/Right navigation
- **Select All / Unselect All** for batch operations
- **Random or Sequential** wallpaper switching mode
- **Folders & Files browser** — navigate your TV storage, USB drives, Downloads
- **Copy, Move, Paste, Delete** file operations
- **Wi-Fi Receive** — upload images from your phone/laptop via a web page with QR code
- **Quick Share** — share wallpapers to nearby devices
- **Add to Wallpapers** — one click to add any image from any folder

---

## 📥 Installation

### One-Click Download

Go to the [**Releases**](https://github.com/richardtharakan/SimplTvOS/releases/latest) page and download:
- **`SimplTvOS-Launcher.apk`** — the Home launcher
- **`SimplTvOS-WallpaperManager.apk`** — the wallpaper manager companion app

### Install via ADB

1. **Enable USB/Wireless Debugging** on your Android TV:
   - Settings → About → tap **Build Number** 7 times to enable Developer Options
   - Settings → Developer Options → turn on **USB Debugging** (or **Wireless Debugging** / **Network Debugging**)

2. **Connect via ADB** from your computer:
   ```bash
   adb connect <your-tv-ip>:5555
   ```

3. **Install the APKs**:
   ```bash
   adb install SimplTvOS-Launcher.apk
   adb install SimplTvOS-WallpaperManager.apk
   ```

4. **Set as Home launcher**:
   - Press the Home button on your remote
   - Select **SimplTvOS** and choose "Always"

5. **Grant storage permissions** (needed to read wallpapers):
   ```bash
   adb shell appops set com.gathin.tvhome MANAGE_EXTERNAL_STORAGE allow
   adb shell appops set com.gathin.wallpapermanager MANAGE_EXTERNAL_STORAGE allow
   ```

### Adding Wallpapers

Place your wallpaper images (`.jpg`, `.jpeg`, `.png`, `.webp`) in:
```
/sdcard/Pictures/Wallpapers/
```

Or use the **Wi-Fi Receive** feature in the Wallpaper Manager to upload images from your phone or laptop — it shows a QR code you can scan.

---

## 🔧 Build from Source

Requires **JDK 17** and **Android SDK** (platform 34, build-tools 34.0.0).

### Launcher
```bash
cd launcher
./gradlew --no-daemon assembleRelease
# APK at: app/build/outputs/apk/release/app-release.apk
```

### Wallpaper Manager
```bash
cd wallpaper-manager
./gradlew --no-daemon assembleRelease
# APK at: app/build/outputs/apk/release/app-release.apk
```

---

## 📁 Project Structure

```
SimplTvOS/
├── launcher/                    # The Home launcher app
│   ├── app/src/main/
│   │   ├── java/.../tvhome/     # Java source code
│   │   ├── res/                 # Layouts, drawables, values
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── wallpaper-manager/           # Wallpaper Manager companion app
│   ├── app/src/main/
│   │   ├── java/.../wallpapermanager/
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── .github/workflows/build.yml  # Auto-build APKs on release
├── LICENSE                      # MIT License
└── README.md
```

---

## 📱 Compatibility

| Requirement | Value |
|---|---|
| **Android version** | Android TV 8.0+ (API 26+) |
| **Target SDK** | 34 (Android 14) |
| **Tested on** | Realme TV (Android 11), Redmi TV |
| **RAM** | Works on 1 GB+ devices |
| **Launcher APK size** | < 576 KB |

---

## 🔒 Privacy

- The launcher has **no INTERNET permission** — it cannot connect to any server, ever
- **No analytics, no telemetry, no ads, no tracking**
- No background services or polling
- Wallpaper Manager uses a **local-only Wi-Fi server** (LAN only, no internet) for the Receive feature — it stops when the app closes
- All data stays on your TV

---

## 🤝 Contributing

Contributions are welcome! Feel free to open issues, submit pull requests, or suggest features.

1. Fork the repository
2. Create your feature branch: `git checkout -b feature/my-feature`
3. Commit your changes: `git commit -m 'Add my feature'`
4. Push to the branch: `git push origin feature/my-feature`
5. Open a Pull Request

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---
