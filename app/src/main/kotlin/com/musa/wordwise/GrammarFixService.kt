// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

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
    private val shortcutRegex = Regex("""\?fix$""")
    private val LARGE_TEXT_THRESHOLD = 1000 // characters
    
    private val SPINNER_FRAMES = arrayOf("◴", "◷", "◶", "◵")
    private var spinnerRunnable: Runnable? = null
    private var spinnerNode: AccessibilityNodeInfo? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pendingJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("GrammarFix", "Service connected!")
        showToast("WordWise is ready! Type ?fix at the end of your text.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val source = event.source ?: return

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

        if (shortcutRegex.containsMatchIn(currentText)) {
            val textToFix = currentText.dropLast(shortcut.length).trim()
            
            if (textToFix.isEmpty()) {
                Log.d("GrammarFix", "No text to fix")
                showToast("No text found to fix")
                source.safeRecycle()
                return
            }

            val apiKey = apiKeyRepository.getApiKey()

            if (apiKey.isEmpty()) {
                showToast(getString(R.string.toast_api_key_empty), long = true)
                source.safeRecycle()
                return
            }

            Log.d("GrammarFix", "Shortcut '$shortcut' detected!")
            Log.d("GrammarFix", "Text to fix: ${textToFix.length} chars")
            
            startSpinner(textToFix, source)

            pendingJob?.cancel()
            pendingJob = serviceScope.launch {
                try {
                    if (textToFix.length > LARGE_TEXT_THRESHOLD) {
                        showToast(getString(R.string.warning_large_text))
                    }

                    val model = MainActivity.getSelectedModel(this@GrammarFixService)
                    val result = AiClient.fixGrammar(textToFix, apiKey, model)

                    stopSpinner()

                    when (result) {
                        is AiClient.Result.Success -> {
                            if (result.text != textToFix) {
                                replaceText(source, result.text)
                                showToast(getString(R.string.toast_fixed))
                            } else {
                                replaceText(source, textToFix)
                                showToast(getString(R.string.error_unchanged), long = true)
                            }
                        }
                        is AiClient.Result.RateLimited -> {
                            replaceText(source, textToFix)
                            showToast(result.message, long = true)
                        }
                        is AiClient.Result.Failure -> {
                            replaceText(source, textToFix)
                            showToast("Correction failed: ${result.error}", long = true)
                        }
                    }
                } catch (e: Exception) {
                    stopSpinner()
                    replaceText(source, textToFix)
                    Log.e("GrammarFix", "Error fixing grammar: ${e.message}", e)
                    showToast("Error: ${e.message}", long = true)
                } finally {
                    stopSpinner()
                    source.safeRecycle()
                }
            }
        } else {
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

    private fun showToast(message: String, long: Boolean = false) {
        mainHandler.post {
            Toast.makeText(
                this,
                message,
                if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startSpinner(baseText: String, node: AccessibilityNodeInfo) {
        stopSpinner()

        val nodeCopy = AccessibilityNodeInfo.obtain(node)
        spinnerNode = nodeCopy
        var frame = 0

        val runnable = object : Runnable {
            override fun run() {
                spinnerNode?.let {
                    replaceText(it, "$baseText ${SPINNER_FRAMES[frame % SPINNER_FRAMES.size]}")
                    frame++
                    mainHandler.postDelayed(this, 300)
                }
            }
        }
        spinnerRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun stopSpinner() {
        spinnerRunnable?.let { mainHandler.removeCallbacks(it) }
        spinnerRunnable = null
        spinnerNode?.safeRecycle()
        spinnerNode = null
    }
}
