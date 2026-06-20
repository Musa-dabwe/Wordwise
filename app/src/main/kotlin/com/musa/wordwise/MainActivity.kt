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
import android.view.accessibility.AccessibilityManager
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

        setupApiKeySection()
        setupSaveButton()

        binding.openAccessibilitySettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, R.string.toast_accessibility_hint, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupApiKeySection() {
        // Pre-fill with any previously saved key
        binding.editApiKey.setText(apiKeyRepository.getApiKey())

        // Static hyperlink — "Get a free key at https://aistudio.google.com"
        val url      = getString(R.string.gemini_api_url)
        val prefix   = getString(R.string.link_get_key_prefix)   // "Get a free key at "
        val linkText = prefix + url
        val spannable = SpannableString(linkText)
        spannable.setSpan(
            URLSpan(url),
            prefix.length,
            linkText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.textGetKeyLink.text = spannable
        binding.textGetKeyLink.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupSaveButton() {
        binding.saveApiKeyButton.setOnClickListener {
            val key = binding.editApiKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, R.string.toast_api_key_empty, Toast.LENGTH_SHORT).show()
            } else {
                apiKeyRepository.saveApiKey(key)
                Toast.makeText(this, R.string.toast_api_key_saved, Toast.LENGTH_SHORT).show()
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
            binding.serviceStatusTextView.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            binding.serviceStatusTextView.setText(R.string.status_disabled)
            binding.serviceStatusTextView.setTextColor(Color.parseColor("#F44336"))
        }
    }
}
