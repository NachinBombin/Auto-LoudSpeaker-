package com.autoloud.speaker

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.autoloud.speaker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object { private const val TAG = "MainActivity" }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var isUpdatingToggle = false

    private val requiredPermissions = mutableListOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.all { it.value }
        Log.d(TAG, "Permissions: $results")
        if (allGranted) { enableService(); updateToggleState(true) }
        else {
            Toast.makeText(this,
                "All permissions are required. Please grant them in Settings.",
                Toast.LENGTH_LONG).show()
            updateToggleState(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("AutoLoudSpeaker", MODE_PRIVATE)

        val isEnabled = prefs.getBoolean("enabled", false)
        updateToggleState(isEnabled)
        updateStatusText(isEnabled)

        binding.toggleSwitch.setOnCheckedChangeListener { _, checked ->
            if (isUpdatingToggle) return@setOnCheckedChangeListener
            if (checked) {
                if (hasPermissions()) enableService()
                else { permissionLauncher.launch(requiredPermissions); updateToggleState(false) }
            } else disableService()
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = prefs.getBoolean("enabled", false)
        if (enabled && !hasPermissions()) {
            disableService(); updateToggleState(false)
            Toast.makeText(this, "Permissions revoked — please re-enable.", Toast.LENGTH_LONG).show()
        } else { updateToggleState(enabled); updateStatusText(enabled) }
    }

    private fun hasPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun enableService() {
        prefs.edit { putBoolean("enabled", true) }
        updateStatusText(true)
        startForegroundService(Intent(this, LoudSpeakerService::class.java))
        Log.d(TAG, "Service started")
    }

    private fun disableService() {
        prefs.edit { putBoolean("enabled", false) }
        updateStatusText(false)
        stopService(Intent(this, LoudSpeakerService::class.java))
        Log.d(TAG, "Service stopped")
    }

    private fun updateToggleState(enabled: Boolean) {
        isUpdatingToggle = true
        binding.toggleSwitch.isChecked = enabled
        isUpdatingToggle = false
    }

    private fun updateStatusText(enabled: Boolean) {
        binding.statusText.text = if (enabled) "Active" else "Inactive"
        binding.statusText.setTextColor(ContextCompat.getColor(this,
            if (enabled) R.color.green_active else R.color.gray_inactive))
    }
}
