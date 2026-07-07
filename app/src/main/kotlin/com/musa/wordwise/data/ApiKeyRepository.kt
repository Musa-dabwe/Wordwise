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
        prefs.edit().putString(KEY_API_KEY_GEMINI, key).apply()
    }

    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY_GEMINI, "") ?: ""
    }

    companion object {
        private const val PREFS_NAME = "secret_keys"
        private const val KEY_API_KEY_GEMINI = "api_key_gemini"
    }
}
