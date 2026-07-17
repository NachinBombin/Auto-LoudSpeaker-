# Implementation Plan - Fix App Crash After Granting Permissions

The user reports the app crashes immediately after permissions are granted. Based on the target SDK (34) and the use of a Foreground Service with type `phoneCall`, the most likely cause is a `SecurityException` introduced in Android 14.

## User Review Required

> [!IMPORTANT]
> Android 14 has strict requirements for the `phoneCall` foreground service type. Even if the permission is declared, the app must either have the `MANAGE_OWN_CALLS` permission or be the default dialer. I will add `MANAGE_OWN_CALLS` to satisfy this requirement.

## Proposed Changes

### [Component] Android Manifest

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/siac/StudioProjects/Auto-LoudSpeaker-/app/src/main/AndroidManifest.xml)
- Add `<uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />`. This is a normal permission and is required for the `phoneCall` foreground service type on Android 14+ if the app is not the default dialer.

### [Component] Service Logic

#### [MODIFY] [LoudSpeakerService.kt](file:///C:/Users/siac/StudioProjects/Auto-LoudSpeaker-/app/src/main/java/com/autoloud/speaker/LoudSpeakerService.kt)
- Add a try-catch block around `startForeground` to provide better error logging and prevent immediate crashes if system conditions are not met.
- (Optional) If `phoneCall` continues to be an issue, I may suggest switching to `specialUse`, but adding the permission is the first step.

## Verification Plan

### Automated Tests
- Build the app using `./gradlew :app:assembleDebug`.

### Manual Verification
- Deploy to an Android 14+ device.
- Grant permissions and verify that `enableService()` no longer causes a crash.
- Check logcat (if possible) to ensure no `SecurityException` is thrown during `startForeground`.
