package com.autoloud.speaker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallReceiver"
        // Delay in ms before enabling speakerphone — gives the call audio route time to initialize
        private const val SPEAKER_DELAY_MS = 500L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("AutoLoudSpeaker", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        Log.d(TAG, "Phone state: $state")

        if (state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
            // Call has been answered — enable speakerphone with a short delay
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.mode = AudioManager.MODE_IN_CALL

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val devices = audioManager.availableCommunicationDevices
                        val speakerDevice = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        if (speakerDevice != null) {
                            audioManager.setCommunicationDevice(speakerDevice)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = true
                    }
                    Log.d(TAG, "Speakerphone enabled")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable speakerphone", e)
                }
            }, SPEAKER_DELAY_MS)
        } else if (state == TelephonyManager.EXTRA_STATE_IDLE) {
            // Call ended — restore audio mode
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = false
                }
                audioManager.mode = AudioManager.MODE_NORMAL
                Log.d(TAG, "Audio mode restored")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore audio mode", e)
            }
        }
    }
}
