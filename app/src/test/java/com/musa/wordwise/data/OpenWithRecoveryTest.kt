package com.musa.wordwise.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

/**
 * The wipe-and-recover path for the encrypted key store.
 *
 * This is the behaviour that saves the user from a permanently crashing app: the
 * master key lives in the Android Keystore and never leaves the device, so a
 * prefs file restored from a backup cannot be decrypted. Rather than failing on
 * every launch, the file is wiped and recreated empty.
 *
 * It is tested here by injection because the real store cannot be built on the
 * JVM at all — Robolectric ships no AndroidKeyStore shadow, so
 * `EncryptedSharedPreferences.create` always throws here. `ApiKeyRepositoryTest`
 * covers the repository's own logic against a plain SharedPreferences, and
 * `ApiKeyRepositoryInstrumentedTest` covers the real keystore on a device. This
 * test covers the recovery decision itself, which nothing else reaches.
 */
class OpenWithRecoveryTest {

    private fun fakePrefs(): SharedPreferences =
        object : SharedPreferences {
            override fun getAll(): MutableMap<String, Any?> = mutableMapOf()
            override fun getString(key: String?, defValue: String?) = defValue
            override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
            override fun getInt(key: String?, defValue: Int) = defValue
            override fun getLong(key: String?, defValue: Long) = defValue
            override fun getFloat(key: String?, defValue: Float) = defValue
            override fun getBoolean(key: String?, defValue: Boolean) = defValue
            override fun contains(key: String?) = false
            override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException("unused")
            override fun registerOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?
            ) = Unit
            override fun unregisterOnSharedPreferenceChangeListener(
                listener: SharedPreferences.OnSharedPreferenceChangeListener?
            ) = Unit
        }

    @Test
    fun `a successful open does not wipe`() {
        val prefs = fakePrefs()
        var wipes = 0
        val result = openWithRecovery(create = { prefs }, wipe = { wipes++ })
        assertSame("should return what create returned", prefs, result)
        assertEquals("wipe must not run on the happy path", 0, wipes)
    }

    @Test
    fun `an unreadable keyset is wiped once and recreated`() {
        val recovered = fakePrefs()
        var attempts = 0
        var wipes = 0

        val result = openWithRecovery(
            create = {
                attempts++
                if (attempts == 1) throw IllegalStateException("keystore unreadable")
                recovered
            },
            wipe = { wipes++ }
        )

        assertSame("should return the second attempt", recovered, result)
        assertEquals("must attempt twice", 2, attempts)
        assertEquals("must wipe exactly once", 1, wipes)
    }

    /**
     * Retrying forever would turn a broken keystore into a hang. The second
     * failure must propagate so the caller sees a real error.
     */
    @Test
    fun `two failures propagate rather than looping`() {
        var attempts = 0
        var wipes = 0
        try {
            openWithRecovery(
                create = { attempts++; throw IllegalStateException("keystore gone") },
                wipe = { wipes++ }
            )
            fail("expected the second failure to propagate")
        } catch (e: IllegalStateException) {
            assertEquals("keystore gone", e.message)
        }
        assertEquals("must not retry forever", 2, attempts)
        assertEquals("wipe runs once, not once per attempt", 1, wipes)
    }

    @Test
    fun `a failing wipe propagates`() {
        try {
            openWithRecovery(
                create = { throw IllegalStateException("first") },
                wipe = { throw IllegalStateException("wipe failed") }
            )
            fail("expected the wipe failure to propagate")
        } catch (e: IllegalStateException) {
            assertEquals("wipe failed", e.message)
        }
    }

    @Test
    fun `the wipe happens before the retry`() {
        val order = mutableListOf<String>()
        openWithRecovery(
            create = {
                order += "create"
                if (order.size == 1) throw IllegalStateException("first")
                fakePrefs()
            },
            wipe = { order += "wipe" }
        )
        assertEquals(listOf("create", "wipe", "create"), order)
    }
}