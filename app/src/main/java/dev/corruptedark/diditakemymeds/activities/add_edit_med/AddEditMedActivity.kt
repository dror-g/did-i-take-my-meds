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

package dev.corruptedark.diditakemymeds.activities.add_edit_med

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.siravorona.utils.activityresult.ActivityResultManager
import com.siravorona.utils.activityresult.getActivityResult
import com.siravorona.utils.base.BaseBoundInteractableVmActivity
import dev.corruptedark.diditakemymeds.BR
import dev.corruptedark.diditakemymeds.R
import dev.corruptedark.diditakemymeds.data.db.doseUnitDao
import dev.corruptedark.diditakemymeds.data.db.medicationDao
import dev.corruptedark.diditakemymeds.data.db.medicationTypeDao
import dev.corruptedark.diditakemymeds.data.models.BirthControlType
import dev.corruptedark.diditakemymeds.data.models.DoseUnit
import dev.corruptedark.diditakemymeds.data.models.Medication
import dev.corruptedark.diditakemymeds.data.models.MedicationType
import dev.corruptedark.diditakemymeds.data.models.RepeatSchedule
import dev.corruptedark.diditakemymeds.data.models.joins.MedicationFull
import dev.corruptedark.diditakemymeds.databinding.ActivityAddOrEditMed2Binding
import dev.corruptedark.diditakemymeds.dialogs.RepeatScheduleDialog2
import dev.corruptedark.diditakemymeds.util.canBeRealId
import dev.corruptedark.diditakemymeds.util.notifications.AlarmIntentManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.Executors

class AddEditMedActivity :
        BaseBoundInteractableVmActivity<ActivityAddOrEditMed2Binding, MedViewModel, MedViewModel.Interactor>(
                ActivityAddOrEditMed2Binding::class, BR.vm) {
    override val vm: MedViewModel by viewModels()
    override val modelInteractor = object : MedViewModel.Interactor {

        override suspend fun openSchedulePicker(): Pair<RepeatSchedule, BirthControlType>? {
            return this@AddEditMedActivity.requestSchedule()
        }

        override suspend fun rescheduleDose(
                index: Int,
                schedule: RepeatSchedule
        ): Pair<RepeatSchedule, BirthControlType>? {
            return this@AddEditMedActivity.requestSchedule(schedule)
        }
    }

    private val lifecycleDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val mainScope = MainScope()
    private var medication = MedicationFull.BLANK
    private var isNewMed = true

    @SuppressLint("CutPasteId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.setupExtraDosesList(binding.shedulesList)

        setSupportActionBar(binding.appbar.toolbar)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.appbar.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        val medicationId = intent.getLongExtra(EXTRA_MEDICATION_ID, Medication.INVALID_MED_ID)
        this.isNewMed = intent.getBooleanExtra(EXTRA_IS_NEW_MED, false)
        if (!isNewMed && !medicationId.canBeRealId()) {
            // invalid params passed
            finish()
        }
        val title = if (isNewMed) getString(R.string.new_medication) else getString(
                R.string.edit_medication)
        supportActionBar?.title = title

        lifecycleScope.launch(lifecycleDispatcher) {
            val medicationFull = fetchMedication(medicationId)
            val medTypes = medicationTypeDao(this@AddEditMedActivity).getAllRaw()
            val doseUnits = doseUnitDao(this@AddEditMedActivity).getAllRaw()
            this@AddEditMedActivity.medication = medicationFull

            mainScope.launch {
                vm.showRequireProofSwitch = packageManager.hasSystemFeature(
                        PackageManager.FEATURE_CAMERA_ANY)
                vm.setupMedicationTypeInput(binding.medTypeInput, medTypes)
                vm.setupDoseUnitInput(binding.doseUnitInput, doseUnits)
                vm.fillFromMedication(medicationFull)
            }
        }
    }

    private fun fetchMedication(medicationId: Long): MedicationFull {
        return if (medicationId.canBeRealId()) {
            medicationDao(this).getFull(medicationId).also {
                it.medication.updateStartsToFuture()
            }
        } else MedicationFull.BLANK
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.add_med_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.save -> {
                lifecycleScope.launch(lifecycleDispatcher) {
                    onSaveButtonTapped()
                }
                true
            }

            R.id.cancel -> {
                val msgId = if (isNewMed) R.string.cancelled else R.string.edit_cancelled
                Toast.makeText(this, msgId, Toast.LENGTH_SHORT).show()
                setResult(false, medication.medication.id)
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getOrCreateMedType(name: String): Long {
        val medTypeExists = medicationTypeDao(this).typeExists(name)
        val typeId = if (medTypeExists) {
            medicationTypeDao(this).get(name).id
        } else {
            var medicationType = MedicationType(name)
            medicationTypeDao(this).insertAll(medicationType)
            medicationType = medicationTypeDao(this).getAllRaw().last()
            medicationType.id
        }
        return typeId
    }

    private fun getOrCreateDoseUnit(unit: String): Long {
        val medTypeExists = doseUnitDao(this).unitExists(unit)
        val unitId = if (medTypeExists) {
            doseUnitDao(this).get(unit).id
        } else {
            var doseUnit = DoseUnit(unit)
            doseUnitDao(this).insertAll(doseUnit)
            doseUnit = doseUnitDao(this).getAllRaw().last()
            doseUnit.id
        }
        return unitId
    }

    private fun validateName(): String? {
        return if (vm.name.isBlank()) {
            getString(R.string.fill_fields)
        } else null
    }

    private fun validateSchedule(): String? {
        val schedulesValid = vm.areAllSchedulesValid();
        return if (!(schedulesValid || vm.asNeeded)) {
            getString(R.string.fill_out_all_schedules)
        } else null
    }

    private fun buildAndSaveMedication(): Medication {
        val typeId = getOrCreateMedType(vm.medTypeString)
        val doseUnitId = getOrCreateDoseUnit(vm.doseUnitString)

        var medication = Medication(name = vm.name, hour = vm.schedule.hour,
                minute = vm.schedule.minute, description = vm.description,
                startDay = vm.schedule.startDay, startMonth = vm.schedule.startMonth,
                startYear = vm.schedule.startYear, daysBetween = vm.schedule.daysBetween,
                weeksBetween = vm.schedule.weeksBetween, monthsBetween = vm.schedule.monthsBetween,
                yearsBetween = vm.schedule.yearsBetween, notify = vm.shouldNotify(),
                requirePhotoProof = vm.requirePhotoProof, typeId = typeId, rxNumber = vm.rxNumber,
                pharmacy = vm.pharmacy, amountPerDose = vm.amountPerDose, doseUnitId = doseUnitId,
                remainingDoses = vm.remainingDoses, takeWithFood = vm.takeWithFood)
        medication.moreDosesPerDay = ArrayList(vm.getExtraSchedules())
        val id = this.medication.medication.id
        if (id.canBeRealId()) {
            medication.id = id
            medication.updateStartsToFuture()
            medicationDao(this).updateMedications(medication)
        } else {
            medicationDao(this).insertAll(medication)
            medication = medicationDao(this).getAllRaw().last()
        }
        return medication
    }

    private fun setMedicationAlarm(medication: Medication) {
        if (medication.notify) {
            AlarmIntentManager.scheduleMedicationAlarm(this, medication)
        } else {
            AlarmIntentManager.cancelAlarm(this, medication)
        }
    }

    private fun onSaveButtonTapped() {
        val updatedMedication = saveMedication()
        if (updatedMedication != null) {
            setResult(true, updatedMedication.id)
            finish()
        }
    }

    private fun setResult(success: Boolean, medicationId: Long) {
        val code = if (success) RESULT_OK else RESULT_CANCELED
        setResult(code, Intent().apply { putExtra(EXTRA_MEDICATION_ID, medicationId) })
    }

    private fun saveMedication(): Medication? {
        val errorMsg = validateName() ?: validateSchedule()
        if (errorMsg != null) {
            mainScope.launch {
                Toast.makeText(this@AddEditMedActivity, errorMsg, Toast.LENGTH_SHORT).show()
            }
            return null
        }

        val medication = buildAndSaveMedication()
        setMedicationAlarm(medication)

        mainScope.launch {
            Toast.makeText(this@AddEditMedActivity, getString(R.string.med_saved),
                    Toast.LENGTH_SHORT).show()
        }
        return medication

    }

    suspend fun requestSchedule(): Pair<RepeatSchedule, BirthControlType>? {
        return requestSchedule(vm.schedule)
    }

    private suspend fun requestSchedule(
            initial: RepeatSchedule
    ): Pair<RepeatSchedule, BirthControlType>? {
        val result = RepeatScheduleDialog2.requestSchedule(supportFragmentManager,
                this@AddEditMedActivity, initial)
        val newSchedule = result?.first ?: return null
        val birthControlType = result.second

        return if (newSchedule.isValid(birthControlType != BirthControlType.NO)) {
            newSchedule to birthControlType
        } else {
            Toast.makeText(this@AddEditMedActivity, getString(R.string.fill_out_schedule),
                    Toast.LENGTH_SHORT).show()
            null
        }
    }

    companion object {
        private const val EXTRA_MEDICATION_ID = "EXTRA_MEDICATION_ID"
        private const val EXTRA_IS_NEW_MED = "EXTRA_IS_NEW_MED"
        suspend fun editMedication(
                launcherActivity: ComponentActivity,
                medication: Medication
        ): Pair<Long, Boolean> {
            return startForResult(launcherActivity, medication.id, false)
        }

        suspend fun addMedication(launcherActivity: ComponentActivity): Pair<Long, Boolean> {
            return startForResult(launcherActivity, Medication.INVALID_MED_ID, true)
        }

        private suspend fun startForResult(
                launcherActivity: ComponentActivity, medicationId: Long,
                isNew: Boolean
        ): Pair<Long, Boolean> {
            val intent = Intent(launcherActivity, AddEditMedActivity::class.java).apply {
                putExtra(EXTRA_MEDICATION_ID, medicationId)
                putExtra(EXTRA_IS_NEW_MED, isNew)
            }
            val result = ActivityResultManager.getInstance().getActivityResult(intent)
                    ?: return medicationId to false

            val resultMedicationId = result.data?.getLongExtra(EXTRA_MEDICATION_ID, 0) ?: 0
            return resultMedicationId to (result.resultCode == RESULT_OK)
        }
    }

}