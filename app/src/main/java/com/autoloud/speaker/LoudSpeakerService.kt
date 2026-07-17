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
        private const val DELAY_MS = 800L
        private const val RETRY_MS = 600L
        private const val MAX_RETRIES = 8
    }

    private lateinit var audioManager: AudioManager
    private lateinit var telephonyManager: TelephonyManager
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0

    private var telephonyCallback: TelephonyCallback? = null
    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null

    // API 31+: listener to confirm device was actually set
    private val communicationDeviceListener =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AudioManager.OnCommunicationDeviceChangedListener { device ->
                Log.d(TAG, "CommunicationDevice changed to: ${device?.type}")
            }
        } else null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            communicationDeviceListener?.let {
                audioManager.addOnCommunicationDeviceChangedListener(
                    Executors.newSingleThreadExecutor(), it
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
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
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = handleCallState(state)
            }
            telephonyCallback = cb
            telephonyManager.registerTelephonyCallback(Executors.newSingleThreadExecutor(), cb)
            Log.d(TAG, "Registered TelephonyCallback")
        } else {
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
            Log.d(TAG, "Registered PhoneStateListener")
        }
    }

    private fun handleCallState(state: Int) {
        Log.d(TAG, "handleCallState: $state")
        handler.removeCallbacksAndMessages(null)
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                retryCount = 0
                handler.postDelayed({ enableSpeaker() }, DELAY_MS)
            }
            TelephonyManager.CALL_STATE_IDLE -> restoreAudio()
        }
    }

    private fun enableSpeaker() {
        try {
            Log.d(TAG, "enableSpeaker attempt $retryCount, API=${Build.VERSION.SDK_INT}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+): isSpeakerphoneOn is IGNORED by the system.
                // Must use setCommunicationDevice with TYPE_BUILTIN_SPEAKER.
                // Step 1: set mode to IN_COMMUNICATION so audio focus shifts to our app.
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

                // Step 2: find the built-in speaker in available communication devices
                val devices = audioManager.availableCommunicationDevices
                Log.d(TAG, "Available comm devices: ${devices.map { it.type }}")

                val speaker = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }

                if (speaker != null) {
                    val result = audioManager.setCommunicationDevice(speaker)
                    Log.d(TAG, "setCommunicationDevice result=$result, " +
                        "currentDevice=${audioManager.communicationDevice?.type}")
                    if (!result && retryCount < MAX_RETRIES) {
                        retryCount++
                        handler.postDelayed({ enableSpeaker() }, RETRY_MS)
                    }
                } else {
                    Log.w(TAG, "TYPE_BUILTIN_SPEAKER not in availableCommunicationDevices yet, retry $retryCount")
                    if (retryCount < MAX_RETRIES) {
                        retryCount++
                        handler.postDelayed({ enableSpeaker() }, RETRY_MS)
                    }
                }
            } else {
                // Android 8-11 (API 26-30): isSpeakerphoneOn still works
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                @Suppress("DEPRECATION")
                if (!audioManager.isSpeakerphoneOn && retryCount < MAX_RETRIES) {
                    retryCount++
                    handler.postDelayed({ enableSpeaker() }, RETRY_MS)
                } else {
                    Log.d(TAG, "Speakerphone ON (legacy API)")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "enableSpeaker exception", e)
            if (retryCount < MAX_RETRIES) {
                retryCount++
                handler.postDelayed({ enableSpeaker() }, RETRY_MS)
            }
        }
    }

    private fun restoreAudio() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.d(TAG, "Audio restored")
        } catch (e: Exception) {
            Log.e(TAG, "restoreAudio exception", e)
        }
    }

    private fun unregisterPhoneListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let { telephonyManager.unregisterTelephonyCallback(it) }
                communicationDeviceListener?.let {
                    audioManager.removeOnCommunicationDeviceChangedListener(it)
                }
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        unregisterPhoneListener()
        restoreAudio()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Auto LoudSpeaker", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Auto LoudSpeaker is active" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto LoudSpeaker")
            .setContentText("Active – calls will use loudspeaker automatically")
            .setSmallIcon(R.drawable.ic_speaker)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
