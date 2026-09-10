# Privacy Policy for TinyScan - Document Scanner

**Effective date:** September 10, 2026

**Developer:** n9nik (Nikhil Suresh) - Contact: nikhil.suresh@gmail.com

TinyScan - Document Scanner was built as an offline-first utility. No account required.

## Core principle: offline, no cloud

Your documents are processed **entirely on your device**. TinyScan does not upload your documents to any server, does not require a login, and does not add a watermark.

- **Camera and image access:** We use the camera and Android's system photo picker to capture or choose document photos. We only read the image you explicitly select, process them locally using on-device image processing, and write the scanned PDF to your chosen location (Pictures/TinyScan or cache). We do not access other files.
- **No server-side storage:** We have no servers that receive your images. Compressed output stays on your device.
- **EXIF / orientation:** We read EXIF orientation locally only to display the photo correctly. We do not collect GPS or camera metadata. Future versions may offer optional "strip metadata" before save — that will also be local.

## Ads and consent

This is an ad-supported app using Google Mobile Ads and Google User Messaging Platform (UMP) for consent where required.

Google and its partners may collect:
- Device identifiers (including advertising ID where available, subject to your device settings)
- IP address, coarse/approximate location
- App diagnostics, ad interactions
- This is governed by Google's policies, not ours: https://policies.google.com/privacy and https://support.google.com/admob/answer/6128543

**Consent:** Where required by law (GDPR, US state laws), the app shows Google's consent message before requesting ads. A "Privacy choices" button appears in the app when UMP requires it.

**No personal data collection by us:** We do not maintain a user account, profile, or server-side database of your images or personal info.

## Permissions

- `INTERNET` / `ACCESS_NETWORK_STATE` — only to load ads and check consent. Core compression works in airplane mode.
- `READ_MEDIA_IMAGES` / `READ_MEDIA_VISUAL_USER_SELECTED` (Android 13+/14+) or legacy storage via system picker — to read the image you picked.
- `WRITE` via MediaStore — to save compressed output to Pictures. No `MANAGE_ALL_FILES` request.
- `AD_ID` — for ads personalization, respects your device's "Delete advertising ID" setting.

We intentionally avoid `READ_CONTACTS`, `CAMERA`, `LOCATION`, and `MANAGE_ALL_FILES`.

## Data retention and deletion

Since we have no account and no server copy of your images, there is nothing to delete on our side. If you uninstall the app, cached files in the app's private cache are removed by Android. Images you saved to Pictures/TinyScan remain until you delete them via your gallery/files app.

Ad data retention is governed by Google.

## Children

TinyScan is not directed to children under 13. It is a general-purpose productivity tool for students, photographers, and general users. We do not knowingly collect data from children.

## Security

All processing is local. We use Android's standard BitmapFactory with sampling to avoid OOM on large images, and standard MediaStore APIs for saving.

## Changes

If we add batch mode, ZIP export, or optional EXIF strip in updates, this policy will be updated and the effective date changed. We will continue to remain offline-first.

## Contact

Email: nikhil.suresh@gmail.com

---
*This policy is a starting point and not legal advice. Final publication should be reviewed against actual SDKs and Play Console Data Safety answers.*
