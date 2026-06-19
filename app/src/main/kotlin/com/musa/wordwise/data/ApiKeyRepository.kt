package com.musa.wordwise.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class ApiKeyRepository(private val context: Context) {
    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(provider: Provider, key: String) {
        val prefKey = when (provider) {
            Provider.OLLAMA -> KEY_API_KEY_OLLAMA
            Provider.GEMINI -> KEY_API_KEY_GEMINI
        }
        prefs.edit().putString(prefKey, key).apply()
    }

    fun getApiKey(provider: Provider): String {
        val prefKey = when (provider) {
            Provider.OLLAMA -> KEY_API_KEY_OLLAMA
            Provider.GEMINI -> KEY_API_KEY_GEMINI
        }
        return prefs.getString(prefKey, "") ?: ""
    }

    fun saveActiveProvider(provider: Provider) {
        prefs.edit().putString(KEY_ACTIVE_PROVIDER, provider.name).apply()
    }

    fun getActiveProvider(): Provider {
        val saved = prefs.getString(KEY_ACTIVE_PROVIDER, null)
        return if (saved != null) {
            runCatching { Provider.valueOf(saved) }.getOrDefault(Provider.OLLAMA)
        } else {
            Provider.OLLAMA
        }
    }

    companion object {
        private const val PREFS_NAME = "secret_keys"
        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_API_KEY_OLLAMA  = "api_key_ollama"
        private const val KEY_API_KEY_GEMINI  = "api_key_gemini"
    }
}
