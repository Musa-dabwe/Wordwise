package com.musa.wordwise.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Opens the encrypted store, wiping the file once if it cannot be created.
 *
 * Extracted for testability, and that is the whole reason it exists. The real
 * store is built on `EncryptedSharedPreferences`, which **cannot be constructed on
 * the JVM at all** — Robolectric ships no AndroidKeyStore shadow — so testing the
 * recovery path through the real implementation was impossible. Injecting both
 * halves makes the recovery itself provable as a plain unit test, while
 * `ApiKeyRepositoryInstrumentedTest` covers the real keystore on a device.
 *
 * Without the wipe, an unreadable keyset would crash the app on every launch.
 * With it, the user re-enters the key once.
 *
 * Deliberately retries only once: if a second `create` also fails, the error
 * propagates rather than looping.
 */
internal fun openWithRecovery(
    create: () -> SharedPreferences,
    wipe: () -> Unit
): SharedPreferences = try {
    create()
} catch (e: Exception) {
    wipe()
    create()
}

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
        openWithRecovery(create = ::createEncryptedPrefs, wipe = ::wipePrefsFile)
    }

    /**
     * Drops the stored file so a fresh keyset can be created.
     *
     * The master key lives in the Android Keystore and never leaves the device,
     * so a prefs file restored from a backup — or one whose keyset is otherwise
     * unreadable — cannot be decrypted.
     */
    private fun wipePrefsFile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appContext.deleteSharedPreferences(PREFS_NAME)
        } else {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit()
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
