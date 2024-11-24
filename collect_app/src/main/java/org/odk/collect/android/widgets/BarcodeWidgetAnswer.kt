package org.odk.collect.android.widgets

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.FrameLayout
import org.odk.collect.android.databinding.BarcodeWidgetAnswerBinding

class BarcodeWidgetAnswer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {
    private val binding = BarcodeWidgetAnswerBinding.inflate(LayoutInflater.from(context), this, true)
    private var hidden = false

    fun setAnswer(answer: String?) {
        binding.answer.text = stripInvalidCharacters(answer)
        binding.root.visibility = if (hidden || binding.answer.text.isNullOrBlank()) GONE else VISIBLE
    }

    fun setTextSize(textSize: Float) {
        binding.answer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize)
    }

    fun setHidden(hidden: Boolean) {
        this.hidden = hidden
    }

    fun getAnswer(): String {
        return binding.answer.text.toString()
    }

    // Remove control characters, invisible characters and unused code points.
    private fun stripInvalidCharacters(data: String?): String? {
        return data?.replace("\\p{C}".toRegex(), "")
    }
}
