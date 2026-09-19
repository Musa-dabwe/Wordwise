package com.musa.wordwise

import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SensitiveFieldTest {

    // --- isSensitiveInput (static companion, no Android node needed) ---

    @Test
    fun sensitiveInput_returns_true_for_isPassword_flag() {
        assertTrue(GrammarFixService.isSensitiveInput(isPassword = true, inputType = 0))
    }

    @Test
    fun sensitiveInput_returns_false_for_normal_text_field() {
        val inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        assertFalse(GrammarFixService.isSensitiveInput(isPassword = false, inputType = inputType))
    }

    @Test
    fun sensitiveInput_returns_true_for_text_password_variation() {
        val inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertTrue(GrammarFixService.isSensitiveInput(isPassword = false, inputType = inputType))
    }

    @Test
    fun sensitiveInput_returns_true_for_visible_password_variation() {
        val inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        assertTrue(GrammarFixService.isSensitiveInput(isPassword = false, inputType = inputType))
    }

    @Test
    fun sensitiveInput_returns_true_for_web_password_variation() {
        val inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        assertTrue(GrammarFixService.isSensitiveInput(isPassword = false, inputType = inputType))
    }

    @Test
    fun sensitiveInput_returns_true_for_number_password_variation() {
        val inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        assertTrue(GrammarFixService.isSensitiveInput(isPassword = false, inputType = inputType))
    }

    @Test
    fun sensitiveInput_returns_false_for_number_class_without_password() {
        val inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_NORMAL
        assertFalse(GrammarFixService.isSensitiveInput(isPassword = false, inputType = inputType))
    }

    @Test
    fun sensitiveInput_returns_false_for_email_variation() {
        val inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertFalse(GrammarFixService.isSensitiveInput(isPassword = false, inputType = inputType))
    }

    @Test
    fun sensitiveInput_returns_false_for_phone_variation() {
        val inputType = InputType.TYPE_CLASS_PHONE
        assertFalse(GrammarFixService.isSensitiveInput(isPassword = false, inputType = inputType))
    }

    // --- isSensitiveField (instance method with real AccessibilityNodeInfo) ---

    @Suppress("DEPRECATION")
    @Test
    fun sensitiveField_returns_true_for_password_node() {
        val node = AccessibilityNodeInfo.obtain()
        node.isPassword = true
        assertTrue(GrammarFixService().isSensitiveField(node))
        node.recycle()
    }

    @Suppress("DEPRECATION")
    @Test
    fun sensitiveField_returns_false_for_normal_text_node() {
        val node = AccessibilityNodeInfo.obtain()
        node.isPassword = false
        node.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        assertFalse(GrammarFixService().isSensitiveField(node))
        node.recycle()
    }

    @Suppress("DEPRECATION")
    @Test
    fun sensitiveField_returns_true_for_text_password_node() {
        val node = AccessibilityNodeInfo.obtain()
        node.isPassword = false
        node.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertTrue(GrammarFixService().isSensitiveField(node))
        node.recycle()
    }
}
