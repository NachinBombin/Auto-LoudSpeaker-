package com.autoloud.speaker

// CallReceiver is kept only for BOOT_COMPLETED migration safety.
// All call-state listening is now done inside LoudSpeakerService
// via TelephonyCallback / PhoneStateListener, which are immune
// to Android battery-optimization broadcast throttling.

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // No-op: phone state is handled by LoudSpeakerService internally.
        Log.d("CallReceiver", "Received (ignored, handled by service): ${intent.action}")
    }
}
