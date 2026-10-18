# AutoSubtitle

An Android subtitle app with a Persian-first interface.

## Current features

- Pick an audio or video file from Android storage.
- Create and edit subtitle cues manually.
- Export UTF-8 SubRip (SRT) subtitle files.
- Preview timed subtitles over video and jump playback to a subtitle cue.
- Choose from 11 Android system font families plus bundled Vazirmatn for consistent Persian/English typography, with size, color, outline, position, background, bold, and shadow controls.
- Choose subtitle entrance animation: fade, slide up, soft pop, or none; animations are applied both in preview and to burned-in MP4 output.
- Save subtitle appearance preferences between app launches.
- Export MP4 with subtitles burned into the video using AndroidX Media3 Transformer.
- Core subtitle editing, preview, SRT export, and MP4 subtitle rendering work locally without an API key or app server.
- No selected media is uploaded to a transcription service in this version.
- Automatic speech recognition using a bundled local model is not yet implemented; the app does not claim to transcribe speech automatically offline yet.

## Bundled font and license

- The Vazirmatn Regular font is downloaded from the official upstream repository at a pinned commit during CI builds and included in the APK for consistent Persian rendering.
- The font is distributed under the SIL Open Font License 1.1; its license notice is included in the app assets at `fonts/OFL.txt`.

## Offline-first operation

The app uses no online transcription API. It downloads the Whisper base model (about 142 MB) once over HTTPS on first use, then processes audio locally without uploading selected media. The user can choose Persian, English, or automatic language detection. Video audio is decoded locally to a temporary WAV file. Core editing, SRT export, and MP4 rendering remain on-device.

## Build and dependencies

- Gradle downloads the required AndroidX Media3 libraries and JUnit automatically during CI; no manual library installation on the phone is needed.
- GitHub Actions runs local unit tests, Android lint checks, and then builds the debug APK on pushes to the main branch.
- Open the repository's Actions tab and download the AutoSubtitle-debug artifact from a successful run.
- Subtitle timing lookup and SRT timestamp formatting have unit tests in app/src/test.
- The automatic pipeline produces a debug APK. A separate manual workflow can produce a signed release APK and AAB after private upload-key secrets are configured.

## Planned improvements

- Integrate and validate a local speech-recognition model for Persian and English without a network API.
- Improve export progress, cancellation, and compatibility for more video formats.


## Security and Google Play preparation

- The manifest disables Android app backup. Internet access is used only to download the speech model on first use; media files are not uploaded.
- The app targets Android 16 / API 36 for current Google Play submission requirements.
- An in-app privacy notice explains that media files are not uploaded to an online transcription provider.
- Review `PRIVACY_POLICY.md`, replace the contact placeholder, publish the policy at a stable public HTTPS URL, and use that URL in Play Console before release.
- The automatic build produces a **debug APK**; the manual release workflow produces a signed release bundle (AAB) once signing secrets are configured. Play App Signing and the Play Console Data safety form still require careful setup.

These steps reduce avoidable security and policy issues but do not guarantee Play Protect or Google Play approval.

## Signed release build (Google Play preparation)

The manual workflow `.github/workflows/release-android.yml` creates a signed release APK and an Android App Bundle (AAB). It will intentionally stop until the signing secrets are configured in **GitHub → Settings → Secrets and variables → Actions**:

- `ANDROID_KEYSTORE_BASE64`: Base64 text of your private upload keystore file.
- `ANDROID_KEYSTORE_PASSWORD`: Keystore password.
- `ANDROID_KEY_ALIAS`: Alias of the upload key.
- `ANDROID_KEY_PASSWORD`: Password for that key.

**Never commit the keystore or passwords to this repository, and never paste them into chat or issues.** Keep a secure offline backup of the keystore and its passwords. Losing the upload key can complicate future updates. Trigger **Actions → Build Signed Android Release → Run workflow** only after all four secrets are set. Download the `AutoSubtitle-signed-release` artifact from a successful run; it contains the APK and AAB but not the private keystore.

This workflow only produces signed files; it does not make the app automatically approved or publish it. Before Play Console submission, replace the contact placeholder in `PRIVACY_POLICY.md`, publish the policy at a stable public HTTPS URL, verify the app's behavior and target audience, complete the Data safety declaration accurately, test the signed release on real devices, and configure Play App Signing in Play Console. The current app is still an MVP and must be reviewed for privacy, reliability, and policy compliance before public release.
