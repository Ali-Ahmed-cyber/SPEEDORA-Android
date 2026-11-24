# SPEEDORA Two-Device Physical QA Checklist

> Note: Physical hardware tests must be executed on two real Android devices before marking as verified.

1. **Android Versions & OEMs**
   - [ ] Device A: Android 13/14/15 (e.g., Pixel / Samsung One UI)
   - [ ] Device B: Android 8.0–12 (e.g., Xiaomi MIUI / Oppo ColorOS / mid-range device)
2. **Permissions & Scoped Storage**
   - [ ] Deny media permission once -> confirm Arabic explanation displays cleanly without crash.
   - [ ] Grant media permissions -> verify real thumbnails load smoothly across 1,000+ photos/videos.
   - [ ] Export a non-split user APK and verify Split APKs are honestly marked non-exportable.
3. **Wireless Discovery & QR Pairing**
   - [ ] Wi-Fi Direct discovery between Device A and Device B.
   - [ ] Local Wi-Fi NSD (`_speedora._tcp.`) discovery on same router/hotspot without internet.
   - [ ] Scan temporary QR code via CameraX; confirm expired QR (>5 min) is rejected.
   - [ ] Verify 6-digit SAS code matches identically on both screens before accepting.
4. **Transfer Reliability & Edge Cases**
   - [ ] Transfer a large file (>1 GB video) and verify low RAM usage and accurate live MB/s.
   - [ ] Transfer multiple files across Photos + Documents + APKs in a single session.
   - [ ] Test Pause -> Resume and Cancel mid-transfer.
   - [ ] Test Receiver Rejection -> confirm zero partial files committed.
   - [ ] Test Battery-Saving mode & Reduced Motion toggle.
   - [ ] Verify SHA-256 integrity status and Room history entry after completion.
