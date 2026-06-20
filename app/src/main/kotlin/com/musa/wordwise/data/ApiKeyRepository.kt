package com.musa.wordwise.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class ApiKeyRepository(private val context: Context) {
    private val prefs by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        @Suppress("DEPRECATION")
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY_GEMINI, key).apply()
    }

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY_GEMINI, "") ?: ""
    }

    companion object {
        private const val PREFS_NAME       = "secret_keys"
        private const val KEY_API_KEY_GEMINI = "api_key_gemini"
    }
}
