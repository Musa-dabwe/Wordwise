// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise.bridge

import android.content.Context
import com.musa.wordwise.data.ApiKeyRepository
import com.musa.wordwise.data.Prefs
import com.musa.wordwise.network.ModelId

/**
 * The production [KeyStore], backed by `EncryptedSharedPreferences`.
 *
 * Uses the process-wide [ApiKeyRepository] singleton rather than a fresh
 * instance: `EncryptedSharedPreferences` caches values in memory per instance
 * and does not observe another instance's writes, so the accessibility service
 * would otherwise keep using the key it read at startup after the user replaced
 * or removed it in settings.
 */
class EncryptedKeyStore(context: Context) : KeyStore {

    private val repository = ApiKeyRepository.get(context.applicationContext)

    override fun hasKey(): Boolean = repository.hasApiKey()

    override fun saveKey(key: String) = repository.saveApiKey(key)

    override fun clearKey() = repository.clearApiKey()
}

/** The production [AppSettings], backed by plain `SharedPreferences`. */
class PrefsSettings(context: Context) : AppSettings {

    private val appContext = context.applicationContext

    override fun model(): String = Prefs.getModel(appContext)

    override fun setModel(validated: ModelId.Result.Valid) = Prefs.setModel(appContext, validated)

    override fun theme(): String = Prefs.getTheme(appContext)

    override fun setTheme(key: String) = Prefs.setTheme(appContext, key)
}