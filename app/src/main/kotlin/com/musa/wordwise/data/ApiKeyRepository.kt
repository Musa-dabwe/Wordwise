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

    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun hasApiKey(): Boolean = getApiKey().isNotEmpty()

    companion object {
        private const val PREFS_NAME = "secret_keys"
        private const val KEY_API_KEY = "api_key"
    }
}
