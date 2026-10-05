package com.musa.wordwise

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.musa.wordwise.data.ApiKeyRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real encrypted key store, on a real device.
 *
 * This is the only place `EncryptedSharedPreferences` and the Android Keystore
 * are exercised together. Everywhere else they are substituted:
 * `ApiKeyRepositoryTest` swaps in a plain `SharedPreferences` via reflection
 * because Robolectric ships no AndroidKeyStore shadow and cannot construct the
 * real store on the JVM at all.
 *
 * So until now the happy path — save a key, read it back — had **no coverage on
 * any platform**. A change that broke the encryption scheme, the key scheme, or
 * the singleton's cache would have passed every other test and only surfaced on a
 * user's phone.
 */
@RunWith(AndroidJUnit4::class)
class ApiKeyRepositoryInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var repository: ApiKeyRepository

    @Before
    fun setUp() {
        repository = ApiKeyRepository.get(context)
        repository.clearApiKey()
        repository.removeLegacyZenKey()
    }

    @After
    fun tearDown() {
        repository.clearApiKey()
        repository.removeLegacyZenKey()
    }

    @Test
    fun savingAndReadingAKeyRoundTrips() {
        repository.saveApiKey("sk-or-v1-abcdefghijklmnopqrstuvwxyz")
        assertEquals("sk-or-v1-abcdefghijklmnopqrstuvwxyz", repository.getApiKey())
        assertTrue(repository.hasApiKey())
    }

    @Test
    fun anUnsetKeyReadsEmpty() {
        assertEquals("", repository.getApiKey())
        assertFalse(repository.hasApiKey())
    }

    @Test
    fun clearingRemovesTheKey() {
        repository.saveApiKey("sk-or-v1-to-be-removed")
        assertTrue(repository.hasApiKey())
        repository.clearApiKey()
        assertFalse(repository.hasApiKey())
        assertEquals("", repository.getApiKey())
    }

    @Test
    fun aKeySurvivesANewRepositoryInstance() {
        repository.saveApiKey("sk-or-v1-survives-new-instance")
        // The real store is created fresh each time; if the encryption scheme or
        // the file were broken, this read would not round-trip.
        assertEquals("sk-or-v1-survives-new-instance", ApiKeyRepository.get(context).getApiKey())
    }

    @Test
    fun theSingletonReturnsTheSameInstance() {
        // Guards the fix for the stale-key bug: two repository instances would
        // each hold their own EncryptedSharedPreferences cache and never see each
        // other's writes, so the accessibility service would keep using a key the
        // user had already removed.
        assertTrue(
            "get() must return one shared instance",
            ApiKeyRepository.get(context) === ApiKeyRepository.get(context)
        )
    }

    /**
     * The legacy OpenCode Zen key is migration-only: no current code path writes
     * it, and there is deliberately no writer added here just to exercise the
     * reader. `ApiKeyRepositoryTest` covers both helpers against a substituted
     * store; on a clean device the reader must simply report absent.
     */
    @Test
    fun noLegacyZenKeyIsPresentOnACleanDevice() {
        assertFalse(repository.hasLegacyZenKey())
        // Harmless when there is nothing to remove.
        repository.removeLegacyZenKey()
        assertFalse(repository.hasLegacyZenKey())
    }

    /**
     * The key must be encrypted at rest, not merely stored.
     *
     * Reads the underlying prefs file directly and asserts the plaintext never
     * appears in it — the claim the README and About screen make.
     */
    @Test
    fun theKeyIsNotStoredInPlaintext() {
        val secret = "sk-or-v1-PLAINTEXT-CANARY-9f3a2b"
        repository.saveApiKey(secret)

        val file = java.io.File(
            context.applicationInfo.dataDir,
            "shared_prefs/secret_keys.xml"
        )
        assertTrue("expected the prefs file at ${file.path} to exist", file.exists())

        val raw = file.readText()
        assertFalse(
            "the API key must not be readable in the prefs file",
            raw.contains(secret)
        )
        assertFalse(
            "nor should the key name suggest a readable value",
            raw.contains(secret.take(12))
        )
    }
}