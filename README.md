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

## Build

GitHub Actions builds a debug APK on pushes to the main branch. Open the repository's Actions tab and download the APK artifact from a successful run.

## Planned improvements

- Improve export progress, cancellation, and compatibility for more video formats.
- Expand subtitle styling options and add an export progress indicator.
- Improve upload progress and large-file handling.
- Improve upload progress, large-file handling, and error messages.


## Security and Google Play preparation

- The manifest disables Android app backup and cleartext (non-HTTPS) traffic.
- The app targets Android 16 / API 36 for current Google Play submission requirements.
- An in-app privacy notice explains the optional upload to OpenAI; the API-key field is cleared when a transcription request begins.
- Review `PRIVACY_POLICY.md`, replace the contact placeholder, publish the policy at a stable public HTTPS URL, and use that URL in Play Console before release.
- This repository currently builds a **debug APK**. A public Google Play release should be built as a signed release bundle (AAB), with Play App Signing configured, and the Play Console Data safety form must accurately match the app.

These steps reduce avoidable security and policy issues but do not guarantee Play Protect or Google Play approval.
