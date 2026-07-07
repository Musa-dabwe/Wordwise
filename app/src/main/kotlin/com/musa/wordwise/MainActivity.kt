package com.musa.wordwise

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.MaterialColors
import com.musa.wordwise.data.ApiKeyRepository
import com.musa.wordwise.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val apiKeyRepository by lazy { ApiKeyRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(AppTheme.forKey(getSelectedThemeKey(this)).styleRes)
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupApiKeySection()
        setupModelSelector()
        setupThemeSelector()

        binding.saveButton.setOnClickListener {
            val key = binding.apiKeyInput.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, R.string.toast_api_key_empty, Toast.LENGTH_SHORT).show()
            } else {
                apiKeyRepository.saveApiKey(key)
                Toast.makeText(this, R.string.toast_api_key_saved, Toast.LENGTH_SHORT).show()
            }
        }

        binding.enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, R.string.toast_accessibility_hint, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupApiKeySection() {
        binding.apiKeyInput.setText(apiKeyRepository.getApiKey())
        binding.aiStudioLink.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.gemini_api_url))))
        }
    }

    private fun setupModelSelector() {
        binding.modelSelector.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, GEMINI_MODELS)
        )
        binding.modelSelector.setText(getSelectedModel(this), false)
        binding.modelSelector.setOnItemClickListener { _, _, position, _ ->
            prefs(this).edit().putString(KEY_MODEL, GEMINI_MODELS[position]).apply()
        }
    }

    private fun setupThemeSelector() {
        binding.themeSelector.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, AppTheme.entries.map { it.label })
        )
        binding.themeSelector.setText(AppTheme.forKey(getSelectedThemeKey(this)).label, false)
        binding.themeSelector.setOnItemClickListener { _, _, position, _ ->
            val theme = AppTheme.entries[position]
            if (theme.key != getSelectedThemeKey(this)) {
                prefs(this).edit().putString(KEY_THEME, theme.key).apply()
                recreate()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityStatus()
    }

    private fun checkAccessibilityStatus() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val isEnabled = am
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }

        val dotColor = MaterialColors.getColor(
            binding.statusDot,
            if (isEnabled) R.attr.wwSuccess else com.google.android.material.R.attr.colorError
        )
        binding.statusDot.backgroundTintList = ColorStateList.valueOf(dotColor)
        binding.statusLabel.setText(
            if (isEnabled) R.string.label_service_active else R.string.label_service_inactive
        )
        binding.enableButton.setText(
            if (isEnabled) R.string.label_enabled else R.string.label_enable
        )
    }

    enum class AppTheme(val key: String, val label: String, val styleRes: Int) {
        GITHUB_DARK("github_dark", "GitHub Dark", R.style.Theme_WordWise_GithubDark),
        GITHUB_LIGHT("github_light", "GitHub Light", R.style.Theme_WordWise_GithubLight),
        VSCODE_DARK("vscode_dark", "VS Code Dark", R.style.Theme_WordWise_VscodeDark),
        VSCODE_LIGHT("vscode_light", "VS Code Light", R.style.Theme_WordWise_VscodeLight),
        CLAUDE_DARK("claude_dark", "Claude Dark", R.style.Theme_WordWise_ClaudeDark),
        CLAUDE_LIGHT("claude_light", "Claude Light", R.style.Theme_WordWise_ClaudeLight);

        companion object {
            fun forKey(key: String?): AppTheme =
                entries.firstOrNull { it.key == key } ?: GITHUB_DARK
        }
    }

    companion object {
        private const val PREFS_NAME = "wordwise_prefs"
        private const val KEY_MODEL = "selected_model"
        private const val KEY_THEME = "selected_theme"
        private const val DEFAULT_MODEL = "gemini-3.1-flash-lite"

        // Free-tier Gemini models (Flash / Flash-Lite families only).
        private val GEMINI_MODELS = listOf(
            "gemini-3.1-flash-lite",
            "gemini-3.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash"
        )

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        fun getSelectedModel(context: Context): String =
            prefs(context).getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL

        private fun getSelectedThemeKey(context: Context): String? =
            prefs(context).getString(KEY_THEME, null)
    }
}
