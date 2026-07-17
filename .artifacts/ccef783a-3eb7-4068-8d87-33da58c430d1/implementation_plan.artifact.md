# Implementation Plan - Fix Speakerphone Activation during Calls

The app currently fails to enable the loudspeaker during a call, even though the service is active. This is likely due to Android 14's strict audio routing restrictions for non-dialer apps and the deprecation of the `isSpeakerphoneOn` API.

## User Review Required

> [!IMPORTANT]
> Programmatically controlling the speakerphone during a system cellular call is increasingly restricted in modern Android versions (API 31+). While we will implement the modern `setCommunicationDevice` API, some devices may still block this unless the app is the default dialer. We will use a robust retry mechanism and the correct audio modes to maximize compatibility.

## Proposed Changes

### [Component] Call Management Logic

#### [MODIFY] [CallReceiver.kt](file:///C:/Users/siac/StudioProjects/Auto-LoudSpeaker-/app/src/main/java/com/autoloud/speaker/CallReceiver.kt)
- **Modernize API**: Implement `AudioManager.setCommunicationDevice` for devices running Android 12 (API 31) and higher.
- **Refine Audio Modes**: Ensure `AudioManager.MODE_IN_COMMUNICATION` is used correctly, as it's the only mode allowed for non-system apps to influence audio routing.
- **Improve Reliability**:
    - Increase the number of retries and adjust the delay to account for the time it takes the system dialer to initialize the audio route.
    - Add explicit logging for each attempt to verify if the routing command was accepted by the system.
- **Service Integration**: Instead of performing the audio switch directly in the `BroadcastReceiver`, delegate the task to the `LoudSpeakerService`. A foreground service has higher priority and a more stable lifecycle, which can help in bypassing some system restrictions.

#### [MODIFY] [LoudSpeakerService.kt](file:///C:/Users/siac/StudioProjects/Auto-LoudSpeaker-/app/src/main/java/com/autoloud/speaker/LoudSpeakerService.kt)
- **Add Audio Control Logic**: Implement methods to enable and disable the speakerphone using the modern APIs.
- **Handle Commands**: Add a way for the service to receive commands (e.g., via Intent extras) from the `CallReceiver`.

### [Component] Manifest

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/siac/StudioProjects/Auto-LoudSpeaker-/app/src/main/AndroidManifest.xml)
- **Add `BLUETOOTH_CONNECT`**: This permission is sometimes required for audio routing APIs on newer Android versions, even if not using Bluetooth, as it's part of the unified routing framework.

## Verification Plan

### Automated Tests
- Build the app with `./gradlew :app:assembleDebug`.

### Manual Verification
- Deploy to a device running Android 14.
- Enable the service.
- Make or receive a call.
- Observe Logcat for "LoudSpeakerService" and "CallReceiver" logs to see if `setCommunicationDevice` is being called and if it returns `true`.
- Verify if the loudspeaker actually turns on.
