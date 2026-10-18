# Privacy Policy — AutoSubtitle

Last updated: 17 October 2026

## What the app does
AutoSubtitle lets users select audio/video files, create subtitles, preview them, and export SRT or MP4 files. Most subtitle editing and export features run locally on the device.

## Automatic transcription
Automatic transcription uses the local Whisper engine on the device. The app downloads the multilingual Whisper base model over HTTPS the first time the feature is used (approximately 142 MB). After the model is downloaded, transcription can run without an internet connection. The selected audio/video is not uploaded to OpenAI or another transcription service.

## Model download
The model download is used only to install the speech-recognition model required for local processing. The app does not require an API key, subscription, or transcription server. Download availability depends on the model host and the user's connection.

## Local files and permissions
The app requests internet access only to download the speech-recognition model on first use. It uses Android's system document picker to let the user choose files; it does not request broad storage access. Temporary audio extraction files are deleted when transcription finishes or fails. Exported files are saved only to a destination chosen by the user.

## Collection, advertising, and sharing
The current MVP does not intentionally collect account details, location, contacts, advertising identifiers, or analytics. It does not include advertising SDKs. The app does not intentionally share selected audio/video files with third parties.

## Security and retention
The model is downloaded over HTTPS. Selected media is processed locally. Temporary working files are deleted after processing; exported files are saved only to a destination chosen by the user.

## Children and contact
Before public release, the developer must add a monitored privacy contact email and confirm any child-directed use. Replace this paragraph with the responsible developer's contact details before publishing.

## Changes
This policy may be updated when app functionality changes. The version published alongside the app should match the actual app behavior and Google Play Data safety declarations.
