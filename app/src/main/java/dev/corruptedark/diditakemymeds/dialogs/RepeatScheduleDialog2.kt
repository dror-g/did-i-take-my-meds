/*
 * Did I Take My Meds? is a FOSS app to keep track of medications
 * Did I Take My Meds? is designed to help prevent a user from skipping doses and/or overdosing
 *     Copyright (C) 2021  Noah Stanford <noahstandingford@gmail.com>
 *
 *     Did I Take My Meds? is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Did I Take My Meds? is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.corruptedark.diditakemymeds.dialogs

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.siravorona.utils.fragmentresult.FragmentResultManager
import com.siravorona.utils.fragmentresult.showDialogFragmentForResult
import com.siravorona.utils.parcelable
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.data.models.BirthControlType
import dev.corruptedark.diditakemymeds.data.models.RepeatSchedule
import dev.corruptedark.diditakemymeds.databinding.FragmentRepeatScheduleDialogBinding
import dev.corruptedark.diditakemymeds.util.DialogUtil
import it.sephiroth.android.library.numberpicker.doOnProgressChanged
import java.io.Serializable
import java.util.Calendar
import java.util.TimeZone


class RepeatScheduleDialog2 : DialogFragment() {

    private var _binding: FragmentRepeatScheduleDialogBinding? = null
    private val binding: FragmentRepeatScheduleDialogBinding
        get() {
            return _binding!!
        }

    private @Volatile
    var pickerIsOpen = false
    private var hour = -1
    private var minute = -1
    private var startDay = -1
    private var startMonth = -1
    private var startYear = -1
    private var daysBetween = 1
    private var weeksBetween = 0
    private var monthsBetween = 0
    private var yearsBetween = 0
    private var birthControlTypeSchedule = BirthControlType.NO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val schedule = it.parcelable(EXTRA_SCHEDULE) ?: RepeatSchedule.BLANK
            loadFromSchedule(schedule)
        }
    }

    private fun loadFromSchedule(repeatSchedule: RepeatSchedule) {
        this.hour = repeatSchedule.hour
        this.minute = repeatSchedule.minute
        this.startDay = repeatSchedule.startDay
        this.startMonth = repeatSchedule.startMonth
        this.startYear = repeatSchedule.startYear
        this.daysBetween = repeatSchedule.daysBetween
        this.weeksBetween = repeatSchedule.weeksBetween
        this.monthsBetween = repeatSchedule.monthsBetween
        this.yearsBetween = repeatSchedule.yearsBetween
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRepeatScheduleDialogBinding.inflate(inflater, container, false)

        binding.birthControlSwitch.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                binding.birthControlGroup.visibility = View.VISIBLE
                binding.timeBetweenPickers.visibility = View.GONE
                birthControlTypeSchedule = getBirthControl(
                        binding.birthControlGroup.checkedRadioButtonId)
            } else {
                binding.birthControlGroup.visibility = View.GONE
                binding.timeBetweenPickers.visibility = View.VISIBLE
                birthControlTypeSchedule = BirthControlType.NO
            }
        }

        binding.birthControlGroup.setOnCheckedChangeListener { group, checkedId ->
            birthControlTypeSchedule = getBirthControl(checkedId)
        }

        if (scheduleIsValid()) {
            val calendar = Calendar.getInstance()
            val isSystem24Hour = DateFormat.is24HourFormat(inflater.context)
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.DAY_OF_MONTH, startDay)
            calendar.set(Calendar.MONTH, startMonth)
            calendar.set(Calendar.YEAR, startYear)
            val formattedTime =
                    if (isSystem24Hour) DateFormat.format(getString(R.string.time_24), calendar)
                    else DateFormat.format(getString(R.string.time_12), calendar)
            binding.timePickerButton.text = formattedTime
            binding.startDateButton.text =
                    DateFormat.format(getString(R.string.date_format), calendar)
            binding.daysBetweenPicker.progress = daysBetween
            binding.weeksBetweenPicker.progress = weeksBetween
            binding.monthsBetweenPicker.progress = monthsBetween
            binding.yearsBetweenPicker.progress = yearsBetween
        }

        binding.timePickerButton.setOnClickListener {
            openTimePicker(it)
        }

        binding.startDateButton.setOnClickListener {
            openDatePicker(it)
        }

        binding.daysBetweenPicker.doOnProgressChanged { numberPicker, progress, formUser ->
            daysBetween = progress
        }

        binding.weeksBetweenPicker.doOnProgressChanged { numberPicker, progress, formUser ->
            weeksBetween = progress
        }

        binding.monthsBetweenPicker.doOnProgressChanged { numberPicker, progress, formUser ->
            monthsBetween = progress
        }

        binding.yearsBetweenPicker.doOnProgressChanged { numberPicker, progress, formUser ->
            yearsBetween = progress
        }

        binding.cancelButton.setOnClickListener {
            setResult(Result.Cancelled)
            dismiss()
        }
        binding.confirmButton.setOnClickListener {
            val schedule = RepeatSchedule(hour, minute, startDay, startMonth, startYear,
                    daysBetween, weeksBetween, monthsBetween, yearsBetween)
            val result = Result.SchedulePicked(schedule, birthControlTypeSchedule)
            setResult(result)
            dismiss()
        }

        return binding.root
    }

    private fun getBirthControl(selectedIndex: Int) = when (selectedIndex) {
        R.id.radio_button_21_7 -> BirthControlType.THREE_WEEKS
        R.id.radio_button_63_7 -> BirthControlType.NINE_WEEKS
        else -> BirthControlType.NO
    }


    override fun onResume() {
        super.onResume()
        binding.birthControlSwitch.isChecked = false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.let { DialogUtil.setWidthPercent(it, 90) }
    }

    private fun openTimePicker(view: View) {
        if (!pickerIsOpen) {
            pickerIsOpen = true

            val calendar = Calendar.getInstance()

            val initialHour: Int
            val initialMinute: Int

            if (hour >= 0 && minute >= 0) {
                initialHour = hour
                initialMinute = minute
            } else {
                initialHour = calendar.get(Calendar.HOUR_OF_DAY)
                initialMinute = calendar.get(Calendar.MINUTE)
            }

            val isSystem24Hour = DateFormat.is24HourFormat(view.context)
            val clockFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
            val timePicker = MaterialTimePicker.Builder()
                    .setTimeFormat(clockFormat)
                    .setHour(initialHour)
                    .setMinute(initialMinute)
                    .setTitleText(getString(R.string.select_a_time))
                    .build()
            timePicker.addOnPositiveButtonClickListener {
                hour = timePicker.hour
                minute = timePicker.minute
                calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                calendar.set(Calendar.MINUTE, timePicker.minute)
                val formattedTime =
                        if (isSystem24Hour) DateFormat.format(getString(R.string.time_24), calendar)
                        else DateFormat.format(getString(R.string.time_12), calendar)
                (view as TextView).text = formattedTime
            }
            timePicker.addOnDismissListener {
                pickerIsOpen = false
            }
            timePicker.show(
                    childFragmentManager, getString(
                    R.string.time_picker_tag
            )
            )
        }
    }

    private fun openDatePicker(view: View) {
        if (!pickerIsOpen) {
            pickerIsOpen = true

            val calendar = Calendar.getInstance(TimeZone.getTimeZone(getString(R.string.utc)))

            val initialSelection = if (startDay >= 0 && startMonth >= 0 && startYear >= 0) {
                calendar.set(Calendar.DAY_OF_MONTH, startDay)
                calendar.set(Calendar.MONTH, startMonth)
                calendar.set(Calendar.YEAR, startYear)
                calendar.timeInMillis
            } else {
                MaterialDatePicker.todayInUtcMilliseconds()
            }

            val datePicker = MaterialDatePicker.Builder.datePicker()
                    .setSelection(initialSelection)
                    .setTitleText(getString(R.string.select_a_start_date))
                    .build()
            datePicker.addOnPositiveButtonClickListener {
                calendar.timeInMillis = datePicker.selection!!
                startDay = calendar.get(Calendar.DATE)
                startMonth = calendar.get(Calendar.MONTH)
                startYear = calendar.get(Calendar.YEAR)
                (view as TextView).text =
                        DateFormat.format(getString(R.string.date_format), calendar)
            }
            datePicker.addOnDismissListener {
                pickerIsOpen = false
            }
            datePicker.show(
                    childFragmentManager, getString(
                    R.string.date_picker_tag
            )
            )
        }
    }

    fun scheduleIsValid(): Boolean {
        val periodIsValid =
                daysBetween > 0 || weeksBetween > 0 || monthsBetween > 0 || yearsBetween > 0
        val startTimeIsValid =
                hour >= 0 && minute >= 0 && startDay >= 0 && startMonth >= 0 && startYear >= 0
        return (periodIsValid || binding.birthControlSwitch.isChecked) && startTimeIsValid
    }

    private fun setResult(result: Any) {
        setFragmentResult(FRAGMENT_REQUEST_KEY, bundleOf(FRAGMENT_RESULT_KEY to result))
    }

    sealed class Result : Serializable {
        object Cancelled : Result()
        class SchedulePicked(
                val schedule: RepeatSchedule,
                val birthControlTypeSchedule: BirthControlType
        ) :
                Result()
    }

    companion object {
        private const val EXTRA_SCHEDULE = "EXTRA_SCHEDULE"
        const val FRAGMENT_REQUEST_KEY = "RepeatScheduleDialog2.Request"
        const val FRAGMENT_RESULT_KEY = "RepeatScheduleDialog2.Result"
        private var counter = 0
        suspend fun requestSchedule(
                fragmentManager: FragmentManager,
                lifecycleOwner: LifecycleOwner,
                initialSchedule: RepeatSchedule,
        ): Pair<RepeatSchedule, BirthControlType>? {
            val fragmentResult =
                    FragmentResultManager.getInstance().showDialogFragmentForResult<Result>(
                            fragmentManager = fragmentManager, lifecycleOwner = lifecycleOwner,
                            requestKey = FRAGMENT_REQUEST_KEY,
                            resultKey = FRAGMENT_RESULT_KEY,
                            fragmentTag = "RepeatScheduleDialog2-$counter",
                            fragment = RepeatScheduleDialog2().apply {
                                arguments = bundleOf(
                                        EXTRA_SCHEDULE to initialSchedule,
                                )
                            }
                    )
            val result = when (fragmentResult) {
                is Result.SchedulePicked -> fragmentResult.schedule to fragmentResult.birthControlTypeSchedule
                else -> null
            }
            return result
        }

    }
}