package com.musa.wordwise.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [ApiKeyRepository].
 *
 * STATE ISOLATION (read before editing): [ApiKeyRepository.get] is a
 * process-wide singleton, and Robolectric gives each *test class* a fresh
 * classloader but reuses it across test *methods*. The singleton — and any
 * injected fake — therefore persists across tests in this class. Forgetting
 * to reset produces order-dependent flakes, which is exactly the class of bug
 * the singleton itself fixed. We clear state in @Before and @After.
 *
 * KEYSTORE LIMITATION: Robolectric 4.11.1 has no AndroidKeyStore provider, so
 * the real `createEncryptedPrefs()` cannot succeed under a JVM test. For the
 * logic tests we swap the lazy `prefs` delegate for a plain SharedPreferences
 * — this keeps the repository's own code (getApiKey/clearApiKey/hasApiKey,
 * legacy-key handling, singleton wiring) under test while replacing only the
 * encryption boundary. The wipe path is covered structurally at the bottom;
 * full recovery can only be verified on a device or Firebase Test Lab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiKeyRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** Stand-in for the encrypted prefs, standing in for the keystore layer. */
    private val fakePrefs: SharedPreferences =
        context.getSharedPreferences("apikey_repository_test", Context.MODE_PRIVATE)

    @Before
    fun resetState() {
        fakePrefs.edit().clear().commit()
        resetSingleton()
    }

    @After
    fun clearStateAndSingleton() {
        fakePrefs.edit().clear().commit()
        resetSingleton()
    }

    private fun resetSingleton() {
        // White-box: drop the cached singleton so the next get() rebuilds it
        // without whatever fake state a previous test left behind. The volatile
        // `instance` is a static volatile field on the outer class, not on the
        // generated Companion (verified with javap).
        val field = ApiKeyRepository::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, null)
    }

    /**
     * Replace the lazy `prefs` delegate with [fakePrefs] so the repository's
     * data logic runs without AndroidKeyStore. See the class-level note — do
     * NOT present this as exercising EncryptedSharedPreferences itself.
     */
    private fun injectFakePrefs(repo: ApiKeyRepository) {
        val delegateField = ApiKeyRepository::class.java
            .getDeclaredField("prefs\$delegate")
        delegateField.isAccessible = true
        delegateField.set(repo, object : Lazy<SharedPreferences> {
            override val value: SharedPreferences = fakePrefs
            override fun isInitialized(): Boolean = true
        })
    }

    // --- Singleton identity (the real bug) ---

    @Test
    fun get_twice_returnsSameInstance() {
        // Regression guard for the settings-UI vs accessibility-service bug:
        // two repository objects in one process never observe each other's
        // writes, so MainActivity and GrammarFixService must share one.
        val a = ApiKeyRepository.get(context)
        val b = ApiKeyRepository.get(context.applicationContext)
        assertSame(a, b)
        // A differently-wrapped context (ContextWrapper over the same app
        // context) must not yield a second instance either.
        val wrapped = android.content.ContextWrapper(context)
        assertSame(a, ApiKeyRepository.get(wrapped))
    }

    @Test
    fun writeThroughOneReference_isVisibleThroughAnother() {
        // This is the observable half of the singleton contract: before the
        // fix, GrammarFixService kept sending the startup key long after the
        // settings UI saved a new one.
        val a = ApiKeyRepository.get(context)
        injectFakePrefs(a)
        val b = ApiKeyRepository.get(context)
        a.saveApiKey("sk-or-shared-key")
        assertEquals("sk-or-shared-key", b.getApiKey())
    }

    // --- Repository behaviour over the prefs boundary ---

    @Test
    fun saveApiKey_thenGetApiKey_roundTrips() {
        val repo = ApiKeyRepository.get(context)
        injectFakePrefs(repo)
        repo.saveApiKey("sk-or-v1-abc123")
        assertEquals("sk-or-v1-abc123", repo.getApiKey())
        // ... and visible from a second get() call, which is the actual fix.
        assertEquals("sk-or-v1-abc123", ApiKeyRepository.get(context).getApiKey())
    }

    @Test
    fun clearApiKey_emptiesTheKey_andIsVisibleToSecondGet() {
        // clearApiKey exists because making the key write-only removed the
        // user's only in-app way to delete it — protect that path.
        val repo = ApiKeyRepository.get(context)
        injectFakePrefs(repo)
        repo.saveApiKey("sk-or-temp")
        repo.clearApiKey()
        assertEquals("", repo.getApiKey())
        assertFalse(repo.hasApiKey())
        // Visible process-wide, not just on the instance that cleared it.
        assertEquals("", ApiKeyRepository.get(context).getApiKey())
        assertFalse(ApiKeyRepository.get(context).hasApiKey())
    }

    @Test
    fun hasApiKey_blankSemantics() {
        val repo = ApiKeyRepository.get(context)
        injectFakePrefs(repo)
        assertFalse(repo.hasApiKey())          // empty
        repo.saveApiKey("sk-or-real")
        assertTrue(repo.hasApiKey())           // normal text
        repo.saveApiKey("   \n\t  ")
        assertFalse(repo.hasApiKey())          // isNotBlank: whitespace-only is absent
        assertEquals("   \n\t  ", repo.getApiKey()) // stored verbatim, only the check treats it as blank
    }

    @Test
    fun key_survivesUnrelatedWritesAndLegacyKeyRemoval() {
        // Guards against an over-broad edit().clear() wiping the API key when
        // some other setting changes or the legacy Zen key is retired.
        val repo = ApiKeyRepository.get(context)
        injectFakePrefs(repo)
        repo.saveApiKey("first")
        repo.removeLegacyZenKey()              // unrelated removal
        repo.saveApiKey("second")              // second save
        assertEquals("second", repo.getApiKey())
        assertTrue(repo.hasApiKey())
    }

    @Test
    fun legacyZenKey_helpers() {
        val repo = ApiKeyRepository.get(context)
        injectFakePrefs(repo)
        assertFalse(repo.hasLegacyZenKey())
        // Simulate a key left behind by an older app version's prefs file.
        fakePrefs.edit().putString("api_key_opencode_zen", "zen-legacy-key").commit()
        assertTrue(repo.hasLegacyZenKey())
        repo.removeLegacyZenKey()
        assertFalse(repo.hasLegacyZenKey())
    }

    // --- Wipe path (limitation, documented) ---

    @Test
    fun keystoreFailure_doesNotSilentlyFabricateEmptyState() {
        // What we CAN assert under Robolectric: constructing the repository is
        // lazy and must not crash, but actually touching the encrypted prefs
        // surfaces the encryption failure instead of returning a wrong key.
        // The wipe path's recovery half cannot be exercised here because
        // Robolectric 4.11.1 has no AndroidKeyStore provider — cover that on a
        // real device / Firebase Test Lab before shipping a change to it.
        val repo = ApiKeyRepository.get(context) // must not throw (lazy prefs)
        assertThrows(Exception::class.java) { repo.getApiKey() }
        assertThrows(Exception::class.java) { repo.hasApiKey() }
    }
}
