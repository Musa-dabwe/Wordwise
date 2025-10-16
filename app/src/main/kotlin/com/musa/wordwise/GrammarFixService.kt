package com.musa.wordwise

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.musa.wordwise.network.fixGrammar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GrammarFixService : AccessibilityService() {
    private lateinit var apiKey: String
    private val shortcut = "?fixg"

    override fun onServiceConnected() {
        apiKey = loadApiKey()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val source = event.source ?: return
        val text = source.text?.toString() ?: return

        if (text.endsWith(shortcut)) {
            val originalText = text.removeSuffix(shortcut).trim()

            CoroutineScope(Dispatchers.IO).launch {
                val correctedText = fixGrammar(originalText, apiKey)

                withContext(Dispatchers.Main) {
                    replaceText(source, correctedText)
                }
            }
        }
    }

    private fun replaceText(node: AccessibilityNodeInfo, newText: String) {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    override fun onInterrupt() {}

    private fun loadApiKey(): String {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            this,
            "secret_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        return sharedPreferences.getString("api_key", "") ?: ""
    }
}