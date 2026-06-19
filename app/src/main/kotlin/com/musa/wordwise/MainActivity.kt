package com.musa.wordwise

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.musa.wordwise.data.ApiKeyRepository
import com.musa.wordwise.data.Provider
import com.musa.wordwise.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val apiKeyRepository by lazy { ApiKeyRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupProviderSpinner()
        setupSaveButton()

        binding.openAccessibilitySettingsButton.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, R.string.toast_accessibility_hint, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupProviderSpinner() {
        val providerNames = Provider.entries.map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providerNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerProvider.adapter = adapter

        val savedProvider = apiKeyRepository.getActiveProvider()
        binding.spinnerProvider.setSelection(Provider.entries.indexOf(savedProvider))

        // Initial UI update
        updateProviderUI(savedProvider)
        binding.editApiKey.setText(apiKeyRepository.getApiKey(savedProvider))

        binding.spinnerProvider.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedProvider = Provider.entries[position]
                updateProviderUI(selectedProvider)
                binding.editApiKey.setText(apiKeyRepository.getApiKey(selectedProvider))
                apiKeyRepository.saveActiveProvider(selectedProvider)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateProviderUI(provider: Provider) {
        binding.textApiKeyLabel.text = provider.apiKeyLabel
        binding.editApiKey.hint = provider.apiKeyHint

        val linkText = getString(R.string.link_get_key_prefix) + provider.apiKeyUrl
        val spannable = SpannableString(linkText)
        val start = linkText.indexOf(provider.apiKeyUrl)
        if (start != -1) {
            spannable.setSpan(
                URLSpan(provider.apiKeyUrl),
                start,
                linkText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.textGetKeyLink.text = spannable
        binding.textGetKeyLink.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupSaveButton() {
        binding.saveApiKeyButton.setOnClickListener {
            val position = binding.spinnerProvider.selectedItemPosition
            val activeProvider = Provider.entries[position]
            val key = binding.editApiKey.text.toString().trim()

            if (key.isEmpty()) {
                Toast.makeText(this, R.string.toast_api_key_empty, Toast.LENGTH_SHORT).show()
            } else {
                apiKeyRepository.saveApiKey(activeProvider, key)
                Toast.makeText(this, "${activeProvider.displayName} API key saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityStatus()
    }

    private fun checkAccessibilityStatus() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val isEnabled = enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }

        if (isEnabled) {
            binding.serviceStatusTextView.setText(R.string.status_enabled)
            binding.serviceStatusTextView.setTextColor(Color.parseColor("#4CAF50")) // Material Green
        } else {
            binding.serviceStatusTextView.setText(R.string.status_disabled)
            binding.serviceStatusTextView.setTextColor(Color.parseColor("#F44336")) // Material Red
        }
    }
}
