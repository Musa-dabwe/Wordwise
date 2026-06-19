package com.musa.wordwise.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.musa.wordwise.network.AiProvider

class ProviderRepository(private val context: Context) {
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

    fun getProvider(): AiProvider {
        val providerName = prefs.getString(KEY_PROVIDER, AiProvider.GEMINI.name)
        return try {
            AiProvider.valueOf(providerName ?: AiProvider.GEMINI.name)
        } catch (e: Exception) {
            AiProvider.GEMINI
        }
    }

    fun saveProvider(provider: AiProvider) {
        prefs.edit().putString(KEY_PROVIDER, provider.name).apply()
    }

    fun getOllamaKey(): String? = prefs.getString(KEY_OLLAMA_KEY, null)

    fun saveOllamaKey(key: String) {
        prefs.edit().putString(KEY_OLLAMA_KEY, key).apply()
    }

    companion object {
        private const val PREFS_NAME = "provider_prefs"
        private const val KEY_PROVIDER = "KEY_PROVIDER"
        private const val KEY_OLLAMA_KEY = "KEY_OLLAMA_KEY"
    }
}
