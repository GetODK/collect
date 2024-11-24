package org.odk.collect.android.widgets

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.TypedValue
import android.view.View
import com.google.zxing.integration.android.IntentIntegrator
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.StringData
import org.javarosa.form.api.FormEntryPrompt
import org.odk.collect.android.activities.ScannerWithFlashlightActivity
import org.odk.collect.android.databinding.BarcodeWidgetBinding
import org.odk.collect.android.formentry.questions.QuestionDetails
import org.odk.collect.android.utilities.Appearances
import org.odk.collect.android.utilities.Appearances.hasAppearance
import org.odk.collect.android.utilities.Appearances.isFrontCameraAppearance
import org.odk.collect.android.widgets.interfaces.WidgetDataReceiver
import org.odk.collect.android.widgets.utilities.WaitingForDataRegistry
import org.odk.collect.androidshared.system.CameraUtils
import org.odk.collect.androidshared.ui.ToastUtils.showLongToast
import org.odk.collect.permissions.PermissionListener
import org.odk.collect.strings.R

@SuppressLint("ViewConstructor")
class BarcodeWidget(
    context: Context,
    questionDetails: QuestionDetails,
    private val waitingForDataRegistry: WaitingForDataRegistry,
    private val cameraUtils: CameraUtils
) : QuestionWidget(context, questionDetails), WidgetDataReceiver {
    lateinit var binding: BarcodeWidgetBinding

    init {
        render()
    }

    override fun onCreateAnswerView(context: Context, prompt: FormEntryPrompt, answerFontSize: Int): View {
        binding = BarcodeWidgetBinding.inflate((context as Activity).layoutInflater)

        if (prompt.isReadOnly) {
            binding.barcodeButton.visibility = GONE
        } else {
            binding.barcodeButton.setOnClickListener { onButtonClick() }
        }
        binding.barcodeAnswerText.setTextSize(
            TypedValue.COMPLEX_UNIT_DIP,
            answerFontSize.toFloat()
        )

        val answer = prompt.answerText
        if (!answer.isNullOrEmpty()) {
            binding.barcodeButton.text = getContext().getString(R.string.replace_barcode)
            binding.barcodeAnswerText.text = answer
        }

        updateVisibility()
        return binding.root
    }

    override fun clearAnswer() {
        binding.barcodeAnswerText.text = null
        binding.barcodeButton.text = context.getString(R.string.get_barcode)
        widgetValueChanged()
        updateVisibility()
    }

    override fun getAnswer(): IAnswerData? {
        val answer = binding.barcodeAnswerText.text.toString()
        return if (answer.isEmpty()) null else StringData(answer)
    }

    override fun setData(answer: Any) {
        val response = answer as String
        binding.barcodeAnswerText.text = stripInvalidCharacters(response)
        binding.barcodeButton.text = context.getString(R.string.replace_barcode)
        updateVisibility()
        widgetValueChanged()
    }

    private fun updateVisibility() {
        if (hasAppearance(formEntryPrompt, Appearances.HIDDEN_ANSWER)) {
            binding.barcodeAnswerText.visibility = GONE
        } else {
            binding.barcodeAnswerText.visibility =
                if (binding.barcodeAnswerText.text.toString().isBlank()) GONE else VISIBLE
        }
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        binding.barcodeAnswerText.setOnLongClickListener(l)
        binding.barcodeButton.setOnLongClickListener(l)
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        binding.barcodeButton.cancelLongPress()
        binding.barcodeAnswerText.cancelLongPress()
    }

    private fun onButtonClick() {
        getPermissionsProvider().requestCameraPermission(
            (context as Activity),
            object : PermissionListener {
                override fun granted() {
                    waitingForDataRegistry.waitForData(formEntryPrompt.index)
                    val intent = IntentIntegrator(context as Activity)
                        .setCaptureActivity(ScannerWithFlashlightActivity::class.java)
                    setCameraIdIfNeeded(formEntryPrompt, intent)
                    intent.initiateScan()
                }
            }
        )
    }

    private fun setCameraIdIfNeeded(prompt: FormEntryPrompt, intent: IntentIntegrator) {
        if (isFrontCameraAppearance(prompt)) {
            if (cameraUtils.isFrontCameraAvailable(context)) {
                intent.addExtra(Appearances.FRONT, true)
            } else {
                showLongToast(context, R.string.error_front_camera_unavailable)
            }
        }
    }

    // Remove control characters, invisible characters and unused code points.
    private fun stripInvalidCharacters(data: String?): String? {
        return data?.replace("\\p{C}".toRegex(), "")
    }
}
