# SubtitleYar 0.9.2 provider integration (development)

This branch adds an initial provider selector with two online workflows:

- **Speech-to-text API**: OpenAI-compatible multipart audio transcription endpoints. Presets currently provided for Groq and OpenAI.
- **Text subtitle processing**: OpenAI-compatible chat-completions endpoints. Presets currently provided for Groq, OpenAI, DeepSeek, Kimi/Moonshot, Qwen/DashScope, and GLM/Zhipu.

## Important compatibility notes

- Provider presets are starting values only. Model availability, account access, region-specific endpoints, and API pricing depend on the provider and can change.
- The text workflow sends subtitle text to a chat-completions endpoint and expects an OpenAI-style `choices[0].message.content` response containing JSON segments. It is not a universal adapter for every vendor.
- Claude/Anthropic and Gemini use different API formats and are **not yet directly implemented** in this branch.
- The speech-to-text workflow currently accepts files up to 24 MiB and does not split longer/larger files automatically.
- Online mode sends the selected audio (or extracted audio from video) to the configured provider. Use only providers you trust.
- The API key is not persisted by the app; it must be entered again after restarting the app. Do not hardcode keys into source code or commit them to GitHub.
- Do not treat this branch as release-ready until CI builds successfully and the app is tested on a real Android device.

## Build

The GitHub Actions workflow `.github/workflows/subtitleryar-build.yml` builds a debug APK and uploads it as an artifact when the workflow runs successfully.
