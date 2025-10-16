package com.musa.wordwise

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class MainActivity : AppCompatActivity() {

    private lateinit var apiKeyEditText: EditText
    private lateinit var saveApiKeyButton: Button
    private lateinit var openAccessibilitySettingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apiKeyEditText = findViewById(R.id.apiKeyEditText)
        saveApiKeyButton = findViewById(R.id.saveApiKeyButton)
        openAccessibilitySettingsButton = findViewById(R.id.openAccessibilitySettingsButton)

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