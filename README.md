# Auto LoudSpeaker

A minimalistic Android app for elderly users that automatically switches incoming calls to loudspeaker/hands-free mode.

## Features
- Single ON/OFF toggle
- Automatically enables loudspeaker when a call is answered
- Stays active until manually disabled or permissions revoked
- Clean, welcoming green UI designed for elderly users

## Requirements
- Android 8.0+ (API 26+)
- ANSWER_PHONE_CALLS permission (Android 8+)
- MODIFY_AUDIO_SETTINGS permission
- READ_PHONE_STATE permission

## Build Instructions
1. Clone this repository
2. Open in Android Studio (Hedgehog or newer)
3. Sync Gradle
4. Build & Run on device

> Note: The app uses a foreground service to stay alive and a BroadcastReceiver for call state changes.
