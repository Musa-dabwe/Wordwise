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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GrammarFixService : AccessibilityService() {
    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.safeRecycle() = recycle()

    private val apiKeyRepository by lazy { ApiKeyRepository(this) }
    
    private val shortcut = "?fix"
    // Regex anchor $ ensures ?fixs, ?fixp, ?fixo do not trigger this
    private val shortcutRegex = Regex("""\?fix$""")
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pendingJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (apiKeyRepository.hasApiKey()) {
            Log.d("GrammarFix", "Service connected! API Key is present.")
            showToast("WordWise is ready! Type ?fix at the end of your text.")
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
            source.safeRecycle()
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
            source.safeRecycle()
            return
        }

        // Check which shortcut was used
        if (shortcutRegex.containsMatchIn(currentText)) {
            // Unify text extraction
            val textToFix = currentText.dropLast(shortcut.length).trim()
            
            if (textToFix.isEmpty()) {
                Log.d("GrammarFix", "No text to fix")
                showToast("No text found to fix")
                source.safeRecycle()
                return
            }

            if (!apiKeyRepository.hasApiKey()) {
                showToast("Please set your API key in WordWise app")
                source.safeRecycle()
                return
            }

            Log.d("GrammarFix", "Shortcut '$shortcut' detected!")
            Log.d("GrammarFix", "Text to fix: ${textToFix.length} chars")
            
            showToast("Fixing...")

            pendingJob?.cancel()
            pendingJob = serviceScope.launch {
                try {
                    // Strip shortcut immediately before API call
                    val stripped = replaceText(source, textToFix)
                    if (!stripped) {
                        // replaceText already showed toast and logged warning
                        return@launch
                    }

                    val correctedText = AiClient.fixGrammar(textToFix, apiKeyRepository.getApiKey())
                    
                    Log.d("GrammarFix", "Corrected text received: ${correctedText.length} chars")
                    
                    if (correctedText != textToFix) {
                        replaceText(source, correctedText)
                        showToast("✓ Fixed!")
                    } else {
                        showToast("Could not reach Gemini — shortcut removed, text unchanged.")
                    }
                } catch (e: Exception) {
                    Log.e("GrammarFix", "Error fixing grammar: ${e.message}", e)
                    showToast("Error: ${e.message}")
                } finally {
                    source.safeRecycle()
                }
            }
        } else {
            // Not our shortcut, but we must recycle the node
            source.safeRecycle()
        }
    }

    private fun replaceText(node: AccessibilityNodeInfo, newText: String): Boolean {
        return try {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            Log.d("GrammarFix", "Text replacement ${if (success) "succeeded" else "failed"}")
            
            if (!success) {
                Log.w("GrammarFix", "ACTION_SET_TEXT failed on ${node.className}")
                showToast("Could not replace text in this field. Try a standard text input field.")
            }
            success
        } catch (e: Exception) {
            Log.e("GrammarFix", "Failed to replace text: ${e.message}", e)
            false
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
