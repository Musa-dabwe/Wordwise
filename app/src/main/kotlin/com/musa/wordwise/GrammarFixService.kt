package com.musa.wordwise

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.musa.wordwise.network.FixMode
import com.musa.wordwise.network.fixGrammar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GrammarFixService : AccessibilityService() {
    private var apiKey: String = ""
    
    // Define shortcuts and their modes
    private val shortcuts = mapOf(
        "?fixs" to FixMode.SENTENCE,
        "?fixp" to FixMode.PARAGRAPH,
        "?fixo" to FixMode.ALL
    )
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isProcessing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        apiKey = loadApiKey()
        if (apiKey.isNotEmpty()) {
            Log.d("GrammarFix", "Service connected! API Key loaded: ${apiKey.take(7)}...")
            showToast("WordWise is ready! Commands: ?fixs (sentence), ?fixp (paragraph), ?fixo (all)")
        } else {
            Log.e("GrammarFix", "API Key is missing! Please set it in the app.")
            showToast("WordWise: Please add your API key in the app settings.")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        if (isProcessing) return // Prevent multiple simultaneous requests

        val source = event.source ?: return
        val currentText = event.text.joinToString("")

        // Check which shortcut was used
        val detectedShortcut = shortcuts.keys.find { currentText.endsWith(it) }
        
        if (detectedShortcut != null) {
            val mode = shortcuts[detectedShortcut]!!
            
            // For ?fixo, get all text from the field
            val textToFix = if (mode == FixMode.ALL) {
                getAllTextFromField(source)
            } else {
                // For ?fixs and ?fixp, get text before the shortcut
                currentText.dropLast(detectedShortcut.length).trim()
            }
            
            if (textToFix.isEmpty()) {
                Log.d("GrammarFix", "No text to fix")
                showToast("No text found to fix")
                return
            }

            if (apiKey.isEmpty()) {
                showToast("Please set your API key in WordWise app")
                return
            }

            val modeLabel = when (mode) {
                FixMode.SENTENCE -> "sentence"
                FixMode.PARAGRAPH -> "paragraph"
                FixMode.ALL -> "all text"
            }

            Log.d("GrammarFix", "Shortcut '$detectedShortcut' detected! Mode: $modeLabel")
            Log.d("GrammarFix", "Text to fix: '$textToFix' (${textToFix.length} chars)")
            
            isProcessing = true
            showToast("Fixing $modeLabel...")

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val correctedText = withContext(Dispatchers.IO) {
                        fixGrammar(textToFix, apiKey, mode)
                    }
                    
                    Log.d("GrammarFix", "Got corrected text: '$correctedText'")
                    
                    if (correctedText != textToFix) {
                        replaceText(source, correctedText)
                        showToast("✓ Fixed $modeLabel!")
                    } else {
                        showToast("No changes needed or API error")
                    }
                } catch (e: Exception) {
                    Log.e("GrammarFix", "Error fixing grammar: ${e.message}", e)
                    showToast("Error: ${e.message}")
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    private fun getAllTextFromField(node: AccessibilityNodeInfo): String {
        // Try to get text from the node
        val nodeText = node.text?.toString() ?: ""
        
        // If node has text, remove any shortcuts from it
        var cleanText = nodeText
        shortcuts.keys.forEach { shortcut ->
            if (cleanText.endsWith(shortcut)) {
                cleanText = cleanText.dropLast(shortcut.length)
            }
        }
        
        return cleanText.trim()
    }

    private fun replaceText(node: AccessibilityNodeInfo, newText: String) {
        try {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d("GrammarFix", "Text replacement ${if (success) "succeeded" else "failed"}")
            
            if (!success) {
                // Try alternative method
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
        } catch (e: Exception) {
            Log.e("GrammarFix", "Failed to replace text: ${e.message}", e)
        }
    }

    override fun onInterrupt() {
        Log.d("GrammarFix", "Service interrupted")
    }

    private fun loadApiKey(): String {
        return try {
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
            val key = sharedPreferences.getString("api_key", "") ?: ""
            Log.d("GrammarFix", "Loaded API key: ${if (key.isEmpty()) "EMPTY" else "Present (${key.length} chars)"}")
            key
        } catch (e: Exception) {
            Log.e("GrammarFix", "Failed to load API key: ${e.message}", e)
            ""
        }
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}