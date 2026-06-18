# Contributing

Thanks for your interest in PitchLab.

## Development Setup

1. Install JDK 17 or newer.
2. Install Android Studio and Android SDK 36.
3. Clone the repository.
4. Run:

```bash
./gradlew :shared:jvmTest :androidApp:assembleDebug
```

On Windows:

```powershell
.\gradlew.bat :shared:jvmTest :androidApp:assembleDebug
```

## Pull Requests

- Keep changes focused and small.
- Add tests for pitch math, classification, or detection changes.
- Do not commit generated build outputs.
- Do not commit local SDK paths or secrets.
- Explain UI changes with screenshots when possible.

## Code Style

- Follow the existing Kotlin and Compose style.
- Keep shared logic in `shared/src/commonMain` when possible.
- Use platform `actual` implementations only for platform APIs such as microphone, storage, locale, or back handling.

## Reporting Bugs

Please include:

- Platform and device.
- App language.
- Steps to reproduce.
- Expected behavior.
- Actual behavior.
- Screenshots or short screen recordings when useful.
