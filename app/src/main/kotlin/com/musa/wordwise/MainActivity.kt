package com.musa.wordwise

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.musa.wordwise.data.ApiKeyRepository
import com.musa.wordwise.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val apiKeyRepository by lazy { ApiKeyRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Transparent status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT

        // Dynamic header padding for status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerLayout) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(0, statusBarHeight + 24, 0, 0)
            insets
        }

        // Load existing API key if present
        loadExistingApiKey()

        binding.saveApiKeyButton.setOnClickListener {
            val apiKey = binding.apiKeyEditText.text.toString().trim()
            if (apiKey.isNotEmpty()) {
                // Validate Gemini API key format (typically starts with AIza)
                if (apiKey.startsWith("AIza") || apiKey.length > 30) {
                    saveApiKey(apiKey)
                    Toast.makeText(this, getString(R.string.toast_api_key_saved), Toast.LENGTH_SHORT).show()
                    // Clear field; inputType="textPassword" handles masking if we were to keep text
                    binding.apiKeyEditText.text = null
                    binding.apiKeyEditText.hint = getString(R.string.api_key_hint)
                } else {
                    Toast.makeText(this, getString(R.string.toast_invalid_api_key), Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.toast_api_key_empty), Toast.LENGTH_SHORT).show()
            }
        }

        binding.openAccessibilitySettingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, getString(R.string.toast_accessibility_hint), Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun updateServiceStatus() {
        if (isAccessibilityServiceEnabled(this, GrammarFixService::class.java)) {
            binding.serviceStatusTextView.text = getString(R.string.status_enabled)
            binding.serviceStatusTextView.setTextColor(
                ContextCompat.getColor(this, R.color.ww_status_enabled)
            )
        } else {
            binding.serviceStatusTextView.text = getString(R.string.status_disabled)
            binding.serviceStatusTextView.setTextColor(
                ContextCompat.getColor(this, R.color.ww_status_disabled)
            )
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
            // Hint that key is saved; inputType handles password dotting if we showed it
            binding.apiKeyEditText.hint = getString(R.string.api_key_hint)
        }
    }

    private fun saveApiKey(key: String) {
        apiKeyRepository.saveApiKey(key)
    }
}
