package com.musa.wordwise

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.musa.wordwise.data.ApiKeyRepository
import com.musa.wordwise.databinding.ActivityMainBinding
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val apiKeyRepository by lazy { ApiKeyRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupApiKeySection()
        setupModelSelector()
        setupSaveButton()
        setupHowToUse()

        binding.enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, R.string.toast_accessibility_hint, Toast.LENGTH_LONG).show()
        }
    }

    private fun setupApiKeySection() {
        binding.apiKeyInput.setText(apiKeyRepository.getApiKey())

        val linkText = getString(R.string.label_ai_studio_link)
        val spannable = SpannableString(linkText)
        val url = getString(R.string.gemini_api_url)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(view: View) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }
            override fun updateDrawState(ds: android.text.TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
                ds.color = ContextCompat.getColor(this@MainActivity, R.color.ww_primary)
            }
        }

        spannable.setSpan(clickableSpan, 0, linkText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.aiStudioLink.text = spannable
        binding.aiStudioLink.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun setupModelSelector() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, GEMINI_MODELS)
        binding.modelSelector.setAdapter(adapter)

        val saved = getSharedPreferences("wordwise_prefs", MODE_PRIVATE)
            .getString("selected_model", GEMINI_MODELS[0])
        binding.modelSelector.setText(saved, false)

        binding.modelSelector.setOnItemClickListener { _, _, position, _ ->
            getSharedPreferences("wordwise_prefs", MODE_PRIVATE)
                .edit()
                .putString("selected_model", GEMINI_MODELS[position])
                .apply()
        }
    }

    private fun setupSaveButton() {
        binding.saveButton.setOnClickListener {
            val key = binding.apiKeyInput.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, R.string.toast_api_key_empty, Toast.LENGTH_SHORT).show()
            } else {
                apiKeyRepository.saveApiKey(key)
                Toast.makeText(this, R.string.toast_api_key_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupHowToUse() {
        // Setup circle backgrounds for step numbers
        val circleColor = ContextCompat.getColor(this, R.color.ww_background)
        val strokeColor = ContextCompat.getColor(this, R.color.ww_primary)
        val strokeWidth = (1 * resources.displayMetrics.density).toInt()

        fun createCircle() = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(circleColor)
            setStroke(strokeWidth, strokeColor)
        }

        binding.step1Number.background = createCircle()
        binding.step2Number.background = createCircle()
        binding.step3Number.background = createCircle()

        // Setup status dot circle
        binding.statusDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
        }

        // Setup ?fix chip styling
        val step3Text = getString(R.string.step_3_desc)
        val spannable = SpannableString(step3Text)
        val target = "?fix"
        var startIndex = step3Text.indexOf(target)

        val chipBgColor = ContextCompat.getColor(this, R.color.ww_surface_inlay)
        val chipTextColor = ContextCompat.getColor(this, R.color.ww_primary)
        val radius = 4f * resources.displayMetrics.density
        val padding = 6f * resources.displayMetrics.density

        while (startIndex >= 0) {
            spannable.setSpan(
                RoundedBackgroundSpan(chipBgColor, chipTextColor, radius, padding),
                startIndex,
                startIndex + target.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = step3Text.indexOf(target, startIndex + target.length)
        }
        binding.step3Desc.text = spannable
    }

    override fun onResume() {
        super.onResume()
        checkAccessibilityStatus()
    }

    private fun checkAccessibilityStatus() {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val isEnabled = enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }

        val dotDrawable = binding.statusDot.background as? GradientDrawable

        if (isEnabled) {
            binding.statusLabel.setText(R.string.label_service_active)
            dotDrawable?.setColor(ContextCompat.getColor(this, R.color.ww_success))
            binding.enableButton.setText(R.string.label_enabled)
        } else {
            binding.statusLabel.setText(R.string.label_service_inactive)
            dotDrawable?.setColor(ContextCompat.getColor(this, R.color.ww_error))
            binding.enableButton.setText(R.string.label_enable)
        }
    }

    companion object {
        private val GEMINI_MODELS = listOf(
            "gemini-2.5-flash-lite",
            "gemini-3-flash",
            "gemini-3.1-flash-lite"
        )

        fun getSelectedModel(context: Context): String {
            return context.getSharedPreferences("wordwise_prefs", MODE_PRIVATE)
                .getString("selected_model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite"
        }
    }
}

class RoundedBackgroundSpan(
    private val backgroundColor: Int,
    private val textColor: Int,
    private val cornerRadius: Float,
    private val padding: Float
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val oldTypeface = paint.typeface
        paint.typeface = Typeface.MONOSPACE
        val size = (paint.measureText(text, start, end) + 2 * padding).roundToInt()
        paint.typeface = oldTypeface
        return size
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val oldTypeface = paint.typeface
        paint.typeface = Typeface.MONOSPACE
        val width = paint.measureText(text, start, end)

        // Draw background
        val rect = RectF(x, top.toFloat(), x + width + 2 * padding, bottom.toFloat())
        val oldColor = paint.color
        paint.color = backgroundColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        // Draw text
        paint.color = textColor
        canvas.drawText(text!!, start, end, x + padding, y.toFloat(), paint)

        paint.color = oldColor
        paint.typeface = oldTypeface
    }
}
