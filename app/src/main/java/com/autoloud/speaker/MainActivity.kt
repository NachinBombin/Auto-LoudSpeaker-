package com.autoloud.speaker

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.autoloud.speaker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private val PERMISSIONS = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ANSWER_PHONE_CALLS,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    )
    private val PERMISSION_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("AutoLoudSpeaker", MODE_PRIVATE)

        val isEnabled = prefs.getBoolean("enabled", false)
        binding.toggleSwitch.isChecked = isEnabled
        updateStatusText(isEnabled)

        binding.toggleSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (hasPermissions()) {
                    enableService()
                } else {
                    requestPermissions()
                    binding.toggleSwitch.isChecked = false
                }
            } else {
                disableService()
            }
        }
    }

    private fun hasPermissions(): Boolean {
        return PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                binding.toggleSwitch.isChecked = true
                enableService()
            } else {
                Toast.makeText(this, "Permissions required to enable Auto LoudSpeaker", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enableService() {
        prefs.edit().putBoolean("enabled", true).apply()
        updateStatusText(true)
        val intent = Intent(this, LoudSpeakerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun disableService() {
        prefs.edit().putBoolean("enabled", false).apply()
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
        if (enabled && !hasPermissions()) {
            disableService()
            binding.toggleSwitch.isChecked = false
            Toast.makeText(this, "Permissions were revoked. Please re-enable.", Toast.LENGTH_LONG).show()
        }
    }
}
