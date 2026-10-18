# AutoSubtitle

An Android subtitle app with a Persian-first interface.

## Current features

- Pick an audio or video file from Android storage.
- Create and edit subtitle cues manually.
- Export UTF-8 SubRip (SRT) subtitle files.
- Preview timed subtitles over video and jump playback to a subtitle cue.
- Customize subtitle font family, size, text color, outline color and thickness, vertical position, background color and opacity, bold style, and shadow.
- Save subtitle appearance preferences between app launches.
- Export MP4 with subtitles burned into the video using AndroidX Media3 Transformer.
- Optional automatic transcription of selected audio files through the OpenAI Whisper transcription API.
- Extract the audio track from selected videos (when the device can demux the format), then transcribe it through Whisper.

## Automatic transcription setup

1. Open the app and select an audio or video file.
2. Enter your own OpenAI API key in the password field.
3. Tap the automatic transcription button and wait for the timestamped segments.
4. Export the result as an SRT file.

Important:
- Automatic transcription requires an internet connection and a valid API key.
- API usage may incur charges. Check current pricing and account availability before use.
- The API key is only held in the input field while the app is running; the app does not intentionally save it.
- Audio is sent to the transcription provider for processing. Do not upload sensitive recordings unless you have permission.
- Provider limits apply to file size, formats, and duration.
- This is a debug/MVP build, not a production-ready release.

## Build and dependencies

- Gradle downloads the required AndroidX Media3 libraries and JUnit automatically during CI; no manual library installation on the phone is needed.
- GitHub Actions runs local unit tests, Android lint checks, and then builds the debug APK on pushes to the main branch.
- Open the repository's Actions tab and download the AutoSubtitle-debug artifact from a successful run.
- Subtitle timing lookup and SRT timestamp formatting have unit tests in app/src/test.
- The current pipeline produces a debug APK only. Release signing is intentionally not configured until a private upload key is securely set up.

## Planned improvements

- Improve export progress, cancellation, and compatibility for more video formats.
- Expand subtitle styling options and add an export progress indicator.
- Improve upload progress, large-file handling, and error messages.


## Security and Google Play preparation

- The manifest disables Android app backup and cleartext (non-HTTPS) traffic.
- The app targets Android 16 / API 36 for current Google Play submission requirements.
- An in-app privacy notice explains the optional upload to OpenAI; the API-key field is cleared when a transcription request begins.
- Review `PRIVACY_POLICY.md`, replace the contact placeholder, publish the policy at a stable public HTTPS URL, and use that URL in Play Console before release.
- This repository currently builds a **debug APK**. A public Google Play release should be built as a signed release bundle (AAB), with Play App Signing configured, and the Play Console Data safety form must accurately match the app.

These steps reduce avoidable security and policy issues but do not guarantee Play Protect or Google Play approval.

## Signed release build (Google Play preparation)

The manual workflow `.github/workflows/release-android.yml` creates a signed release APK and an Android App Bundle (AAB). It will intentionally stop until the signing secrets are configured in **GitHub → Settings → Secrets and variables → Actions**:

- `ANDROID_KEYSTORE_BASE64`: Base64 text of your private upload keystore file.
- `ANDROID_KEYSTORE_PASSWORD`: Keystore password.
- `ANDROID_KEY_ALIAS`: Alias of the upload key.
- `ANDROID_KEY_PASSWORD`: Password for that key.

**Never commit the keystore or passwords to this repository, and never paste them into chat or issues.** Keep a secure offline backup of the keystore and its passwords. Losing the upload key can complicate future updates. Trigger **Actions → Build Signed Android Release → Run workflow** only after all four secrets are set. Download the `AutoSubtitle-signed-release` artifact from a successful run; it contains the APK and AAB but not the private keystore.

This workflow only produces signed files; it does not make the app automatically approved or publish it. Before Play Console submission, replace the contact placeholder in `PRIVACY_POLICY.md`, publish the policy at a stable public HTTPS URL, verify the app's behavior and target audience, complete the Data safety declaration accurately, test the signed release on real devices, and configure Play App Signing in Play Console. The current app is still an MVP and must be reviewed for privacy, reliability, and policy compliance before public release.
