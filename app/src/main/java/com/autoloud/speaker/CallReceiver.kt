package com.autoloud.speaker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay as coDelay

class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallReceiver"
        private const val INITIAL_DELAY_MS = 1000L
        private const val RETRY_DELAY_MS   = 600L
        private const val MAX_RETRIES      = 6
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("AutoLoudSpeaker", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        Log.d(TAG, "Phone state: $state")

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Use goAsync() so we can delay inside the receiver without being killed
                val pendingResult = goAsync()
                GlobalScope.launch {
                    try {
                        coDelay(INITIAL_DELAY_MS)
                        var success = false
                        for (attempt in 0 until MAX_RETRIES) {
                            try {
                                // MODE_IN_COMMUNICATION is the correct mode for third-party apps.
                                // MODE_IN_CALL is for the system dialer ONLY and is silently ignored here.
                                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                                @Suppress("DEPRECATION")
                                audioManager.isSpeakerphoneOn = true
                                @Suppress("DEPRECATION")
                                if (audioManager.isSpeakerphoneOn) {
                                    Log.d(TAG, "Speakerphone ON (attempt ${attempt + 1})")
                                    success = true
                                    break
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Attempt ${attempt + 1} failed", e)
                            }
                            coDelay(RETRY_DELAY_MS)
                        }
                        if (!success) Log.e(TAG, "Could not enable speakerphone after $MAX_RETRIES attempts")
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                try {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = false
                    audioManager.mode = AudioManager.MODE_NORMAL
                    Log.d(TAG, "Audio restored to normal")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore audio", e)
                }
            }
        }
    }
}
