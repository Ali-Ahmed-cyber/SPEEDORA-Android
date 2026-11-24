# SPEEDORA — منصة نقل الملفات اللاسلكية للأندرويد

**FASTER. SMARTER. CONNECTED.** · **نقل أسرع. اتصال أذكى.**

Package ID: `com.speedora.app`  
Minimum SDK: `26` (Android 8.0) · Target/Compile SDK: `35` (Android 15) · JDK: `17`

## Architecture Overview
- **UI & Design System (`core/designsystem`, `ui/`)**: Jetpack Compose + Material 3 with SPEEDORA Pure White (`#FFFFFF`), Royal Blue (`#1769D2`), Deep Blue (`#104FA8`), and Midnight Navy (`#102B50`). Full Arabic RTL primary localization and English LTR support.
- **Cryptography & Safety (`core/security`)**: Ephemeral ECDH P-256 (`secp256r1`) key agreement, RFC 5869 HKDF-SHA256 key derivation, AES-256-GCM authenticated chunk encryption with strictly non-repeating 96-bit nonces and AAD framing, 6-digit Short Authentication String (SAS), streaming SHA-256 file integrity verification, and canonical path confinement in `FilenameSanitizer`.
- **Real File Discovery (`data/files`)**: `MediaStore` (Photos, Videos, Music, Downloads, Recent), Storage Access Framework (`DocumentFile`) for Documents/Archives/Folders without `MANAGE_EXTERNAL_STORAGE`, `PackageManager` for installed apps (honestly flagging Split APKs and system protections), and optional user-consented `ContactsContract` vCard 3.0 export.
- **Wireless Discovery & Transport (`data/network`, `transfer/`)**: `WifiP2pManager` (Wi-Fi Direct), `NsdManager` (`_speedora._tcp.` mDNS), `BluetoothFallbackManager` (RFCOMM fallback for small transfers/pairing assistance), Manual IP/Port fallback, CameraX + ZXing offline temporary QR pairing (5-minute TTL, zero permanent secrets), and `TransferForegroundService` for reliable background transfers.
- **Local Persistence (`data/local`)**: Room (`TransferHistoryEntity`, `TrustedDeviceEntity`, `FavoriteFileEntity`) and Jetpack DataStore Preferences. Zero external cloud servers required.

## Building in Android Studio or CLI
1. Open the `/android` directory in **Android Studio Ladybug / Meerkat** with **JDK 17**.
2. Sync Gradle and run:
   ```bash
   gradle testDebugUnitTest
   gradle assembleDebug
   ```
3. Or push to GitHub to run `.github/workflows/android-build.yml`, which sets up JDK 17 + Android SDK, runs all unit tests, builds `app-debug.apk`, and uploads it as a downloadable workflow artifact.
