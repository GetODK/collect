package org.odk.collect.android.widgets

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.FrameLayout
import org.javarosa.core.model.data.IAnswerData
import org.odk.collect.android.databinding.BarcodeWidgetAnswerBinding

class BarcodeWidgetAnswer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {
    private val binding = BarcodeWidgetAnswerBinding.inflate(LayoutInflater.from(context), this, true)

    fun setup(answer: IAnswerData?, textSize: Float) {
        binding.answer.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSize)
        setAnswer(answer?.displayText)
    }

    fun setAnswer(answer: String?) {
        binding.answer.text = stripInvalidCharacters(answer)
    }

    fun getAnswer(): String {
        return binding.answer.text.toString()
    }

    // Remove control characters, invisible characters and unused code points.
    private fun stripInvalidCharacters(data: String?): String? {
        return data?.replace("\\p{C}".toRegex(), "")
    }
}
