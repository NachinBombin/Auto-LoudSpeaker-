package com.autoloud.speaker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

// No-op: all call handling is done in AutoSpeakerInCallService
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("CallReceiver", "noop - handled by InCallService")
    }
}
