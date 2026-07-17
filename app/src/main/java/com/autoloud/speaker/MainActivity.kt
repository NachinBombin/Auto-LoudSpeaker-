package com.autoloud.speaker

import android.Manifest
import android.app.role.RoleManager
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

    // Core permissions (no MODIFY_AUDIO_SETTINGS needed — InCallService handles routing)
    private val requiredPermissions = mutableListOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            // After permissions, ask for CALL_COMPANION role (needed for InCallService)
            requestCallCompanionRole()
        } else {
            Toast.makeText(this, "Permissions required.", Toast.LENGTH_LONG).show()
            updateToggleState(false)
        }
    }

    // Role request for InCallService to be bound by telecom
    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // Whether granted or not, enable — InCallService may still work on some devices
        enableService()
        updateToggleState(true)
        Log.d(TAG, "Role request done")
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
                if (hasPermissions()) requestCallCompanionRole()
                else { permissionLauncher.launch(requiredPermissions); updateToggleState(false) }
            } else disableService()
        }
    }

    private fun requestCallCompanionRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_COMPANION) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_CALL_COMPANION)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_COMPANION)
                roleLauncher.launch(intent)
            } else {
                // Role already held or not available — just enable
                enableService()
                updateToggleState(true)
            }
        } else {
            enableService()
            updateToggleState(true)
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
