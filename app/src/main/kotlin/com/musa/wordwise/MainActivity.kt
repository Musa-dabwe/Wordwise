package com.musa.wordwise

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.musa.wordwise.data.ApiKeyRepository
import com.musa.wordwise.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val apiKeyRepository by lazy { ApiKeyRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load existing API key if present
        loadExistingApiKey()

        binding.saveApiKeyButton.setOnClickListener {
            val apiKey = binding.apiKeyEditText.text.toString().trim()
            if (apiKey.isNotEmpty()) {
                // Validate Gemini API key format (typically starts with AIza)
                if (apiKey.startsWith("AIza") || apiKey.length > 30) {
                    saveApiKey(apiKey)
                    Toast.makeText(this, "✅ API Key saved securely!", Toast.LENGTH_SHORT).show()
                    // Clear the field for security
                    binding.apiKeyEditText.setText("••••••••••••••••")
                } else {
                    Toast.makeText(this, "⚠️ Invalid API key format. Get your key from aistudio.google.com", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "❌ API Key cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        binding.openAccessibilitySettingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Find and enable 'WordWise' in the list", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled(this, GrammarFixService::class.java)) {
            binding.serviceStatusTextView.text = "Enabled ✓"
            binding.serviceStatusTextView.setTextColor(Color.parseColor("#51CF66"))
        } else {
            binding.serviceStatusTextView.text = "Disabled"
            binding.serviceStatusTextView.setTextColor(Color.parseColor("#FF6B6B"))
        }
    }

    private fun isAccessibilityServiceEnabled(
        context: Context,
        service: Class<out AccessibilityService>
    ): Boolean {
        val serviceId = "${context.packageName}/${service.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceId) ?: false
    }

    private fun loadExistingApiKey() {
        val existingKey = apiKeyRepository.getApiKey()
        if (existingKey.isNotEmpty()) {
            // Show masked version if key exists
            binding.apiKeyEditText.setText("••••••••••••••••")
            binding.apiKeyEditText.hint = "API key already saved"
        }
    }

    private fun saveApiKey(key: String) {
        apiKeyRepository.saveApiKey(key)
    }
}
