package com.autoloud.speaker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors

class LoudSpeakerService : Service() {

    companion object {
        private const val TAG = "LoudSpeakerService"
        const val CHANNEL_ID = "AutoLoudSpeakerChannel"
        const val NOTIF_ID = 1
        private const val SPEAKER_DELAY_MS = 1000L
        private const val SPEAKER_RETRY_DELAY_MS = 700L
        private const val MAX_RETRIES = 5
    }

    private lateinit var audioManager: AudioManager
    private lateinit var telephonyManager: TelephonyManager
    private val handler = Handler(Looper.getMainLooper())

    // API 31+ callback
    private var telephonyCallback: TelephonyCallback? = null

    // API 26-30 listener
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
        }

        registerPhoneListener()
        return START_STICKY
    }

    private fun registerPhoneListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ — TelephonyCallback
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state)
                }
            }
            telephonyCallback = cb
            telephonyManager.registerTelephonyCallback(
                Executors.newSingleThreadExecutor(), cb
            )
            Log.d(TAG, "Registered TelephonyCallback (API 31+)")
        } else {
            // API 26-30 — PhoneStateListener
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Suppress("DEPRECATION")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallState(state)
                }
            }
            phoneStateListener = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            Log.d(TAG, "Registered PhoneStateListener (API <31)")
        }
    }

    private fun unregisterPhoneListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                phoneStateListener?.let {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "unregister failed", e)
        }
    }

    private fun handleCallState(state: Int) {
        Log.d(TAG, "Call state: $state")
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call answered — enable speakerphone with retry
                handler.removeCallbacksAndMessages(null)
                enableSpeakerWithRetry(0)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended
                handler.removeCallbacksAndMessages(null)
                restoreAudio()
            }
        }
    }

    private fun enableSpeakerWithRetry(attempt: Int) {
        val delay = if (attempt == 0) SPEAKER_DELAY_MS else SPEAKER_RETRY_DELAY_MS
        handler.postDelayed({
            try {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                @Suppress("DEPRECATION")
                if (audioManager.isSpeakerphoneOn) {
                    Log.d(TAG, "✓ Speakerphone ON (attempt ${attempt + 1})")
                } else if (attempt < MAX_RETRIES) {
                    Log.w(TAG, "Speakerphone not confirmed, retry ${attempt + 1}")
                    enableSpeakerWithRetry(attempt + 1)
                } else {
                    Log.e(TAG, "Speakerphone failed after $MAX_RETRIES attempts")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception enabling speaker", e)
                if (attempt < MAX_RETRIES) enableSpeakerWithRetry(attempt + 1)
            }
        }, delay)
    }

    private fun restoreAudio() {
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.d(TAG, "Audio restored")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore audio", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Auto LoudSpeaker", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Auto LoudSpeaker is active" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto LoudSpeaker")
            .setContentText("Active – calls will use loudspeaker automatically")
            .setSmallIcon(R.drawable.ic_speaker)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        unregisterPhoneListener()
        restoreAudio()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
