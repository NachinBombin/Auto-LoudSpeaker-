package com.autoloud.speaker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallReceiver"
        // Attempts to enable speakerphone with retries
        private const val INITIAL_DELAY_MS = 1000L
        private const val RETRY_DELAY_MS = 500L
        private const val MAX_RETRIES = 5
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("AutoLoudSpeaker", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        Log.d(TAG, "Phone state: $state")

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call answered — enable speakerphone with retry loop
                enableSpeakerWithRetry(context, 0)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended — restore audio
                restoreAudio(context)
            }
        }
    }

    private fun enableSpeakerWithRetry(context: Context, attempt: Int) {
        val delay = if (attempt == 0) INITIAL_DELAY_MS else RETRY_DELAY_MS
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                // MODE_IN_COMMUNICATION is the correct mode for VoIP/calls in third-party apps
                // MODE_IN_CALL is reserved for the system dialer and will be silently ignored
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true

                if (audioManager.isSpeakerphoneOn) {
                    Log.d(TAG, "Speakerphone enabled on attempt ${attempt + 1}")
                } else if (attempt < MAX_RETRIES) {
                    Log.w(TAG, "Speakerphone not yet active, retrying (attempt ${attempt + 1})")
                    enableSpeakerWithRetry(context, attempt + 1)
                } else {
                    Log.e(TAG, "Failed to enable speakerphone after $MAX_RETRIES attempts")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception enabling speakerphone", e)
            }
        }, delay)
    }

    private fun restoreAudio(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.d(TAG, "Audio mode restored to normal")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore audio mode", e)
        }
    }
}
