# AutoSubtitle

An Android subtitle app with a Persian-first interface.

## Current features

- Pick an audio or video file from Android storage.
- Create and edit subtitle cues manually.
- Export UTF-8 SubRip (SRT) subtitle files.
- Optional automatic transcription of selected audio files through the OpenAI Whisper transcription API.
- Importing video directly for automatic transcription is not supported yet; select an audio file for the current transcription feature.

## Automatic transcription setup

1. Open the app and select an audio file.
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

- Extract audio from video files before transcription.
- Add subtitle preview, cue editing/deletion, and better RTL/Persian text handling.
- Add optional subtitle burn-in/export to MP4.
- Improve upload progress, large-file handling, and error messages.
