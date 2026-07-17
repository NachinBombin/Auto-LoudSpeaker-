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

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private val permissionsList = mutableListOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private var isUpdatingToggle = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.all { it.value }
        Log.d(TAG, "Permissions result: allGranted=$allGranted")
        if (allGranted) {
            updateToggleState(true)
            enableService()
        } else {
            Toast.makeText(this, "Permissions required to enable Auto LoudSpeaker", Toast.LENGTH_LONG).show()
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
            Log.d(TAG, "Toggle changed to: $checked")

            if (checked) {
                if (hasPermissions()) {
                    enableService()
                } else {
                    Log.d(TAG, "Missing permissions, requesting...")
                    requestPermissionLauncher.launch(permissionsList)
                    updateToggleState(false)
                }
            } else {
                disableService()
            }
        }
    }

    private fun updateToggleState(enabled: Boolean) {
        isUpdatingToggle = true
        binding.toggleSwitch.isChecked = enabled
        isUpdatingToggle = false
    }

    private fun hasPermissions(): Boolean {
        return permissionsList.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun enableService() {
        Log.d(TAG, "Enabling service")
        prefs.edit { putBoolean("enabled", true) }
        updateStatusText(true)
        val intent = Intent(this, LoudSpeakerService::class.java)
        startForegroundService(intent)
    }

    private fun disableService() {
        Log.d(TAG, "Disabling service")
        prefs.edit { putBoolean("enabled", false) }
        updateStatusText(false)
        stopService(Intent(this, LoudSpeakerService::class.java))
    }

    private fun updateStatusText(enabled: Boolean) {
        binding.statusText.text = if (enabled) "Active" else "Inactive"
        binding.statusText.setTextColor(
            ContextCompat.getColor(
                this,
                if (enabled) R.color.green_active else R.color.gray_inactive
            )
        )
    }

    override fun onResume() {
        super.onResume()
        // Reflect permission revocation
        val enabled = prefs.getBoolean("enabled", false)
        Log.d(TAG, "onResume: enabled=$enabled")
        if (enabled && !hasPermissions()) {
            Log.d(TAG, "onResume: permissions lost, disabling service")
            disableService()
            updateToggleState(false)
            Toast.makeText(this, "Permissions were revoked. Please re-enable.", Toast.LENGTH_LONG).show()
        }
    }
}
