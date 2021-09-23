package org.odk.collect.androidshared.utils

import android.app.Application
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import org.odk.collect.strings.getLocalizedString

/**
 * Convenience wrapper around Android's [Toast] API.
 */
object ToastUtils {

    private lateinit var lastToast: Toast

    @JvmStatic
    fun showShortToast(context: Context, message: String) {
        showToast(context.applicationContext as Application, message)
    }

    @JvmStatic
    fun showShortToast(context: Context, messageResource: Int) {
        showToast(
            context.applicationContext as Application,
            context.getLocalizedString(messageResource)
        )
    }

    @JvmStatic
    fun showLongToast(context: Context, message: String) {
        showToast(context.applicationContext as Application, message, Toast.LENGTH_LONG)
    }

    @JvmStatic
    fun showLongToast(context: Context, messageResource: Int) {
        showToast(
            context.applicationContext as Application,
            context.getLocalizedString(messageResource),
            Toast.LENGTH_LONG
        )
    }

    @JvmStatic
    fun showShortToastInMiddle(context: Context, message: String) {
        showToastInMiddle(context.applicationContext as Application, message)
    }

    private fun showToast(
        context: Application,
        message: String,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        hideLastToast()
        lastToast = Toast.makeText(context, message, duration)
        lastToast.show()
    }

    private fun showToastInMiddle(
        context: Application,
        message: String,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        hideLastToast()
        lastToast = Toast.makeText(context, message, duration)
        try {
            val group = lastToast.view as ViewGroup?
            val messageTextView = group!!.getChildAt(0) as TextView
            messageTextView.textSize = 21f
            messageTextView.gravity = Gravity.CENTER
        } catch (ignored: Exception) {
            // ignored
        }
        lastToast.setGravity(Gravity.CENTER, 0, 0)
        lastToast.show()
    }

    private fun hideLastToast() {
        if (ToastUtils::lastToast.isInitialized) {
            lastToast.cancel()
        }
    }
}
