package com.autoloud.speaker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("AutoLoudSpeaker", Context.MODE_PRIVATE)
            if (prefs.getBoolean("enabled", false)) {
                Log.d("BootReceiver", "Restarting service after boot")
                val si = Intent(context, LoudSpeakerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    context.startForegroundService(si)
                else context.startService(si)
            }
        }
    }
}
