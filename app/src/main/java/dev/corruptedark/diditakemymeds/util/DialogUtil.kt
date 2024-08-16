package dev.corruptedark.diditakemymeds.util

import android.app.Dialog
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
object DialogUtil {
    enum class Action {
        CANCELLED, POSITIVE, NEGATIVE, NEUTRAL
    }

    fun setWidthPercent(dialog: Dialog, percentage: Int) {
        val percent = percentage.toFloat() / 100
        val dm = Resources.getSystem().displayMetrics
        val rect = dm.run { Rect(0, 0, widthPixels, heightPixels) }
        val percentWidth = rect.width() * percent
        dialog.window?.setLayout(percentWidth.toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    suspend fun showMaterialDialogSuspend(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        title: String,
        message: String,
        positiveButtonText: String,
        negativeButtonText: String? = null,
        neutralButtonString: String? = null,
        dialogCancelable: Boolean = false
    ): Action {
        return try {
            withContext(lifecycleOwner.lifecycleScope.coroutineContext) {
                suspendCancellableCoroutine { continuation ->
                    val dialog = MaterialAlertDialogBuilder(context).setTitle(title)
                        .setMessage(message).setPositiveButton(positiveButtonText) { _, _ ->
                            continuation.resume(Action.POSITIVE, null)
                        }.apply {
                            if (negativeButtonText != null) {
                                setNegativeButton(negativeButtonText) { _, _ ->
                                    continuation.resume(Action.NEGATIVE, null)
                                }
                            }
                        }.apply {
                            if (neutralButtonString != null) {
                                setNeutralButton(neutralButtonString) { _, _ ->
                                    continuation.resume(Action.NEUTRAL, null)
                                }
                            }
                        }.setOnDismissListener {
                            if (continuation.isActive) {
                                continuation.resume(Action.CANCELLED, null)
                            }
                        }.setCancelable(dialogCancelable).show()

                    continuation.invokeOnCancellation { dialog.dismiss() }
                }

            }
        } catch (e: CancellationException) {
            Action.CANCELLED
        }
    }


    data class DatePickerConfigB(val title: String, val timeZone: TimeZone, val millis: Long)


    suspend fun showMaterialDatePickerSuspend(
        fragmentManager: FragmentManager,
        lifecycleOwner: LifecycleOwner,
        config: DatePickerConfigB,
        tag: String? = null,
        dialogCancelable: Boolean = false
    ): Triple<Int, Int, Int>? {
        val datePicker = MaterialDatePicker.Builder.datePicker().setSelection(config.millis)
            .setTitleText(config.title).build()
        return showMaterialDatePickerSuspend(
            fragmentManager,
            lifecycleOwner,
            datePicker,
            config.timeZone,
            tag,
            dialogCancelable
        )
    }

    suspend fun showMaterialDatePickerSuspend(
        fragmentManager: FragmentManager,
        lifecycleOwner: LifecycleOwner,
        datePicker: MaterialDatePicker<Long>,
        timeZone: TimeZone,
        tag: String? = null,
        dialogCancelable: Boolean = false
    ): Triple<Int, Int, Int>? {
        return try {
            withContext(lifecycleOwner.lifecycleScope.coroutineContext) {
                suspendCancellableCoroutine { continuation ->
                    datePicker.addOnPositiveButtonClickListener { millis ->
                        val calendar = Calendar.getInstance(timeZone)
                        calendar.timeInMillis = millis
                        val year = calendar.get(Calendar.YEAR)
                        val month = calendar.get(Calendar.MONTH)
                        val day = calendar.get(Calendar.DAY_OF_MONTH)
                        continuation.resume(Triple(year, month, day), null)
                    }
                    datePicker.addOnNegativeButtonClickListener {
                        continuation.resume(null, null)
                    }
                    datePicker.addOnDismissListener {
                        if (continuation.isActive) {
                            continuation.resume(null, null)
                        }
                    }
                    datePicker.addOnCancelListener {
                        continuation.resume(null, null)
                    }
                    datePicker.isCancelable = dialogCancelable
                    datePicker.show(fragmentManager, tag)
                    continuation.invokeOnCancellation { datePicker.dismiss() }
                }

            }
        } catch (e: CancellationException) {
            null
        }
    }

    data class TimePickerConfig(
        val title: String, val hour: Int, val minute: Int, val is24H: Boolean
    ) {}

    suspend fun showMaterialTimePickerSuspend(
        fragmentManager: FragmentManager,
        lifecycleOwner: LifecycleOwner,
        config: TimePickerConfig,
        tag: String? = null,
        dialogCancelable: Boolean = false
    ): Pair<Int, Int>? {
        val initialHour: Int
        val initialMinute: Int

        if (config.hour >= 0 && config.minute >= 0) {
            initialHour = config.hour
            initialMinute = config.minute
        } else {
            val calendar = Calendar.getInstance()
            initialHour = calendar.get(Calendar.HOUR_OF_DAY)
            initialMinute = calendar.get(Calendar.MINUTE)
        }

        val clockFormat = if (config.is24H) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
        val timePicker = MaterialTimePicker.Builder().setTimeFormat(clockFormat)
            .setHour(initialHour).setMinute(initialMinute).setTitleText(config.title).build()
        return showMaterialTimePickerSuspend(
            fragmentManager,
            lifecycleOwner,
            timePicker,
            tag,
            dialogCancelable
        )
    }

    suspend fun showMaterialTimePickerSuspend(
        fragmentManager: FragmentManager,
        lifecycleOwner: LifecycleOwner,
        timePicker: MaterialTimePicker,
        tag: String? = null,
        dialogCancelable: Boolean = false
    ): Pair<Int, Int>? {
        return try {
            withContext(lifecycleOwner.lifecycleScope.coroutineContext) {
                suspendCancellableCoroutine { continuation ->
                    timePicker.addOnPositiveButtonClickListener {
                        val hour = timePicker.hour
                        val minute = timePicker.minute
                        continuation.resume(Pair(hour, minute), null)
                    }
                    timePicker.addOnNegativeButtonClickListener {
                        continuation.resume(null, null)
                    }
                    timePicker.addOnDismissListener {
                        if (continuation.isActive) {
                            continuation.resume(null, null)
                        }
                    }
                    timePicker.addOnCancelListener {
                        continuation.resume(null, null)
                    }
                    timePicker.isCancelable = dialogCancelable
                    timePicker.show(fragmentManager, tag)
                    continuation.invokeOnCancellation { timePicker.dismiss() }
                }

            }
        } catch (e: CancellationException) {
            null
        }
    }
}