package com.autoloud.speaker

import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log

class AutoSpeakerInCallService : InCallService() {

    companion object {
        private const val TAG = "AutoSpeakerInCall"
        private const val DELAY_MS = 800L
        private const val RETRY_MS = 700L
        private const val MAX_RETRIES = 6
    }

    private val handler = Handler(Looper.getMainLooper())

    // Callback registered per call
    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            Log.d(TAG, "onStateChanged: $state")
            if (state == Call.STATE_ACTIVE) {
                // Call is now answered and active — route to speaker
                handler.removeCallbacksAndMessages(null)
                handler.postDelayed({ enableSpeaker(0) }, DELAY_MS)
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded state=${call.state}")

        val prefs = getSharedPreferences("AutoLoudSpeaker", MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return

        call.registerCallback(callCallback)

        // If call is already active when added (e.g. outgoing accepted fast)
        if (call.state == Call.STATE_ACTIVE) {
            handler.postDelayed({ enableSpeaker(0) }, DELAY_MS)
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved")
        call.unregisterCallback(callCallback)
        handler.removeCallbacksAndMessages(null)
    }

    private fun enableSpeaker(attempt: Int) {
        val prefs = getSharedPreferences("AutoLoudSpeaker", MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", false)) return

        try {
            val currentState = callAudioState
            Log.d(TAG, "enableSpeaker attempt=$attempt, currentRoute=${currentState?.route}, " +
                    "supportedMask=${currentState?.supportedRouteMask}")

            if (currentState == null) {
                if (attempt < MAX_RETRIES) handler.postDelayed({ enableSpeaker(attempt + 1) }, RETRY_MS)
                return
            }

            if (currentState.route == CallAudioState.ROUTE_SPEAKER) {
                Log.d(TAG, "✓ Already on speaker")
                return
            }

            // setAudioRoute is the official InCallService API to switch audio route
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            Log.d(TAG, "setAudioRoute(SPEAKER) called")

            // Verify after short delay
            handler.postDelayed({
                val newState = callAudioState
                Log.d(TAG, "Post-set route=${newState?.route}")
                if (newState?.route != CallAudioState.ROUTE_SPEAKER && attempt < MAX_RETRIES) {
                    Log.w(TAG, "Route not SPEAKER yet, retry ${attempt + 1}")
                    enableSpeaker(attempt + 1)
                } else {
                    Log.d(TAG, "✓ Speaker confirmed on attempt ${attempt + 1}")
                }
            }, RETRY_MS)

        } catch (e: Exception) {
            Log.e(TAG, "enableSpeaker exception", e)
            if (attempt < MAX_RETRIES) handler.postDelayed({ enableSpeaker(attempt + 1) }, RETRY_MS)
        }
    }
}
