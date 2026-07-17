# Implementation Plan - Fix Permission Loop and Activation

The app is currently stuck in a permission loop because `POST_NOTIFICATIONS` is being requested in the code but is missing from the `AndroidManifest.xml`. Additionally, `MANAGE_OWN_CALLS` is missing, which is a requirement for the `phoneCall` foreground service type on Android 14.

## User Review Required

> [!IMPORTANT]
> I will be restoring the `POST_NOTIFICATIONS` and `MANAGE_OWN_CALLS` permissions to the manifest. I will also remove `PROCESS_OUTGOING_CALLS` as it is deprecated and not required for the current implementation.

## Proposed Changes

### [Component] Android Manifest

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/siac/StudioProjects/Auto-LoudSpeaker-/app/src/main/AndroidManifest.xml)
- Add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />` (Required for API 33+).
- Add `<uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />` (Required for FGS type `phoneCall` on API 34+).
- Remove `<uses-permission android:name="android.permission.PROCESS_OUTGOING_CALLS" />` (Deprecated and unused).

### [Component] MainActivity Logic

#### [MODIFY] [MainActivity.kt](file:///C:/Users/siac/StudioProjects/Auto-LoudSpeaker-/app/src/main/java/com/autoloud/speaker/MainActivity.kt)
- **Improve `hasPermissions`**: Ensure it only checks permissions that are actually requested.
- **Fix Logic Timing**: Ensure `prefs.edit` happens AFTER successful service start, or at least handle the error state better.
- **Activity Result API**: Migrate from `ActivityCompat.requestPermissions` to `registerForActivityResult(RequestMultiplePermissions())` for more reliable results and less lifecycle-related issues.

## Verification Plan

### Automated Tests
- Build the app with `./gradlew :app:assembleDebug`.

### Manual Verification
- Deploy to a device running Android 13 or 14.
- Verify that the app requests "Phone", "Answer Phone Calls", and "Notifications".
- Verify that the toggle activates correctly once all permissions are granted.
- Check Logcat for "MainActivity" and "LoudSpeakerService" logs to ensure no `SecurityException` occurs.
