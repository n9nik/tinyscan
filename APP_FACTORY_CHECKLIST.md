# Clone-to-launch checklist

## 1. Clone

```bash
python3 tools/clone_app.py \
  --name "Your App Name" \
  --package com.n9nik.yourapp \
  --out ../your-app
```

## 2. Replace the demo utility

- Put the app's one core transformation in `domain/UtilityLogic.kt`.
- Replace the input/result screen in `ui/UtilityApp.kt` only as much as the job requires.
- Keep the core feature useful without an account.
- Add unit tests for the actual utility behavior.

## 3. Brand

- Set app name in `res/values/strings.xml`.
- Replace `res/drawable/ic_launcher.xml` with the final adaptive icon assets.
- Update colors and store screenshots.
- Keep screenshots truthful; do not depict features the build does not contain.

## 4. Ads and privacy

- Create the app and banner unit in AdMob.
- Put `ADMOB_APP_ID` and `ADMOB_BANNER_ID` in `~/.gradle/gradle.properties`, not the repository.
- Configure GDPR/US-state messages in AdMob Privacy & messaging.
- Host a completed privacy policy and add its URL in Play Console.
- Complete Play Data safety based on the final SDKs and behavior.
- Keep test IDs in debug. Never click your own live ads.

## 5. Quality gate

```bash
python3 tools/preflight.py
./gradlew test lint assembleDebug
```

On a physical Android device, verify cold start, rotation, dark mode, airplane mode, consent flow, privacy choices, ad loading, and the full utility task. Fix every crash and blocker before closed testing.

## 6. Release

```bash
./gradlew bundleRelease \
  -PADMOB_APP_ID=ca-app-pub-...~... \
  -PADMOB_BANNER_ID=ca-app-pub-.../...
```

Use Play App Signing. Upload the generated AAB, complete content declarations, run the required test, collect real feedback, and only then request production access.
