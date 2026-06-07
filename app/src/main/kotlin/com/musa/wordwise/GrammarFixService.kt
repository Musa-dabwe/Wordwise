package com.musa.wordwise

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.musa.wordwise.data.ApiKeyRepository
import com.musa.wordwise.network.AiClient
import com.musa.wordwise.network.FixMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GrammarFixService : AccessibilityService() {
    private val apiKeyRepository by lazy { ApiKeyRepository(this) }
    
    // Define shortcuts and their modes
    private val shortcuts = mapOf(
        "?fixs" to FixMode.SENTENCE,
        "?fixp" to FixMode.PARAGRAPH,
        "?fixo" to FixMode.ALL
    )
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pendingJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (apiKeyRepository.hasApiKey()) {
            Log.d("GrammarFix", "Service connected! API Key is present.")
            showToast("WordWise is ready! Commands: ?fixs (sentence), ?fixp (paragraph), ?fixo (all)")
        } else {
            Log.e("GrammarFix", "API Key is missing! Please set it in the app.")
            showToast("WordWise: Please add your API key in the app settings.")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val source = event.source ?: return

        // TODO: verify inputType filtering on device
        val inputType = source.inputType
        val typeClass = inputType and android.text.InputType.TYPE_MASK_CLASS
        val typeVariation = inputType and android.text.InputType.TYPE_MASK_VARIATION

        val isSensitive = typeClass == android.text.InputType.TYPE_CLASS_TEXT && (
            typeVariation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            typeVariation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            typeVariation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        ) || typeClass == android.text.InputType.TYPE_CLASS_NUMBER && (
            typeVariation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )

        if (isSensitive) {
            source.recycle()
            return
        }

        // Try multiple extraction sources: node text, node content description, then event text
        val sourceText = source.text?.toString()
        val sourceDescription = source.contentDescription?.toString()
        val eventText = event.text.joinToString("")

        val currentText = when {
            !sourceText.isNullOrBlank() -> sourceText
            !sourceDescription.isNullOrBlank() -> sourceDescription
            !eventText.isBlank() -> eventText
            else -> null
        }

        if (currentText == null) {
            source.recycle()
            return
        }

        // Check which shortcut was used
        val detectedShortcut = shortcuts.keys.find { currentText.endsWith(it) }
        
        if (detectedShortcut != null) {
            val mode = shortcuts[detectedShortcut]!!
            
            // Unify text extraction for all modes
            val textToFix = currentText.dropLast(detectedShortcut.length).trim()
            
            if (textToFix.isEmpty()) {
                Log.d("GrammarFix", "No text to fix")
                showToast("No text found to fix")
                source.recycle()
                return
            }

            if (!apiKeyRepository.hasApiKey()) {
                showToast("Please set your API key in WordWise app")
                source.recycle()
                return
            }

            val modeLabel = when (mode) {
                FixMode.SENTENCE -> "sentence"
                FixMode.PARAGRAPH -> "paragraph"
                FixMode.ALL -> "all text"
            }

            Log.d("GrammarFix", "Shortcut '$detectedShortcut' detected! Mode: $modeLabel")
            Log.d("GrammarFix", "Text to fix: ${textToFix.length} chars, mode: $modeLabel")
            
            showToast("Fixing $modeLabel...")

            pendingJob?.cancel()
            pendingJob = serviceScope.launch {
                try {
                    val correctedText = withContext(Dispatchers.IO) {
                        AiClient.fixGrammar(textToFix, apiKeyRepository.getApiKey(), mode)
                    }
                    
                    Log.d("GrammarFix", "Corrected text received: ${correctedText.length} chars")
                    
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
                    source.recycle()
                }
            }
        } else {
            // Not our shortcut, but we must recycle the node
            source.recycle()
        }
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

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}
