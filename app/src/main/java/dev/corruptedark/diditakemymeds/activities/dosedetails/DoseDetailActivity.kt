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

package dev.corruptedark.diditakemymeds.activities.dosedetails

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.siravorona.utils.base.BaseBoundInteractableVmActivity
import com.siravorona.utils.parcelable
import dev.corruptedark.diditakemymeds.BR
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.StorageManager
import dev.corruptedark.diditakemymeds.data.db.medicationDao
import dev.corruptedark.diditakemymeds.data.db.proofImageDao
import dev.corruptedark.diditakemymeds.data.models.DoseRecord
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.databinding.ActivityDoseDetailBinding
import dev.corruptedark.diditakemymeds.util.DialogUtil
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.TimeZone
import java.util.concurrent.Executors

class DoseDetailActivity :
        BaseBoundInteractableVmActivity<ActivityDoseDetailBinding, DoseDetailViewModel, DoseDetailViewModel.Interactor>(
                ActivityDoseDetailBinding::class, BR.vm) {

    private var medId = Medication.INVALID_MED_ID

    private val lifecycleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val mainScope = MainScope()

    override val vm: DoseDetailViewModel by viewModels()
    override val modelInteractor = object : DoseDetailViewModel.Interactor {
        override suspend fun requestTime(initialHour: Int, initialMinute: Int): Pair<Int, Int>? {
            return this@DoseDetailActivity.requestTimeOfDay(initialHour, initialMinute)
        }

        override suspend fun requestDate(initialSelectionMillis: Long): Triple<Int, Int, Int>? {
            return this@DoseDetailActivity.requestDate(initialSelectionMillis)
        }

        override suspend fun confirmTakenTimeChanges(): Boolean {
            return this@DoseDetailActivity.promptConfirmTimeChanges()
        }

        override fun saveTimeTaken(doseRecord: DoseRecord, newTimeTakenMills: Long) {
            this@DoseDetailActivity.doUpdateDose(doseRecord, newTimeTakenMills)
        }


    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.appbar.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        val record = intent.parcelable<DoseRecord>(EXTRA_DOSE_RECORD)
        if (record == null) {
            finish()
            return
        }
        vm.doseRecord = record

        medId = intent.getLongExtra(EXTRA_MEDICATION_ID, Medication.INVALID_MED_ID)

        lifecycleScope.launch(lifecycleDispatcher) {
            val uri = getImageUri(medId, record.doseTime)
            mainScope.launch {
                vm.imageUri = uri
            }
        }

    }

    private fun getImageUri(medId: Long, doseTime: Long): Uri? {
        val proofImage = proofImageDao(this).get(medId, doseTime)
        val imageUri = proofImage?.let { StorageManager.getImageUri(this, it) }
        return imageUri
    }

    private suspend fun promptConfirmTimeChanges(): Boolean {
        val action = DialogUtil.showMaterialDialogSuspend(
                this, this,
                getString(R.string.are_you_sure),
                getString(R.string.dose_update_warning),
                getString(R.string.confirm),
                getString(R.string.cancel),
        )
        return action == DialogUtil.Action.POSITIVE
    }

    private fun doUpdateDose(doseRecord: DoseRecord, newTimeTakenMills: Long) {
        val medId = this.medId
        lifecycleScope.launch(lifecycleDispatcher) {
            deleteProofImage(medId, doseRecord.doseTime)
            val medication = medicationDao(this@DoseDetailActivity).get(medId)
            medication.removeTakenDose(doseRecord, false)
            val newRecord = DoseRecord(newTimeTakenMills, doseRecord.closestDose)
            medication.addNewTakenDose(newRecord)
            medicationDao(this@DoseDetailActivity).updateMedications(medication)
            mainScope.launch {
                vm.doseRecord = newRecord
            }
        }
    }

    private fun deleteProofImage(medId: Long, doseTime: Long) {
        val proofImage = proofImageDao(this).get(medId, doseTime) ?: return
        StorageManager.deleteImageFile(this, proofImage)
        proofImageDao(this).delete(proofImage)
    }

    private suspend fun requestTimeOfDay(initialHour: Int, initialMinute: Int): Pair<Int, Int>? {
        val config = DialogUtil.TimePickerConfig(getString(R.string.select_a_time), initialHour,
                initialMinute, DateFormat.is24HourFormat(this))
        return DialogUtil.showMaterialTimePickerSuspend(this.supportFragmentManager, this, config,
                getString(R.string.time_picker_tag))
    }

    private suspend fun requestDate(initialSelection: Long): Triple<Int, Int, Int>? {
        val config = DialogUtil.DatePickerConfigB(getString(R.string.select_a_start_date),
                TimeZone.getTimeZone(getString(R.string.utc)), initialSelection)
        return DialogUtil.showMaterialDatePickerSuspend(supportFragmentManager, this, config)
    }

    companion object {
        private const val EXTRA_DOSE_RECORD = "EXTRA_DOSE_RECORD"
        private const val EXTRA_MEDICATION_ID = "EXTRA_MEDICATION_ID"
        fun open(context: Context, medicationId: Long, doseRecord: DoseRecord) {
            val intent = Intent(context, DoseDetailActivity::class.java).apply {
                putExtra(EXTRA_DOSE_RECORD, doseRecord)
                putExtra(EXTRA_MEDICATION_ID, medicationId)
            }
            context.startActivity(intent)
        }
    }
}