# Auto LoudSpeaker

A minimalistic Android app for elderly users that automatically switches incoming calls to loudspeaker/hands-free mode.

## How It Works

This app uses the **`InCallService` API** — the same API used by Google Phone and Truecaller — to intercept every active call and route audio to the built-in speaker via `setAudioRoute(ROUTE_SPEAKER)`. This is the only reliable method on Android 10+ for a non-dialer app to control call audio routing.

## First-Time Setup (IMPORTANT)

When you enable the toggle for the first time, the app will ask for two things:
1. **Phone permissions** — grant all
2. **"Allow Auto LoudSpeaker to see and control your calls?"** — this is the `CALL_COMPANION` role. **You MUST tap Allow.** Without this, the InCallService is never bound by Android Telecom and the speaker will not activate.

## Features
- Single ON/OFF toggle
- Automatically enables loudspeaker when a call is answered
- Stays active until manually disabled or permissions revoked
- Clean, welcoming green UI designed for elderly users
- Works on Android 8.0+ (API 26+), correctly targets Android 14 (API 34)

## Build Instructions
1. Clone this repository
2. Open in Android Studio (Hedgehog or newer)
3. Sync Gradle → Build → Run on a **physical device** (emulators don’t handle phone calls)
4. Enable toggle → grant permissions → grant Call Companion role

## Requirements
- Android 8.0+ (API 26+)
- `READ_PHONE_STATE` permission
- `ANSWER_PHONE_CALLS` permission  
- `CALL_COMPANION` role (requested automatically on first enable)
