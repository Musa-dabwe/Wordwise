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
import android.text.InputType
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.musa.wordwise.data.ApiKeyRepository
import com.musa.wordwise.data.Prefs
import com.musa.wordwise.network.AiClient
import kotlinx.coroutines.CancellationException
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
    private var spinnerToken = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pendingJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected!")
        showToast(getString(R.string.toast_service_ready))
    }

    /**
     * Processes text changes containing the grammar-correction shortcut.
     *
     * Sensitive fields and events without the shortcut are ignored. Matching text is
     * corrected asynchronously and replaced with the result when processing completes.
     *
     * @param event The accessibility event containing the changed text.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val source = event.source ?: return

        if (isSensitiveField(source)) {
            source.safeRecycle()
            return
        }

        val sourceText = source.text?.toString()
        val sourceDescription = source.contentDescription?.toString()
        val eventText = event.text.joinToString("")

        val currentText = when {
            !sourceText.isNullOrBlank() -> sourceText
            !sourceDescription.isNullOrBlank() -> sourceDescription
            eventText.isNotBlank() -> eventText
            else -> null
        }

        if (currentText == null || !shortcutRegex.containsMatchIn(currentText)) {
            source.safeRecycle()
            return
        }

        val textToFix = currentText.dropLast(shortcut.length).trim()

        if (textToFix.isEmpty()) {
            showToast(getString(R.string.toast_no_text))
            source.safeRecycle()
            return
        }

        val apiKey = apiKeyRepository.getApiKey()
        if (apiKey.isEmpty()) {
            showToast(getString(R.string.toast_api_key_missing), long = true)
            source.safeRecycle()
            return
        }

        Log.d(TAG, "Shortcut '$shortcut' detected — ${textToFix.length} chars to fix")

        pendingJob?.cancel()
        val token = startSpinner(textToFix, source)

        pendingJob = serviceScope.launch {
            try {
                if (textToFix.length > LARGE_TEXT_THRESHOLD) {
                    showToast(getString(R.string.warning_large_text))
                }

                val model = Prefs.getSelectedModel(this@GrammarFixService)
                val result = AiClient.fixGrammar(textToFix, apiKey, model)

                stopSpinner(token)

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
                        showToast(getString(R.string.error_correction_failed, result.error), long = true)
                    }
                }
            } catch (e: CancellationException) {
                // A newer ?fix superseded this job — it owns the field now.
                throw e
            } catch (e: Exception) {
                stopSpinner(token)
                replaceText(source, textToFix)
                Log.e(TAG, "Error fixing grammar: ${e.message}", e)
                showToast(getString(R.string.error_correction_failed, e.message ?: "unknown"), long = true)
            } finally {
                stopSpinner(token)
                source.safeRecycle()
            }
        }
    }

    private fun isSensitiveField(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true

        val inputType = node.inputType
        val typeClass = inputType and InputType.TYPE_MASK_CLASS
        val typeVariation = inputType and InputType.TYPE_MASK_VARIATION

        return typeClass == InputType.TYPE_CLASS_TEXT && (
            typeVariation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            typeVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            typeVariation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        ) || typeClass == InputType.TYPE_CLASS_NUMBER &&
            typeVariation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun replaceText(
        node: AccessibilityNodeInfo,
        newText: String,
        notifyFailure: Boolean = true
    ): Boolean {
        return try {
            val arguments = Bundle()
            arguments.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                newText
            )
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

            if (!success) {
                Log.w(TAG, "ACTION_SET_TEXT failed on ${node.className}")
                if (notifyFailure) {
                    showToast(getString(R.string.error_replace_failed))
                }
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to replace text: ${e.message}", e)
            false
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSpinner(spinnerToken)
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

    /**
     * Starts the inline spinner and returns a token identifying this run.
     * Pass the token to [stopSpinner] — a stale token is ignored, so a
     * cancelled job can never kill the spinner a newer job started.
     */
    @Suppress("DEPRECATION")
    private fun startSpinner(baseText: String, node: AccessibilityNodeInfo): Int {
        stopSpinner(spinnerToken)

        val token = ++spinnerToken
        spinnerNode = AccessibilityNodeInfo.obtain(node)
        var frame = 0

        val runnable = object : Runnable {
            override fun run() {
                spinnerNode?.let {
                    replaceText(it, "$baseText ${SPINNER_FRAMES[frame % SPINNER_FRAMES.size]}", notifyFailure = false)
                    frame++
                    mainHandler.postDelayed(this, 300)
                }
            }
        }
        spinnerRunnable = runnable
        mainHandler.post(runnable)
        return token
    }

    private fun stopSpinner(token: Int) {
        if (token != spinnerToken) return
        spinnerRunnable?.let { mainHandler.removeCallbacks(it) }
        spinnerRunnable = null
        spinnerNode?.safeRecycle()
        spinnerNode = null
    }

    private companion object {
        const val TAG = "GrammarFix"
    }
}
