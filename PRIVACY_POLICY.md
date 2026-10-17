# Privacy Policy — AutoSubtitle

Last updated: 17 October 2026

## What the app does
AutoSubtitle lets users select audio/video files, create subtitles, preview them, and export SRT or MP4 files. Most subtitle editing and export features run locally on the device.

## Optional transcription
Automatic transcription is optional. Only after the user selects a file, enters an API key, and taps the transcription button, the app sends the selected audio to OpenAI's transcription API over HTTPS. For a video, the app extracts its audio track and sends that audio for transcription. OpenAI processes the submitted content under its applicable terms and privacy policies.

## API key
The API key is entered by the user, held in app memory for the request, and is not intentionally written to persistent app storage. The input field is cleared when the request starts. The user is responsible for their API key and any charges associated with its use.

## Local files and permissions
The app requests internet access for optional transcription. It uses Android's system document picker to let the user choose files; it does not request broad storage access. Temporary audio extraction files are deleted when transcription finishes or fails. Exported files are saved only to a destination chosen by the user.

## Collection, advertising, and sharing
The current MVP does not intentionally collect account details, location, contacts, advertising identifiers, or analytics. It does not include advertising SDKs. The selected audio is shared with OpenAI only when the user explicitly starts transcription.

## Security and retention
Network transcription requests use HTTPS. Do not submit confidential or third-party recordings unless you have the necessary permission. OpenAI's handling and retention of submitted audio are governed by OpenAI's own current policies and settings.

## Children and contact
Before public release, the developer must add a monitored privacy contact email and confirm any child-directed use. Replace this paragraph with the responsible developer's contact details before publishing.

## Changes
This policy may be updated when app functionality changes. The version published alongside the app should match the actual app behavior and Google Play Data safety declarations.
