package com.musa.wordwise.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class ApiKeyRepository(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            // The keyset lives in the Android Keystore and never leaves the
            // device, so prefs restored from a backup (or a corrupted keyset)
            // cannot be decrypted. Wipe and start fresh — the user re-enters
            // the key once instead of the app crashing on every launch.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(PREFS_NAME)
            } else {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
            createEncryptedPrefs()
        }
    }

    @Suppress("DEPRECATION")
    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    /**
     * Checks whether a legacy Gemini key exists from before the OpenCode Zen
     * migration. Used by the one-time migration notice in GrammarFixService.
     * This method should be deleted once the migration notice is removed.
     */
    fun hasLegacyGeminiKey(): Boolean = prefs.contains(KEY_LEGACY_GEMINI)

    /**
     * Removes the legacy Gemini key from encrypted storage.
     * Called after the migration notice is shown.
     */
    fun removeLegacyGeminiKey() {
        prefs.edit().remove(KEY_LEGACY_GEMINI).apply()
    }

    companion object {
        private const val PREFS_NAME = "secret_keys"
        private const val KEY_API_KEY = "api_key_opencode_zen"
        private const val KEY_LEGACY_GEMINI = "api_key_gemini"
    }
}
