package com.musa.wordwise

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doOnTextChanged
import com.musa.wordwise.data.ApiKeyRepository
import com.musa.wordwise.data.ProviderRepository
import com.musa.wordwise.databinding.ActivityMainBinding
import com.musa.wordwise.network.AiProvider

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val apiKeyRepository by lazy { ApiKeyRepository(this) }
    private val providerRepository by lazy { ProviderRepository(this) }

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

        // Load existing state
        loadExistingApiKey()
        setupProviderSelection()
        loadExistingOllamaKey()

        binding.apiKeyEditText.doOnTextChanged { _, _, _, _ ->
            binding.apiKeyInputLayout.error = null
        }

        binding.saveApiKeyButton.setOnClickListener {
            val apiKey = binding.apiKeyEditText.text.toString().trim()
            if (apiKey.isNotEmpty()) {
                // Validate Gemini API key format (typically starts with AIza)
                if (apiKey.startsWith("AIza") || apiKey.length > 30) {
                    saveApiKey(apiKey)
                    Toast.makeText(this, getString(R.string.toast_api_key_saved), Toast.LENGTH_SHORT).show()
                    binding.apiKeyInputLayout.helperText = getString(R.string.key_saved_indicator)
                    binding.apiKeyInputLayout.error = null
                } else {
                    binding.apiKeyInputLayout.error = getString(R.string.toast_invalid_api_key)
                    binding.apiKeyInputLayout.helperText = null
                }
            } else {
                binding.apiKeyInputLayout.error = getString(R.string.key_empty_error)
                binding.apiKeyInputLayout.helperText = null
            }
        }

        binding.saveOllamaKeyButton.setOnClickListener {
            val apiKey = binding.ollamaKeyEditText.text.toString().trim()
            if (apiKey.isNotEmpty()) {
                providerRepository.saveOllamaKey(apiKey)
                Toast.makeText(this, getString(R.string.toast_api_key_saved), Toast.LENGTH_SHORT).show()
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
                ContextCompat.getColor(this, R.color.ww_success)
            )
        } else {
            binding.serviceStatusTextView.text = getString(R.string.status_disabled)
            binding.serviceStatusTextView.setTextColor(
                ContextCompat.getColor(this, R.color.ww_error)
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
            binding.apiKeyEditText.setText(existingKey)
            binding.apiKeyInputLayout.helperText = getString(R.string.key_saved_indicator)
        }
    }

    private fun saveApiKey(key: String) {
        apiKeyRepository.saveApiKey(key)
    }

    private fun setupProviderSelection() {
        val providers = arrayOf(getString(R.string.provider_gemini), getString(R.string.provider_ollama))
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, providers)
        binding.providerAutoComplete.setAdapter(adapter)

        val currentProvider = providerRepository.getProvider()
        val selection = if (currentProvider == AiProvider.OLLAMA_CLOUD) 1 else 0
        binding.providerAutoComplete.setText(providers[selection], false)
        updateOllamaSectionVisibility(currentProvider)

        binding.providerAutoComplete.setOnItemClickListener { _, _, position, _ ->
            val provider = if (position == 1) AiProvider.OLLAMA_CLOUD else AiProvider.GEMINI
            providerRepository.saveProvider(provider)
            updateOllamaSectionVisibility(provider)
        }
    }

    private fun loadExistingOllamaKey() {
        val existingKey = providerRepository.getOllamaKey()
        if (!existingKey.isNullOrEmpty()) {
            binding.ollamaKeyEditText.setText(existingKey)
        }
    }

    private fun updateOllamaSectionVisibility(provider: AiProvider) {
        binding.ollamaKeySection.visibility = if (provider == AiProvider.OLLAMA_CLOUD) View.VISIBLE else View.GONE
    }
}
