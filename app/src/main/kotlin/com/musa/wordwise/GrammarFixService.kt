// Copyright 2026 Fackson Mutetesha (Musa-dabwe)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

package com.musa.wordwise

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.musa.wordwise.data.ApiKeyRepository
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
    private val shortcutRegex = Regex("""\?fix\s*$""")
    private val askRegex = Regex("""\?ask\s*$""", RegexOption.IGNORE_CASE)
    private val LARGE_TEXT_THRESHOLD = 1000 // characters
    private val LARGE_TEXT_THRESHOLD_ASK_WORDS = 10_000

    private val SPINNER_FRAMES = arrayOf("◴", "◷", "◶", "◵")
    private var spinnerRunnable: Runnable? = null
    private var spinnerNode: AccessibilityNodeInfo? = null
    private var spinnerToken = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pendingJob: Job? = null

    private sealed class Command {
        data class Fix(val text: String) : Command()
        data class Ask(val prompt: String) : Command()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected!")
        showMigrationNoticeIfNeeded()
        showToast(getString(R.string.toast_service_ready))
    }

    /**
     * One-time migration notice: if a legacy OpenCode Zen key exists but no
     * OpenRouter key is configured, tell the user to add a new key.
     * Shows once per install/update, then cleans up the old key.
     */
    private fun showMigrationNoticeIfNeeded() {
        val prefs = getSharedPreferences("wordwise_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("migration_notice_shown", false)) return

        if (apiKeyRepository.hasLegacyZenKey() && !apiKeyRepository.hasApiKey()) {
            showToast(
                "WordWise now uses OpenRouter. " +
                "Your old OpenCode Zen key is no longer used — add your OpenRouter key in settings.",
                long = true
            )
            prefs.edit().putBoolean("migration_notice_shown", true).apply()
            apiKeyRepository.removeLegacyZenKey()
        }
    }

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

        val command = currentText?.let { detectCommand(it) }
        if (command == null) {
            source.safeRecycle()
            return
        }

        val textForAi = when (command) {
            is Command.Fix -> command.text
            is Command.Ask -> command.prompt
        }

        if (textForAi.isEmpty()) {
            val toastRes = when (command) {
                is Command.Fix -> R.string.toast_no_text
                is Command.Ask -> R.string.toast_ask_no_text
            }
            showToast(getString(toastRes))
            source.safeRecycle()
            return
        }

        Log.d(TAG, "Command detected — ${textForAi.length} chars to process")

        pendingJob?.cancel()
        val token = startSpinner(textForAi, source)

        pendingJob = serviceScope.launch {
            try {
                if (command is Command.Ask) {
                    val wordCount = countWords(textForAi)
                    if (wordCount > LARGE_TEXT_THRESHOLD_ASK_WORDS) {
                        showToast(getString(R.string.warning_large_text_ask))
                    }
                } else if (command is Command.Fix && textForAi.length > LARGE_TEXT_THRESHOLD) {
                    showToast(getString(R.string.warning_large_text))
                }

                val apiKey = apiKeyRepository.getApiKey()
                if (apiKey.isEmpty()) {
                    showMigrationNoticeIfNeeded()
                    showToast(getString(R.string.toast_api_key_missing), long = true)
                    stopSpinner(token)
                    source.safeRecycle()
                    return@launch
                }

                val result = when (command) {
                    is Command.Fix -> AiClient.fixGrammar(textForAi, apiKey)
                    is Command.Ask -> AiClient.ask(textForAi, apiKey)
                }

                stopSpinner(token)

                when (result) {
                    is AiClient.Result.Success -> {
                        val unchanged = result.text == textForAi
                        val isEmpty = result.text.isBlank()

                        if (unchanged || isEmpty) {
                            replaceText(source, textForAi)
                            val unchangedToast = when (command) {
                                is Command.Fix -> R.string.error_unchanged
                                is Command.Ask -> R.string.toast_ask_unchanged
                            }
                            showToast(getString(unchangedToast), long = true)
                        } else {
                            if (command is Command.Ask) {
                                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("WordWise prompt", textForAi)
                                clipboard.setPrimaryClip(clip)
                            }
                            replaceText(source, result.text)
                            val toastRes = when (command) {
                                is Command.Fix -> R.string.toast_fixed
                                is Command.Ask -> R.string.toast_ask_done
                            }
                            showToast(getString(toastRes))
                        }
                    }
                    is AiClient.Result.RateLimited -> {
                        replaceText(source, textForAi)
                        showToast(result.message, long = true)
                    }
                    is AiClient.Result.Failure -> {
                        replaceText(source, textForAi)
                        val errorRes = when (command) {
                            is Command.Fix -> R.string.error_correction_failed
                            is Command.Ask -> R.string.error_ask_failed
                        }
                        showToast(getString(errorRes, result.error), long = true)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                stopSpinner(token)
                replaceText(source, textForAi)
                Log.e(TAG, "Error processing command: ${e.message}", e)
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

    private fun detectCommand(text: String): Command? {
        return when {
            shortcutRegex.containsMatchIn(text) -> Command.Fix(
                text.replace(shortcutRegex, "").trim()
            )
            askRegex.containsMatchIn(text) -> Command.Ask(
                text.replace(askRegex, "").trim()
            )
            else -> null
        }
    }

    private fun countWords(text: String): Int =
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }.size

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
