# TinyScan - Document Scanner - Build Notes

**Built from:** TinyPic 1.0.1 codebase (green build proven via GitHub Actions run 33799933298)
**Date:** Sep 9-10 2026
**Package:** com.n9nik.documentscanner
**App Name:** TinyScan - Document Scanner

Follows `~/workspace/goals/play-store-app-factory/hidden_files/BUILD_PLAYBOOK.md` exactly.

## What changed from TinyPic

- Domain replaced: `QuadFinder.kt` (pure-Kotlin document edge detection: blur → Sobel →
  threshold → dilate → largest component → convex hull → quad; JVM unit-tested),
  `DocumentDetector.kt` (Bitmap wrapper), `DocumentProcessor.kt` (perspective crop via
  `Matrix.setPolyToPoly`, enhance filters via ColorMatrix, multi-page A4 PDF via
  `PdfDocument`, MediaStore save to Downloads/TinyScan, share).
- UI replaced: `ScannerApp.kt` — Camera screen (CameraX Preview + ImageCapture, gallery
  import), Adjust screen (draggable corner overlay on Canvas, Color/Grayscale/B&W chips,
  live cropped preview), Pages screen (thumbnail list, Save PDF / Share).
- Deps: added CameraX 1.3.4 (camera2, lifecycle, view) + lifecycle-runtime-compose;
  kept exifinterface (EXIF orientation on capture/import), play-services-ads, UMP.
- Manifest: CAMERA + READ_MEDIA_IMAGES permissions, `android:icon="@mipmap/ic_launcher"`.
- `compileSdk 36 / targetSdk 36 / minSdk 24`, minify OFF, env-var signing — per playbook.

## Icon

- 3 concepts generated via media skill (TinyPic family style: flat, clean, high contrast).
- Selected: white document with folded corner + indigo scan-corner brackets on deep indigo.
- Exported 512px, PIL-resized to mipmap mdpi 48 / hdpi 72 / xhdpi 96 / xxhdpi 144 / xxxhdpi 192
  (`ic_launcher.png`, `ic_launcher_round.png` circular, `ic_launcher_foreground.png`).

## Tests

`QuadFinderTest` (JVM): rotated rect detection (avg corner error ~4px), axis-aligned rect,
flat image → null, tiny speck → null, orderQuad correctness. All pass locally via kotlinc.
