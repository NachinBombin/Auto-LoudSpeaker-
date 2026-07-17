package com.autoloud.speaker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("AutoLoudSpeaker", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        Log.d(TAG, "Phone state received: $state")

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Log.d(TAG, "Call Answered - Sending ENABLE_SPEAKER command")
                sendCommandToService(context, LoudSpeakerService.ACTION_ENABLE_SPEAKER)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.d(TAG, "Call Idle - Sending DISABLE_SPEAKER command")
                sendCommandToService(context, LoudSpeakerService.ACTION_DISABLE_SPEAKER)
            }
        }
    }

    private fun sendCommandToService(context: Context, action: String) {
        val intent = Intent(context, LoudSpeakerService::class.java).apply {
            this.action = action
        }
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command to service: $action", e)
        }
    }
}
