package com.musa.wordwise.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted access to the OpenRouter API key.
 *
 * **Must be obtained through [get].** `EncryptedSharedPreferences` keeps an
 * in-memory map built when the instance is created and does not observe writes
 * from another instance, so two `ApiKeyRepository` objects in the same process
 * would not see each other's saves or deletions. That mattered once the settings
 * UI started writing the key: the accessibility service would keep sending
 * requests with the key it had read at startup, long after the user replaced or
 * removed it.
 */
class ApiKeyRepository private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            // The keyset lives in the Android Keystore and never leaves the
            // device, so prefs restored from a backup (or a corrupted keyset)
            // cannot be decrypted. Wipe and start fresh — the user re-enters
            // the key once instead of the app crashing on every launch.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appContext.deleteSharedPreferences(PREFS_NAME)
            } else {
                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().clear().commit()
            }
            createEncryptedPrefs()
        }
    }

    @Suppress("DEPRECATION")
    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
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
     * Deletes the stored key.
     *
     * Required because the key is write-only from the UI's point of view: making
     * it unreadable also removed the user's only in-app way to remove it.
     */
    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    fun hasLegacyZenKey(): Boolean = prefs.contains(KEY_LEGACY_ZEN)

    fun removeLegacyZenKey() {
        prefs.edit().remove(KEY_LEGACY_ZEN).apply()
    }

    companion object {
        private const val PREFS_NAME = "secret_keys"
        private const val KEY_API_KEY = "api_key_openrouter"
        private const val KEY_LEGACY_ZEN = "api_key_opencode_zen"

        @Volatile private var instance: ApiKeyRepository? = null

        /**
         * The process-wide repository. Double-checked so the accessibility
         * service and the settings UI share one `EncryptedSharedPreferences`
         * cache and therefore see each other's writes immediately.
         */
        fun get(context: Context): ApiKeyRepository =
            instance ?: synchronized(this) {
                instance ?: ApiKeyRepository(context).also { instance = it }
            }
    }
}
