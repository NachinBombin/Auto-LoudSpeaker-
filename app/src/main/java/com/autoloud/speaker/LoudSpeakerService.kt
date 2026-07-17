package com.autoloud.speaker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat

class LoudSpeakerService : Service() {

    companion object {
        private const val TAG = "LoudSpeakerService"
        const val CHANNEL_ID = "AutoLoudSpeakerChannel"
        const val NOTIF_ID = 1

        const val ACTION_ENABLE_SPEAKER = "com.autoloud.speaker.ACTION_ENABLE_SPEAKER"
        const val ACTION_DISABLE_SPEAKER = "com.autoloud.speaker.ACTION_DISABLE_SPEAKER"

        private const val INITIAL_DELAY_MS = 1500L
        private const val RETRY_DELAY_MS = 1000L
        private const val MAX_RETRIES = 3
    }

    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_ENABLE_SPEAKER -> {
                Log.d(TAG, "Action received: ENABLE_SPEAKER")
                enableSpeakerWithRetry(0)
            }
            ACTION_DISABLE_SPEAKER -> {
                Log.d(TAG, "Action received: DISABLE_SPEAKER")
                restoreAudio()
            }
        }

        return START_STICKY
    }

    private fun enableSpeakerWithRetry(attempt: Int) {
        val delay = if (attempt == 0) INITIAL_DELAY_MS else RETRY_DELAY_MS
        handler.postDelayed({
            try {
                Log.d(TAG, "Attempting to enable speakerphone (attempt ${attempt + 1})")
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val devices = audioManager.availableCommunicationDevices
                    val speakerDevice = devices.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speakerDevice != null) {
                        audioManager.setCommunicationDevice(speakerDevice)
                    } else {
                        Log.e(TAG, "Built-in speaker device not found!")
                        false
                    }
                } else {
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn
                }

                if (success) {
                    Log.d(TAG, "Speakerphone enabled successfully")
                } else if (attempt < MAX_RETRIES) {
                    Log.w(TAG, "Speakerphone activation failed/deferred, retrying...")
                    enableSpeakerWithRetry(attempt + 1)
                } else {
                    Log.e(TAG, "Failed to enable speakerphone after $MAX_RETRIES attempts")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during speaker activation", e)
            }
        }, delay)
    }

    private fun restoreAudio() {
        handler.removeCallbacksAndMessages(null)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.d(TAG, "Audio mode restored to normal")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore audio mode", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Auto LoudSpeaker",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps Auto LoudSpeaker running in the background"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto LoudSpeaker")
            .setContentText("Active – calls will use loudspeaker automatically")
            .setSmallIcon(R.drawable.ic_speaker)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
