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

package dev.corruptedark.diditakemymeds.activities

import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.activities.base.BaseBoundActivity
import dev.corruptedark.diditakemymeds.data.db.medicationDao
import dev.corruptedark.diditakemymeds.data.db.proofImageDao
import dev.corruptedark.diditakemymeds.data.models.DoseRecord
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.models.ProofImage
import dev.corruptedark.diditakemymeds.databinding.ActivityDoseDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

class DoseDetailActivity : BaseBoundActivity<ActivityDoseDetailBinding>(ActivityDoseDetailBinding::class) {

    private var hourTaken: Int = -1
    private var minuteTaken: Int = -1
    private var dayTaken: Int = -1
    private var monthTaken: Int = -1
    private var yearTaken: Int = -1
    private var medId = Medication.INVALID_MED_ID
    private var doseTime = DoseRecord.INVALID_TIME
    private var pickerIsOpen = false
    private val context = this
    private val lifecycleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var proofImage: ProofImage? = null
    private val mainScope = MainScope()
    private var imageFolder: File? = null
    private var closestDose = DoseRecord.INVALID_TIME
    private var yesterdayString = ""
    private var todayString = ""
    private var tomorrowString = ""
    private var dateFormat = ""
    private var systemIs24Hour = false
    private var timeFormat = ""
    private lateinit var locale: Locale
    private val workingCalendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageFolder = File(filesDir.path + File.separator + getString(R.string.image_path))

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.appbar.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        medId = intent.getLongExtra(getString(R.string.med_id_key), Medication.INVALID_MED_ID)
        doseTime = intent.getLongExtra(getString(R.string.dose_time_key), DoseRecord.INVALID_TIME)
        closestDose = intent.getLongExtra(getString(R.string.time_taken_key), DoseRecord.NONE)

        yesterdayString = context.getString(R.string.yesterday)
        todayString = context.getString(R.string.today)
        tomorrowString = context.getString(R.string.tomorrow)
        dateFormat = context.getString(R.string.date_format)

        systemIs24Hour = DateFormat.is24HourFormat(context)

        timeFormat = if (systemIs24Hour) {
            context.getString(R.string.time_24)
        }
        else {
            context.getString(R.string.time_12)
        }

        locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales[0]
        } else {
            Resources.getSystem().configuration.locale
        }

        val doseTimeString = Medication.doseString(
            yesterdayString,
            todayString,
            tomorrowString,
            doseTime,
            dateFormat,
            timeFormat,
            locale
        )

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = doseTime
        hourTaken = calendar.get(Calendar.HOUR_OF_DAY)
        minuteTaken = calendar.get(Calendar.MINUTE)
        dayTaken = calendar.get(Calendar.DAY_OF_MONTH)
        monthTaken = calendar.get(Calendar.MONTH)
        yearTaken = calendar.get(Calendar.YEAR)

        binding.doseTakenTimeLabel.text = context.getString(R.string.time_taken, doseTimeString)

        binding.startTakenTimeEditButton.setOnClickListener { _ ->
            val takenTimeEditLayout = binding.takenTimeEditLayout
            if (takenTimeEditLayout.visibility == View.GONE) {
                binding.takenTimeButton.text = DateFormat.format(timeFormat, doseTime)
                binding.takenDateButton.text = DateFormat.format(dateFormat, doseTime)
                takenTimeEditLayout.visibility = View.VISIBLE
            } else {
                takenTimeEditLayout.visibility = View.GONE
            }
        }

        binding.takenTimeButton.setOnClickListener { view ->
            openTimePicker(view)
        }

        binding.takenDateButton.setOnClickListener { view ->
            openDatePicker(view)
        }

        binding.takenTimeConfirmButton.setOnClickListener { view ->

            if (doseChanged()) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(getString(R.string.are_you_sure))
                    .setMessage(getString(R.string.dose_update_warning))
                    .setNegativeButton(getString(R.string.cancel)) { dialog, which ->
                        dialog.cancel()
                    }
                    .setPositiveButton(getString(R.string.confirm)) { dialog, which ->
                        dialog.cancel()
                        updateDose()
                    }
                    .show()
            }
        }

        binding.takenTimeCancelButton.setOnClickListener { _ ->
            binding.takenTimeEditLayout.visibility = View.GONE
        }

        if(closestDose == DoseRecord.NONE) {
            binding.closestDoseTimeLabel.visibility = View.GONE
        }
        else {
            binding.closestDoseTimeLabel.visibility = View.VISIBLE
            binding.closestDoseTimeLabel.text = context.getString(
                R.string.closest_dose_label,
                Medication.doseString(
                    yesterdayString,
                    todayString,
                    tomorrowString,
                    closestDose,
                    dateFormat,
                    timeFormat,
                    locale
                )
            )
        }

        lifecycleScope.launch (lifecycleDispatcher) {
            proofImage = proofImageDao(context).get(medId, doseTime)
            val imageDir = imageFolder
            if(proofImage != null && imageDir != null){
                val imageFile = File(imageDir.absolutePath + File.separator + proofImage!!.filePath)
                if (imageFile.exists() && imageFile.canRead()) {
                    mainScope.launch {
                        binding.proofImageView.setImageURI(imageFile.toUri())
                        binding.noImageLabel.visibility = View.GONE
                        binding.proofImageView.visibility = View.VISIBLE
                    }
                }
            }
            else {
                mainScope.launch {
                    binding.noImageLabel.visibility = View.VISIBLE
                    binding.proofImageView.visibility = View.GONE
                }
            }
        }
    }

    private fun updateDose() {

        if (doseChanged()) {
            lifecycleScope.launch(lifecycleDispatcher) {
                if (proofImageDao(context).proofImageExists(medId, doseTime)) {
                    val proofImage = proofImageDao(context).get(medId, doseTime)
                    if (proofImage != null) {
                        imageFolder?.apply {
                            proofImage.deleteImageFile(this)
                        }
                        proofImageDao(context).delete(proofImage)
                    }
                }

                val doseRecord = medicationDao(context).get(medId).doseRecord.find { record ->
                    record.doseTime == doseTime
                }

                doseRecord?.apply {
                    val medication = medicationDao(context).get(medId)
                    medication.removeTakenDose(this, false)

                    val newRecord = DoseRecord(workingCalendar.timeInMillis, this.closestDose)
                    medication.addNewTakenDose(newRecord)
                    medicationDao(context).updateMedications(medication)

                    val newDoseTimeString = Medication.doseString(
                        yesterdayString,
                        todayString,
                        tomorrowString,
                        workingCalendar.timeInMillis,
                        dateFormat,
                        timeFormat,
                        locale
                    )

                    this@DoseDetailActivity.doseTime = workingCalendar.timeInMillis

                    mainScope.launch(Dispatchers.Main) {
                        binding.doseTakenTimeLabel.text = context.getString(R.string.time_taken, newDoseTimeString)

                        binding.proofImageView.visibility = View.GONE
                        binding.noImageLabel.visibility = View.VISIBLE
                    }
                }
            }
        }

        binding.takenTimeEditLayout.visibility = View.GONE
    }

    private fun doseChanged(): Boolean {
        workingCalendar.timeInMillis = doseTime
        workingCalendar.set(Calendar.HOUR_OF_DAY, hourTaken)
        workingCalendar.set(Calendar.MINUTE, minuteTaken)
        workingCalendar.set(Calendar.DAY_OF_MONTH, dayTaken)
        workingCalendar.set(Calendar.MONTH, monthTaken)
        workingCalendar.set(Calendar.YEAR, yearTaken)

        return doseTime != workingCalendar.timeInMillis
    }

    private fun openTimePicker(view: View) {
        if (!pickerIsOpen) {
            pickerIsOpen = true

            val calendar = Calendar.getInstance()

            val initialHour: Int
            val initialMinute: Int

            if (hourTaken >= 0 && minuteTaken >= 0) {
                initialHour = hourTaken
                initialMinute = minuteTaken
            }
            else
            {
                initialHour = calendar.get(Calendar.HOUR_OF_DAY)
                initialMinute = calendar.get(Calendar.MINUTE)
            }

            val isSystem24Hour = DateFormat.is24HourFormat(context)
            val clockFormat = if (isSystem24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(clockFormat)
                .setHour(initialHour)
                .setMinute(initialMinute)
                .setTitleText(getString(R.string.select_a_time))
                .build()
            timePicker.addOnPositiveButtonClickListener {
                hourTaken = timePicker.hour
                minuteTaken = timePicker.minute
                calendar.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                calendar.set(Calendar.MINUTE, timePicker.minute)
                val formattedTime = if (isSystem24Hour) DateFormat.format(getString(R.string.time_24), calendar)
                else DateFormat.format(getString(R.string.time_12), calendar)
                (view as TextView).text = formattedTime
            }
            timePicker.addOnDismissListener {
                pickerIsOpen = false
            }
            timePicker.show(context.supportFragmentManager, getString(R.string.time_picker_tag))
        }
    }

    private fun openDatePicker(view: View) {
        if (!pickerIsOpen) {
            pickerIsOpen = true

            val calendar = Calendar.getInstance(TimeZone.getTimeZone(getString(R.string.utc)))

            val initialSelection = if (dayTaken >= 0 && monthTaken >= 0 && yearTaken >= 0) {
                calendar.set(Calendar.DAY_OF_MONTH, dayTaken)
                calendar.set(Calendar.MONTH, monthTaken)
                calendar.set(Calendar.YEAR, yearTaken)
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
                dayTaken = calendar.get(Calendar.DATE)
                monthTaken = calendar.get(Calendar.MONTH)
                yearTaken = calendar.get(Calendar.YEAR)
                (view as TextView).text = DateFormat.format(getString(R.string.date_format), calendar)
            }
            datePicker.addOnDismissListener {
                pickerIsOpen = false
            }
            datePicker.show(context.supportFragmentManager, getString(R.string.date_picker_tag))
        }
    }}