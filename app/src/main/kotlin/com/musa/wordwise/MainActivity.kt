package com.musa.wordwise

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class MainActivity : AppCompatActivity() {

    private lateinit var apiKeyEditText: EditText
    private lateinit var saveApiKeyButton: Button
    private lateinit var openAccessibilitySettingsButton: Button
    private lateinit var serviceStatusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apiKeyEditText = findViewById(R.id.apiKeyEditText)
        saveApiKeyButton = findViewById(R.id.saveApiKeyButton)
        openAccessibilitySettingsButton = findViewById(R.id.openAccessibilitySettingsButton)
        serviceStatusTextView = findViewById(R.id.serviceStatusTextView)

        saveApiKeyButton.setOnClickListener {
            val apiKey = apiKeyEditText.text.toString()
            if (apiKey.isNotEmpty()) {
                saveApiKey(apiKey)
                Toast.makeText(this, "API Key saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "API Key cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        openAccessibilitySettingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled(this, GrammarFixService::class.java)) {
            serviceStatusTextView.text = "Service Status: Enabled"
        } else {
            serviceStatusTextView.text = "Service Status: Disabled"
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val serviceId = "${context.packageName}/${service.name}"
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(serviceId) ?: false
    }

    private fun saveApiKey(key: String) {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            this,
            "secret_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        sharedPreferences.edit().putString("api_key", key).apply()
    }
}