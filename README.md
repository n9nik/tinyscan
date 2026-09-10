# TinyScan - Document Scanner

Offline document scanner: point the camera at a document, auto-detect edges, drag corners to adjust, enhance (color / grayscale / B&W), and export multi-page PDFs. **No sign-in, no cloud upload, no watermark on free PDFs** — the key differentiator vs Adobe Scan and CamScanner.

- **Package:** `com.n9nik.documentscanner`
- **Tech:** Kotlin, Jetpack Compose, Material 3, CameraX, Android PdfDocument
- **Ads:** Google Mobile Ads banner (sample IDs for closed testing; real IDs before Production)
- **Permissions:** CAMERA + READ_MEDIA_IMAGES only (+ INTERNET for ads)

## Build

Cloud builds via GitHub Actions (`.github/workflows/android-cloud-build.yml`):
push to `main` → debug APK + signed release AAB artifacts.

Release signing is env-var based (`UPLOAD_KEYSTORE_PATH`, `UPLOAD_KEYSTORE_PASSWORD`,
`UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD` from GitHub Secrets). Minification is
intentionally OFF (TinyPic 1.0.0 with R8 crashed on a real device).

See `BUILD_NOTES.md` for the full build story.
